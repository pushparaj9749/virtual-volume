import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const repo = 'https://github.com/pushparaj9749/virtual-volume/releases';
const apkUrl = `${repo}/download/v1.0.0/virtual-volume.apk`;
async function unavailable(page) {
  await page.route('**/api.github.com/repos/pushparaj9749/virtual-volume/releases/latest', route => route.fulfill({ status: 404, contentType: 'application/json', body: '{}' }));
}

test('responsive layout, local assets, navigation and honest unreleased state', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const missing = [];
  page.on('response', response => { if (response.url().includes('127.0.0.1') && response.status() >= 400) missing.push(response.url()); });
  await unavailable(page);
  await page.goto('/');
  await expect(page.locator('#release-summary')).toContainText('No signed APK');
  await expect(page.locator('[data-download-link]').first()).toHaveAttribute('href', repo);
  await expect(page.locator('[data-download-label]').first()).toHaveText('View releases');
  const overflow = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, viewport: innerWidth }));
  expect(overflow.scroll).toBeLessThanOrEqual(overflow.viewport + 1);
  for (const image of await page.locator('img').all()) expect(await image.evaluate(img => img.complete && img.naturalWidth > 0)).toBeTruthy();
  await expect(page.locator('h1')).toContainText('Your volume.');
  if (await page.locator('#menu-toggle').isVisible()) {
    await page.locator('#menu-toggle').click();
    await expect(page.locator('#menu-toggle')).toHaveAttribute('aria-expanded', 'true');
    await page.getByRole('link', { name: 'How it works', exact: true }).click();
    await expect(page.locator('#menu-toggle')).toHaveAttribute('aria-expanded', 'false');
  }
  expect(errors).toEqual([]); expect(missing).toEqual([]);
});

test('dark and light are accessible and the theme survives reload', async ({ page }) => {
  await unavailable(page); await page.goto('/');
  for (const theme of ['dark', 'light']) {
    if (await page.locator('html').getAttribute('data-theme') !== theme) await page.locator('#theme-toggle').click();
    // Make the entire document visible to axe, including reveal cards below the fold.
    await page.locator('.reveal').evaluateAll(elements => elements.forEach(element => element.classList.add('is-visible')));
    const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
    expect(result.violations.map(violation => ({ id: violation.id, nodes: violation.nodes.map(node => node.target) }))).toEqual([]);
  }
  await page.reload(); await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
});

test('keyboard volume gestures, physical-edge orientation and FAQ work', async ({ page }) => {
  await unavailable(page); await page.goto('/');
  const slider = page.locator('#demo-control');
  await slider.focus(); await slider.press('ArrowUp');
  await expect(slider).toHaveAttribute('aria-valuenow', '11');
  await slider.press('Home'); await expect(slider).toHaveAttribute('aria-valuenow', '0');
  await slider.press('End'); await expect(slider).toHaveAttribute('aria-valuenow', '15');
  await slider.press('ArrowDown'); await expect(slider).toHaveAttribute('aria-valuenow', '14');
  await page.locator('[data-rotation="90"]').click();
  await expect(page.locator('#rotation-status')).toContainText('top edge');
  await page.locator('[data-rotation="270"]').click();
  await expect(page.locator('#rotation-status')).toContainText('bottom edge');
  const faq = page.locator('details').first(); await faq.locator('summary').click();
  await expect(faq).toHaveAttribute('open', '');
  await expect(faq).toContainText('there is no WebView');
});

test('pointer tapping and continuous dragging actually update the preview', async ({ page }) => {
  await unavailable(page); await page.goto('/');
  const slider = page.locator('#demo-control'); await slider.scrollIntoViewIfNeeded();
  const bounds = await slider.boundingBox();
  const x = bounds.x + bounds.width / 2;
  await page.mouse.click(x, bounds.y + bounds.height / 4);
  await expect(slider).toHaveAttribute('aria-valuenow', '11');
  await page.mouse.move(x, bounds.y + bounds.height * .75); await page.mouse.down();
  await page.mouse.move(x, bounds.y + bounds.height * .2, { steps: 15 }); await page.mouse.up();
  expect(Number(await slider.getAttribute('aria-valuenow'))).toBeGreaterThan(11);
  const before = Number(await slider.getAttribute('aria-valuenow'));
  await page.mouse.move(x, bounds.y + bounds.height * .2); await page.mouse.down();
  await page.mouse.move(x, bounds.y + bounds.height * .8, { steps: 15 }); await page.mouse.up();
  expect(Number(await slider.getAttribute('aria-valuenow'))).toBeLessThan(before);
});

test('released buttons target the real APK asset, never a fake download', async ({ page }) => {
  await page.route('**/api.github.com/repos/pushparaj9749/virtual-volume/releases/latest', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ tag_name: 'v1.0.0', assets: [{ name: 'virtual-volume.apk', label: 'Verified signed release APK', size: 5_000_000, browser_download_url: apkUrl }, { name: 'release.json' }] }) }));
  await page.goto('/');
  await expect(page.locator('#release-summary')).toContainText('Version 1.0.0');
  for (const link of await page.locator('[data-download-link]').all()) {
    await expect(link).toHaveAttribute('href', apkUrl);
    await expect(link).toContainText('Download APK');
  }
  await expect(page.locator('#release-notes')).toHaveAttribute('href', `${repo}/tag/v1.0.0`);
});

test('a packaged verified asset remains downloadable during API rate limits', async ({ page }) => {
  await page.route('**/release.json', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ available: true, signed: true, versionName: '1.0.0', versionCode: 1, sha256: 'a'.repeat(64), apkUrl, apkSize: 5_000_000 }) }));
  await page.route('**/api.github.com/repos/pushparaj9749/virtual-volume/releases/latest', route => route.fulfill({ status: 403, contentType: 'application/json', body: '{}' }));
  await page.goto('/');
  await expect(page.locator('[data-download-link]').first()).toHaveAttribute('href', apkUrl);
  await expect(page.locator('#release-summary')).toContainText('Version 1.0.0');
});
