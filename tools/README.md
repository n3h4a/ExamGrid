# Tools

Scripts that regenerate the repository's visual assets, so images in the docs never drift
from the product. Both need Node 18+ and Playwright. Run this once from the repository root
(it only creates a git-ignored `node_modules/`):

```bash
npm install --no-save playwright@1.63.0 && npx playwright install chromium
```

If [`pngquant`](https://pngquant.org) is installed, the screenshot script also compresses its
output.

| Script | Produces | Needs |
|---|---|---|
| `brand/render.cjs` | `docs/assets/banner.png` (README header) and `docs/assets/social-preview.png` (1280×640, for *Settings → Social preview*) from `brand/brand.html` | the Inter font |
| `screenshots/capture.cjs` | every image in `docs/screenshots/`, with numbered callouts on the key screens | a fresh demo stack |

```bash
node tools/brand/render.cjs

docker compose -f docker-compose.full.yml down -v        # start from an empty database
docker compose -f docker-compose.full.yml up -d --build
node tools/screenshots/capture.cjs                       # BASE_URL=http://localhost:8088 by default
```

The capture script runs the real workflow end to end:

1. reject a faulty import;
2. solve a timetable and watch it live;
3. review the plan;
4. approve it as two people and wait for publication;
5. re-seat a lost room;
6. compare the runs;
7. use the student portal and the public seat finder.

It fails if any page throws an error, so it doubles as a smoke test of the whole stack.
