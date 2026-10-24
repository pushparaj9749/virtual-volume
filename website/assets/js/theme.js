(() => {
  try {
    const stored = localStorage.getItem('virtual-volume-theme');
    if (stored === 'light' || stored === 'dark') document.documentElement.dataset.theme = stored;
  } catch { /* Theme remains usable when browser storage is disabled. */ }
})();
