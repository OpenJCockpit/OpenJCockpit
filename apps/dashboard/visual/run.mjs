// Visual regression safety net for the styling refactor (docs/styling-modularization-spec.md).
//   node visual/run.mjs baseline   capture screenshots into visual/shots/baseline
//   node visual/run.mjs check      capture into visual/shots/current and require pixel-identical PNGs (within a tiny noise tolerance)
// Screenshots are git-ignored; baselines are meant to be taken on the same machine before a change.
import { createRequire } from 'node:module';
import { createServer } from 'vite';
import { mkdirSync, readdirSync, readFileSync, rmSync, existsSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { routes } from './fixtures.mjs';

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
  { name: '800', width: 800, height: 900 },
];

// Each view: how to reach it from the loaded workspace (project already stored => workspace view).
const VIEWS = [
  { name: 'project-selection', stored: false, go: async () => {} },
  { name: 'workspace', go: async () => {} },
  { name: 'prompt-modal', go: (p) => p.getByRole('button', { name: /start/i }).click() },
  { name: 'project-settings', go: (p) => p.getByTitle('Project management').click() },
  ...[
    ['project-form-new', '+ New project'],
    ['project-form-edit', 'Edit'],
    ['project-credentials', 'Update credentials'],
  ].map(([name, label]) => ({
    name,
    go: async (p) => {
      await p.getByTitle('Project management').click();
      await p.getByRole('button', { name: label, exact: true }).first().click();
    },
  })),
  { name: 'workflow', go: (p) => p.getByTestId('nav-workflow').click() },
  ...[
    ['workflow-configuration', 'Configuration'],
    ['workflow-execution', 'Workflow Execution'],
  ].map(([name, tab]) => ({
    name,
    go: async (p) => {
      await p.getByTestId('nav-workflow').click();
      await p.getByRole('button', { name: tab, exact: true }).click();
    },
  })),
  ...['Workflows', 'Groups', 'Agents', 'Subagents', 'Skills'].map((sub) => ({
    name: `workflow-design-${sub.toLowerCase()}`,
    go: async (p) => {
      await p.getByTestId('nav-workflow').click();
      await p.getByRole('button', { name: 'Design', exact: true }).click();
      await p.getByRole('button', { name: sub, exact: true }).click();
    },
  })),
  { name: 'spec-files', go: (p) => p.getByTestId('nav-spec-files').click() },
  { name: 'skills-hub', go: (p) => p.getByTestId('nav-skills-hub').click() },
  {
    name: 'skills-hub-settings',
    go: async (p) => {
      await p.getByTestId('nav-skills-hub').click();
      await p
        .getByRole('button', { name: /settings|⚙/i })
        .first()
        .click();
    },
  },
];

const server = await createServer({
  configFile: path.join(here, 'vite.visual.config.ts'),
  logLevel: 'error',
});
await server.listen();
const base = 'http://localhost:4173';
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
      await page.route('**/api/**', (route) => {
        const url = new URL(route.request().url());
        const hit = routes.find(([re]) => re.test(url.pathname));
        if (route.request().method() === 'GET' && hit) {
          return route.fulfill({ json: hit[1] });
        }
        return route.fulfill({ status: 404, body: '' });
      });
      await page.addInitScript((stored) => {
        if (stored) localStorage.setItem('openjcockpit_project_id', 'noordzee');
      }, view.stored !== false);
      try {
        await page.goto(base);
        await page.waitForLoadState('networkidle');
        await view.go(page);
        await page.waitForLoadState('networkidle');
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
  await server.close();
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
    // Tolerate rasterisation noise (a few dozen pixels off by a few levels).
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
