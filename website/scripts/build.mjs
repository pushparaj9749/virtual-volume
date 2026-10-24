import { mkdir, rm, cp, writeFile, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { build, transform } from 'esbuild';
import { RELEASE_API, REPOSITORY, releaseToManifest, validateManifest } from '../assets/js/release-model.js';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'dist');
await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });
await cp(path.join(root, 'assets'), path.join(output, 'assets'), { recursive: true });
await cp(path.join(root, 'index.html'), path.join(output, 'index.html'));
const css = await transform(await readFile(path.join(root, 'assets/css/styles.css'), 'utf8'), { loader: 'css', minify: true });
await writeFile(path.join(output, 'assets/css/styles.css'), css.code);
for (const entry of ['main', 'download', 'theme']) {
  await build({ entryPoints: [path.join(root, `assets/js/${entry}.js`)], bundle: true, minify: true, format: 'esm', target: ['es2020'], outfile: path.join(output, `assets/js/${entry}.js`), legalComments: 'eof' });
}

let manifest = JSON.parse(await readFile(path.join(root, 'release.json'), 'utf8'));
if (process.argv.includes('--resolve-release')) {
  const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'Virtual-Volume-Pages-Build' };
  if (process.env.GH_TOKEN) headers.Authorization = `Bearer ${process.env.GH_TOKEN}`;
  const response = await fetch(RELEASE_API, { headers, signal: AbortSignal.timeout(20_000) });
  if (response.status === 404) manifest = { available: false, repository: REPOSITORY, reason: 'No signed release is published yet.' };
  else {
    if (!response.ok) throw new Error(`GitHub release lookup failed (${response.status}); refusing to generate an unverified download.`);
    const release = await response.json();
    const candidate = releaseToManifest(release);
    if (!candidate) throw new Error('The latest release does not have the verified APK and metadata. Nothing has been faked.');
    const metadataAsset = release.assets.find(asset => asset.name === 'release.json');
    const metadataResponse = await fetch(metadataAsset.browser_download_url, { signal: AbortSignal.timeout(20_000) });
    if (!metadataResponse.ok) throw new Error('Release metadata could not be verified.');
    manifest = await metadataResponse.json();
    if (!validateManifest(manifest) || manifest.apkUrl !== candidate.apkUrl || manifest.apkSize !== candidate.apkSize || manifest.versionName !== candidate.versionName) {
      throw new Error('APK and signed release metadata disagree. Refusing a broken download.');
    }
    manifest.publishedAt = release.published_at;
    // Turn the progressive HTML into a real direct download even without JavaScript.
    let html = await readFile(path.join(output, 'index.html'), 'utf8');
    html = html.replace(/<a\b[^>]*\bdata-download-link\b[^>]*>/g, tag => tag.replace(/href="[^"]+"/, `href="${manifest.apkUrl}"`))
      .replace(/(<span[^>]*\bdata-download-label[^>]*>)\s*View releases\s*(<\/span>)/g, '$1Download APK$2')
      .replace('Checking for a verified, signed release.', `Version ${manifest.versionName} · verified signed native Android APK.`);
    await writeFile(path.join(output, 'index.html'), html);
  }
}
await writeFile(path.join(output, 'release.json'), JSON.stringify(manifest, null, 2) + '\n');
await writeFile(path.join(output, '.nojekyll'), '');
console.log(`Website built. Signed release download: ${manifest.available === true ? manifest.versionName : 'not yet published (honest unavailable state)'}.`);
