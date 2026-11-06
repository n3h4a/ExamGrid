// Renders the brand cards in brand.html to docs/assets/ using the app's own font (Inter).
// Usage (from the repository root): node tools/brand/render.cjs
const path = require('node:path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..', '..');
const cards = [
  // Retina banner for the README; GitHub requires the social preview at exactly 1280×640.
  { card: 'banner', height: 360, scale: 2, out: 'docs/assets/banner.png' },
  { card: 'social', height: 640, scale: 1, out: 'docs/assets/social-preview.png' },
];

(async () => {
  const browser = await chromium.launch();
  for (const { card, height, scale, out } of cards) {
    const page = await browser.newPage({ viewport: { width: 1280, height }, deviceScaleFactor: scale });
    await page.goto(`file://${path.join(__dirname, 'brand.html')}?card=${card}`);
    await page.evaluate(() => document.fonts.ready);
    await page.locator('#card').screenshot({ path: path.join(root, out) });
    await page.close();
    console.log('wrote', out);
  }
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
