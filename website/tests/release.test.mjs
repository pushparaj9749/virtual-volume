import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validApkUrl, validateManifest, releaseToManifest, formatSize, RELEASES_URL } from '../assets/js/release-model.js';
const url = `${RELEASES_URL}/download/v1.0.0/virtual-volume.apk`;
const manifest = { available: true, signed: true, versionName: '1.0.0', versionCode: 1, sha256: 'a'.repeat(64), apkUrl: url, apkSize: 5_000_000 };
const apk = { name: 'virtual-volume.apk', label: 'Verified signed release APK', browser_download_url: url, size: 5_000_000 };
const release = { tag_name: 'v1.0.0', assets: [apk, { name: 'release.json' }] };

test('only this repository HTTPS release APK URLs are accepted', () => {
  assert.ok(validApkUrl(url));
  for (const invalid of ['javascript:alert(1)', 'http://github.com/pushparaj9749/virtual-volume/releases/download/v1.0.0/virtual-volume.apk', url.replace('github.com', 'github.com.evil.test'), url.replace('virtual-volume.apk', 'debug.apk'), url.replace('pushparaj9749', 'someone-else'), url + '?x=1']) assert.equal(validApkUrl(invalid), false);
});
test('a packaged download needs version, signature provenance, checksum and a real asset', () => {
  assert.ok(validateManifest(manifest));
  for (const delta of [{ available: false }, { signed: false }, { sha256: 'bad' }, { versionCode: 0 }, { apkSize: 0 }, { versionName: 'latest' }]) assert.equal(validateManifest({ ...manifest, ...delta }), false);
});
test('a verified release with metadata resolves to the actual asset', () => {
  assert.equal(releaseToManifest(release).apkUrl, url);
});
test('debug APKs never silently become public downloads', () => {
  assert.equal(releaseToManifest({ ...release, assets: [{ ...apk, name: 'app-debug.apk' }, { name: 'release.json' }] }), null);
  assert.equal(releaseToManifest({ ...release, assets: [{ ...apk, label: '' }, { name: 'release.json' }] }), null);
});
test('drafts, prereleases, missing metadata and empty assets are not mislabeled as stable', () => {
  for (const delta of [{ draft: true }, { prerelease: true }, { assets: [apk] }, { assets: [] }, { tag_name: 'main' }]) assert.equal(releaseToManifest({ ...release, ...delta }), null);
});
test('download sizes are human readable', () => {
  assert.equal(formatSize(1_048_576), '1.0 MB'); assert.equal(formatSize(2048), '2 KB');
});
