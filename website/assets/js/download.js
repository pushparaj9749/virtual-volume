import { RELEASE_API, RELEASES_URL, releaseToManifest, validateManifest, formatSize } from './release-model.js';

const links = [...document.querySelectorAll('[data-download-link]')];
const summary = document.querySelector('#release-summary');
const meta = document.querySelector('#release-meta');
const notes = document.querySelector('#release-notes');
let available = false;

function apply(manifest) {
  available = true;
  links.forEach(link => { link.href = manifest.apkUrl; link.dataset.releaseReady = 'true'; link.querySelector('[data-download-label]').textContent = 'Download APK'; });
  summary.textContent = `Version ${manifest.versionName} · a signed native Android release. Free, with no ads or tracking.`;
  meta.textContent = [`v${manifest.versionName}`, formatSize(manifest.apkSize), 'Signed release'].join('  ·  ');
  notes.href = `${RELEASES_URL}/tag/v${manifest.versionName}`;
}

function unavailable(message) {
  if (available) return; // An API outage must not break an already verified packaged download.
  summary.textContent = message;
  meta.textContent = 'APKs are published only after release-signature verification.';
  links.forEach(link => { link.href = RELEASES_URL; link.dataset.releaseReady = 'false'; link.querySelector('[data-download-label]').textContent = 'View releases'; });
}

async function start() {
  try {
    const packaged = await fetch(new URL('../../release.json', import.meta.url));
    if (packaged.ok) {
      const manifest = await packaged.json();
      if (validateManifest(manifest)) apply(manifest);
    }
  } catch { /* Continue to the public release lookup. */ }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6500);
  try {
    const response = await fetch(RELEASE_API, { headers: { Accept: 'application/vnd.github+json' }, signal: controller.signal });
    if (response.status === 404) { unavailable('No signed APK has been published yet. The download appears here after a verified release.'); return; }
    if (!response.ok) throw new Error('Release lookup unavailable');
    const manifest = releaseToManifest(await response.json());
    if (manifest) apply(manifest);
    else unavailable('There is no verified release APK to download yet. Check the releases page for publication status.');
  } catch { unavailable('Release information is temporarily unavailable. Check GitHub Releases for signed APKs.'); }
  finally { clearTimeout(timer); }
}
start();
