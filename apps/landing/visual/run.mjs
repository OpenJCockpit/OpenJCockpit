// Visual regression safety net for the landing CSS Modules conversion (docs/landing-dashboard-integration.md §4.7).
//   node visual/run.mjs baseline   capture screenshots into visual/shots/baseline
//   node visual/run.mjs check      capture into visual/shots/current and require pixel-identical PNGs (within a tiny noise tolerance)
// Screenshots are git-ignored; baselines are meant to be taken on the same machine before a change.
import { createRequire } from 'node:module';
import { build, createServer, preview } from 'vite';
import { mkdirSync, readdirSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(path.join(here, '../../../e2e/package.json'));
const { chromium } = require('@playwright/test');
const { PNG } = require('playwright-core/lib/utilsBundle');

const TOLERANCE = { maxDelta: 16, pixels: 100 };

function compare(a, b) {
  const imgA = PNG.sync.read(a);
  const imgB = PNG.sync.read(b);
  if (imgA.width !== imgB.width || imgA.height !== imgB.height) return { sizeChanged: true };
  let pixels = 0;
  let maxDelta = 0;
  for (let i = 0; i < imgA.data.length; i += 4) {
    const d = Math.max(
      Math.abs(imgA.data[i] - imgB.data[i]),
      Math.abs(imgA.data[i + 1] - imgB.data[i + 1]),
      Math.abs(imgA.data[i + 2] - imgB.data[i + 2]),
    );
    if (d > 0) {
      pixels += 1;
      maxDelta = Math.max(maxDelta, d);
    }
  }
  return { sizeChanged: false, pixels, maxDelta };
}

const mode = process.argv[2];
if (mode !== 'baseline' && mode !== 'check') {
  console.error('usage: node visual/run.mjs baseline|check');
  process.exit(2);
}
const outDir = path.join(here, 'shots', mode === 'baseline' ? 'baseline' : 'current');
rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });

const VIEWPORTS = [
  { name: '1440', width: 1440, height: 900 },
  { name: '600', width: 600, height: 900 },
];

// intro: the greeting overlay is still up. hub: the overlay has exited (it unmounts after 2.85s).
const VIEWS = [
  { name: 'intro', wait: 1800 },
  { name: 'hub', wait: 3500 },
  {
    name: 'topbar-popover',
    wait: 3500,
    go: (p) => p.getByRole('button', { name: /profile/i }).click(),
  },
];

const configFile = path.join(here, 'vite.visual.config.ts');
const buildDir = path.join(here, 'shots', '.build');
let server;
// VISUAL_BUILD=1 serves a production build instead of the dev server, to catch CSS order
// differences between the two.
if (process.env.VISUAL_BUILD) {
  await build({ configFile, logLevel: 'error', build: { outDir: buildDir, emptyOutDir: true } });
  server = await preview({
    configFile,
    logLevel: 'error',
    build: { outDir: buildDir },
    preview: { port: 4174, strictPort: true },
  });
} else {
  server = await createServer({ configFile, logLevel: 'error' });
  await server.listen();
}
const base = 'http://localhost:4174';
const browser = await chromium.launch();
let failures = 0;

try {
  for (const vp of VIEWPORTS) {
    for (const view of VIEWS) {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        reducedMotion: 'reduce',
      });
      const page = await context.newPage();
      // Third-party fonts are never loaded, so screenshots do not depend on the network.
      await page.route(/fonts\.(googleapis|gstatic)\.com/, (route) => route.abort());
      // The puff field is seeded with Math.random(); make it deterministic.
      await page.addInitScript(() => {
        let s = 123456789;
        Math.random = () => {
          s = (s * 1664525 + 1013904223) % 4294967296;
          return s / 4294967296;
        };
      });
      try {
        await page.goto(base);
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(view.wait);
        await view.go?.(page);
        await page.waitForTimeout(300);
        await page.screenshot({
          path: path.join(outDir, `${view.name}@${vp.name}.png`),
          fullPage: true,
          animations: 'disabled',
        });
      } catch (error) {
        failures += 1;
        console.error(`capture failed: ${view.name}@${vp.name}: ${String(error).split('\n')[0]}`);
      }
      await context.close();
    }
  }
} finally {
  await browser.close();
  await (server.close?.() ?? server.httpServer?.close());
}

const shots = readdirSync(outDir).length;
console.log(`${mode}: captured ${shots}/${VIEWPORTS.length * VIEWS.length} screenshots`);
if (failures > 0) process.exit(1);

if (mode === 'check') {
  const baseDir = path.join(here, 'shots', 'baseline');
  if (!existsSync(baseDir)) {
    console.error('no baseline: run `node visual/run.mjs baseline` first');
    process.exit(1);
  }
  const diffs = [];
  for (const file of readdirSync(baseDir)) {
    const current = path.join(outDir, file);
    if (!existsSync(current)) {
      diffs.push(`${file} (missing)`);
      continue;
    }
    const delta = compare(readFileSync(path.join(baseDir, file)), readFileSync(current));
    if (
      delta.sizeChanged ||
      delta.maxDelta > TOLERANCE.maxDelta ||
      delta.pixels > TOLERANCE.pixels
    ) {
      diffs.push(
        `${file} (${delta.sizeChanged ? 'size changed' : `${delta.pixels}px, max delta ${delta.maxDelta}`})`,
      );
    }
  }
  if (diffs.length > 0) {
    console.error(`visual diff in ${diffs.length} screenshot(s):\n  ${diffs.join('\n  ')}`);
    process.exit(1);
  }
  console.log('visual check: identical to baseline');
}
