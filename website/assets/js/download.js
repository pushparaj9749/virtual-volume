/* Virtual Volume — release lookup
   Resolves the newest APK published on GitHub Releases so the download button always
   points at a real, signed artifact. No hardcoded version, no fake link. */
(function () {
  'use strict';

  var REPO = 'pushparaj9749/virtual-volume';
  var API = 'https://api.github.com/repos/' + REPO + '/releases/latest';
  var RELEASES_PAGE = 'https://github.com/' + REPO + '/releases';

  var summary = document.getElementById('release-summary');
  var meta = document.getElementById('release-meta');
  var ctaLabel = document.getElementById('download-cta-label');
  var links = Array.prototype.slice.call(document.querySelectorAll('[data-download-link]'));

  function setLinks(href) {
    links.forEach(function (link) {
      link.setAttribute('href', href);
    });
  }

  function formatBytes(bytes) {
    if (!bytes && bytes !== 0) return '';
    var mb = bytes / (1024 * 1024);
    return mb >= 1 ? mb.toFixed(1) + ' MB' : Math.max(1, Math.round(bytes / 1024)) + ' KB';
  }

  function formatDate(value) {
    if (!value) return '';
    var date = new Date(value);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  }

  function pickApk(assets) {
    if (!assets || !assets.length) return null;
    // Prefer a signed release build; fall back to any APK asset.
    var preferred = assets.filter(function (a) {
      return /\.apk$/i.test(a.name) && /release/i.test(a.name);
    });
    var any = assets.filter(function (a) {
      return /\.apk$/i.test(a.name);
    });
    return (preferred[0] || any[0] || null);
  }

  function reportFailure(message) {
    if (summary) summary.textContent = message;
    if (meta) {
      meta.innerHTML = '';
    }
    if (ctaLabel) ctaLabel.textContent = 'Open GitHub Releases';
    setLinks(RELEASES_PAGE);
  }

  function apply(release) {
    var asset = pickApk(release.assets);
    if (!asset) {
      reportFailure(
        'The newest release does not carry an APK yet. Grab the latest build from the releases page.'
      );
      setLinks(release.html_url || RELEASES_PAGE);
      return;
    }

    var tag = release.tag_name || 'latest';
    var size = formatBytes(asset.size);
    var published = formatDate(release.published_at);

    if (summary) {
      summary.textContent =
        'Version ' + tag + ' — a signed APK built by CI from the published source. Free, no ads, no tracking.';
    }
    if (meta) {
      meta.textContent = [tag, size, published].filter(Boolean).join(' · ') + ' · ';
    }
    if (ctaLabel) {
      ctaLabel.textContent = 'Download ' + (size ? size + ' APK' : 'APK');
    }
    setLinks(asset.browser_download_url);
  }

  function start() {
    if (!links.length) return;

    // Point at the stable "latest" redirect immediately so the button is never dead,
    // then refine it once the API answers.
    setLinks(RELEASES_PAGE + '/latest');

    var controller = typeof AbortController === 'function' ? new AbortController() : null;
    var timer = controller
      ? setTimeout(function () {
          controller.abort();
        }, 8000)
      : null;

    fetch(API, {
      headers: { Accept: 'application/vnd.github+json' },
      signal: controller ? controller.signal : undefined
    })
      .then(function (response) {
        if (response.status === 404) {
          reportFailure('No release has been published yet. The first APK appears here as soon as it is tagged.');
          return null;
        }
        if (!response.ok) {
          throw new Error('GitHub API responded with ' + response.status);
        }
        return response.json();
      })
      .then(function (release) {
        if (release) apply(release);
      })
      .catch(function () {
        // Offline or rate-limited: fall back to the always-valid "latest" redirect.
        if (summary) {
          summary.textContent =
            'Download the newest release straight from GitHub. Free, no ads, no tracking.';
        }
        if (ctaLabel) ctaLabel.textContent = 'Download APK';
        setLinks(RELEASES_PAGE + '/latest');
      })
      .then(function () {
        if (timer) clearTimeout(timer);
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
