import { defineConfig } from '@playwright/test';
import chromium from '@sparticuz/chromium';

const bundled = process.env.VV_BUNDLED_CHROMIUM === '1';
if (bundled) {
  // Hermetic npm-distributed test browser for SDK/CDN-restricted development environments.
  const { inflate } = await import('./node_modules/@sparticuz/chromium/build/esm/lambdafs.js');
  const libraryFolder = await inflate(new URL('./node_modules/@sparticuz/chromium/bin/al2023.tar.br', import.meta.url).pathname);
  process.env.LD_LIBRARY_PATH = `${libraryFolder}/lib${process.env.LD_LIBRARY_PATH ? ':' + process.env.LD_LIBRARY_PATH : ''}`;
}
export default defineConfig({
  testDir: './tests/browser',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }], ['junit', { outputFile: 'test-results/browser.xml' }]],
  use: {
    baseURL: process.env.SITE_URL || 'http://127.0.0.1:3000',
    browserName: 'chromium',
    launchOptions: bundled ? { executablePath: await chromium.executablePath(), args: chromium.args.filter(argument => !['--disable-web-security', '--allow-running-insecure-content', '--single-process'].includes(argument)), headless: true } : {},
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    reducedMotion: 'reduce'
  },
  webServer: process.env.SITE_URL ? undefined : { command: 'node scripts/serve.mjs', port: 3000, reuseExistingServer: !process.env.CI },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    { name: 'mobile', use: { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true } },
    { name: 'small-mobile', use: { viewport: { width: 320, height: 740 }, isMobile: true, hasTouch: true } }
  ]
});
