export const REPOSITORY = 'pushparaj9749/virtual-volume';
export const RELEASES_URL = `https://github.com/${REPOSITORY}/releases`;
export const RELEASE_API = `https://api.github.com/repos/${REPOSITORY}/releases/latest`;

export function validApkUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname === 'github.com' && !url.username && !url.password &&
      url.pathname.startsWith(`/${REPOSITORY}/releases/download/`) && url.pathname.endsWith('/virtual-volume.apk') && !url.search;
  } catch { return false; }
}

export function validateManifest(manifest) {
  return manifest?.available === true && manifest.signed === true &&
    /^\d+\.\d+\.\d+(?:-[\da-z.-]+)?$/i.test(manifest.versionName ?? '') &&
    Number.isSafeInteger(manifest.versionCode) && manifest.versionCode > 0 &&
    /^[a-f\d]{64}$/i.test(manifest.sha256 ?? '') &&
    validApkUrl(manifest.apkUrl) && manifest.apkSize > 0;
}

/** Only the pipeline's stable-named APK plus its metadata qualify; never a debug fallback. */
export function releaseToManifest(release) {
  if (!release || release.draft || release.prerelease || !/^v\d+\.\d+\.\d+$/.test(release.tag_name ?? '')) return null;
  const apk = release.assets?.find(asset => asset.name === 'virtual-volume.apk' && asset.label === 'Verified signed release APK' && asset.size > 0 && validApkUrl(asset.browser_download_url));
  const metadata = release.assets?.find(asset => asset.name === 'release.json');
  if (!apk || !metadata) return null;
  return {
    available: true, signed: true, versionName: release.tag_name.slice(1),
    apkUrl: apk.browser_download_url, apkSize: apk.size, releaseUrl: release.html_url,
    publishedAt: release.published_at, fromApi: true
  };
}

export function formatSize(bytes) {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}
