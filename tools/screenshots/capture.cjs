// Recreates every image in docs/screenshots/ from a fresh demo stack, so the screenshots in
// the documentation always match the current UI.
//
//   docker compose -f docker-compose.full.yml down -v      # fresh database with the demo university
//   docker compose -f docker-compose.full.yml up -d --build
//   node tools/screenshots/capture.cjs                     # BASE_URL defaults to http://localhost:8088
//
// The script drives the real workflow (import check, solve, approve twice, publish, disruption,
// comparison, portals) and adds numbered callouts to the key screens. The README explains them.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { chromium } = require('playwright');

const BASE = process.env.BASE_URL || 'http://localhost:8088';
const OUT = path.resolve(__dirname, '..', '..', 'docs', 'screenshots');
const DESKTOP = { width: 1360, height: 900 };

/**
 * Outlines each target and adds a numbered badge: on the top-left corner by default, or beside
 * the outline (`badge: 'end'`) for small inline targets where the corner would cover text.
 */
async function annotate(page, marks) {
  const boxes = [];
  for (const { target, pad = 6, badge = 'corner' } of marks) {
    await target.scrollIntoViewIfNeeded().catch(() => {});
    const box = await target.boundingBox();
    if (!box) throw new Error(`annotation target not visible: ${target}`);
    boxes.push({ x: box.x - pad, y: box.y - pad, w: box.width + 2 * pad, h: box.height + 2 * pad, badge });
  }
  await page.evaluate((list) => {
    const layer = document.createElement('div');
    layer.id = 'eg-annotations';
    layer.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:2147483647';
    list.forEach((b, i) => {
      const ring = document.createElement('div');
      ring.style.cssText = `position:absolute;left:${b.x}px;top:${b.y}px;width:${b.w}px;height:${b.h}px;`
        + 'border:2.5px solid #e11d48;border-radius:10px;box-shadow:0 0 0 4px rgba(225,29,72,.12)';
      const badge = document.createElement('div');
      badge.textContent = String(i + 1);
      const [bx, by] = b.badge === 'end'
        ? [b.x + b.w + 8, b.y + b.h / 2 - 13]
        : [Math.max(4, b.x - 13), Math.max(4, b.y - 13)];
      badge.style.cssText = `position:absolute;left:${bx}px;top:${by}px;width:26px;height:26px;border-radius:50%;`
        + 'background:#e11d48;color:#fff;font:700 14px/26px Inter,sans-serif;text-align:center;'
        + 'box-shadow:0 1px 4px rgba(0,0,0,.35),0 0 0 2px #fff';
      layer.append(ring, badge);
    });
    document.body.append(layer);
  }, boxes);
}

async function shot(page, name, marks, { settleMs = 300, height } = {}) {
  await page.waitForTimeout(settleMs);
  if (marks) await annotate(page, marks);
  const clip = height ? { x: 0, y: 0, width: page.viewportSize().width, height } : undefined;
  await page.screenshot({ path: path.join(OUT, `${name}.png`), clip });
  await page.evaluate(() => document.getElementById('eg-annotations')?.remove());
  console.log('wrote', `docs/screenshots/${name}.png`);
}

(async () => {
  const browser = await chromium.launch();
  const errors = [];

  async function session(user, password, viewport = DESKTOP) {
    const context = await browser.newContext({ viewport, deviceScaleFactor: 2 });
    const page = await context.newPage();
    page.on('pageerror', (e) => errors.push(String(e)));
    if (user) {
      await page.goto(`${BASE}/login`);
      await page.fill('input[autocomplete=username]', user);
      await page.fill('input[type=password]', password);
      await page.click('button[type=submit]');
      await page.waitForLoadState('networkidle');
    }
    return page;
  }

  const admin = await session('admin', 'admin12345');
  const card = (title) => admin.locator(`section:has(> header h2:text-is("${title}"))`);

  // Dashboard
  await admin.waitForSelector('text=Ready to schedule?');
  await shot(admin, 'dashboard');

  // Import: a file with problems is rejected as a whole, with row-level reasons.
  const bad = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'examgrid-')), 'enrolments.csv');
  fs.writeFileSync(bad, 'Roll No,Course Code\n21CS045,CS101\n21CS045,CS101\nbad roll,CS101\n21CS046,ZZ999\n');
  await admin.click('a[href="/admin/imports"]');
  await admin.selectOption('select', 'enrolments');
  await admin.setInputFiles('input[type=file]', bad);
  await admin.click('text=Check file');
  await admin.waitForSelector('text=Nothing was imported');
  await shot(admin, 'import-report', [
    { target: admin.locator('div.rounded-lg:has(> div > p:text-matches("Nothing was imported"))') },
    { target: admin.locator('table').first() },
  ]);

  // Seat-grid editor
  await admin.click('a[href="/admin/rooms"]');
  await admin.click('tr:has-text("R101") >> text=Edit');
  await admin.click('[aria-label="Seat A1"]');
  await shot(admin, 'room-editor');
  await admin.keyboard.press('Escape');

  // Solve. Six days between hard papers cannot be met everywhere, so the optimiser spends its
  // whole move budget (about two seconds) and the live view is captured as soon as it starts.
  await admin.goto(`${BASE}/admin/runs`);
  await admin.click('text=New run');
  await admin.fill('input[placeholder^="Dec 2026"]', 'Dec 2026 end-semester · draft 1');
  await admin.getByLabel('Time limit (seconds)').fill('10');
  await admin.getByLabel('Min. days apart').fill('6');
  await admin.click('text=Show advanced settings');
  await admin.getByLabel('Move budget').fill('20000000');
  await admin.click('button:has-text("Start run")');
  await admin.waitForSelector('text=Optimising', { timeout: 20000 });
  await shot(admin, 'run-live-progress', [
    { target: card('Solving') },
    { target: admin.getByRole('button', { name: /Cancel/ }) },
  ], { settleMs: 150, height: 360 });
  await admin.waitForSelector('text=Ready for review', { timeout: 60000 });
  const runUrl = admin.url();
  await shot(admin, 'run-review', [
    { target: admin.locator('div.grid:has(> div:has-text("Score"))').first() },
    { target: card('Candidates per slot') },
    { target: admin.getByRole('button', { name: /Room lost/ }) },
  ]);

  const tabs = admin.locator('[role=tablist]');
  const scrollToTabs = () => tabs.evaluate((el) => window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 80));
  await admin.click('role=tab[name="seating"]');
  await admin.waitForSelector('text=Front');
  await scrollToTabs();
  const room = admin.locator('section:has(> header h3)').first();
  await shot(admin, 'seating-chart', [
    { target: admin.getByLabel('Slot', { exact: true }), badge: 'end' },
    { target: room.locator('header') },
    { target: room.locator('div.grid') },
  ]);

  await admin.click('role=tab[name="timetable"]');
  await admin.fill('input[aria-label=Highlight]', 'CS');
  await scrollToTabs();
  await shot(admin, 'timetable-grid');

  // Two different people approve; publication renders the PDFs and the seat index.
  await admin.goto(runUrl);
  await card('Approval').locator('button:has-text("Approve")').click();
  await admin.waitForSelector('text=Withdraw my approval');
  const second = await session('examcell2', 'demo12345');
  await second.goto(runUrl);
  await second.locator('section:has(> header h2:text-is("Approval"))').locator('button:has-text("Approve")').click();
  await second.waitForSelector('text=Live for students', { timeout: 60000 });
  await second.click('role=tab[name="documents"]');
  await second.waitForSelector('text=Hall tickets');
  await second.locator('[role=tablist]').evaluate((el) => window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 80));
  await shot(second, 'documents');

  // A room is lost for one session: only its students move, into a new run that needs approval again.
  await admin.goto(runUrl);
  await admin.click('button:has-text("Room lost")');
  const dialog = admin.locator('[role=dialog]');
  await dialog.locator('select').nth(0).selectOption('R103');
  await dialog.locator('select').nth(1).selectOption({ index: 3 }); // one session, e.g. a flooded morning
  await dialog.locator('button:has-text("Re-seat students")').click();
  await admin.waitForSelector('text=Re-seated without R103', { timeout: 30000 });
  await shot(admin, 'disruption', [
    { target: admin.locator('div:has(> p:text-matches("^Re-seated without"))') },
    { target: admin.getByRole('link', { name: /derived from run/ }), badge: 'end' },
  ]);

  await admin.goto(`${BASE}/admin/runs`);
  await admin.check('input[aria-label="Select run 1 for comparison"]');
  await admin.check('input[aria-label="Select run 2 for comparison"]');
  await admin.click('button:has-text("Compare")');
  await admin.waitForSelector('text=Compare runs');
  await shot(admin, 'compare');

  const student = await session('23ce001', 'demo12345');
  await student.waitForSelector('text=Hall ticket');
  await shot(student, 'student-portal', [
    { target: student.getByRole('button', { name: /Hall ticket/ }) },
    { target: student.locator('ol > li').first() },
  ]);

  const visitor = await session(null, null, { width: 900, height: 800 });
  await visitor.goto(`${BASE}/seat`);
  await visitor.fill('input[aria-label="Roll number"]', '23ce002');
  await visitor.click('button:has-text("Find")');
  await visitor.waitForSelector('text=exam(s)');
  await shot(visitor, 'seat-finder', [
    { target: visitor.locator('form') },
    { target: visitor.locator('ol > li').first() },
  ]);

  await browser.close();
  // Optional: shrink the PNGs (typically by two thirds) when pngquant is installed.
  const pngs = fs.readdirSync(OUT).filter((f) => f.endsWith('.png')).map((f) => path.join(OUT, f));
  const quant = spawnSync('pngquant', ['--quality=80-95', '--speed', '1', '--force', '--ext', '.png', ...pngs]);
  console.log(quant.error ? 'pngquant not installed; PNGs left uncompressed' : 'compressed with pngquant');
  if (errors.length) {
    console.error('page errors:', errors);
    process.exit(1);
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
