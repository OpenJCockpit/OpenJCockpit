// Fails when the built bundle outgrows its gzip budget. Run after `nx run dashboard:build`.
// Budgets sit about 20% above the measured size; raise them deliberately when growth is intended.
import { readdirSync, readFileSync, appendFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { join, extname } from 'node:path';

const assetsDir = new URL('../dist/assets', import.meta.url).pathname;
const KB = 1024;
const budgets = {
  'largest JS chunk': 150 * KB,
  'total JS': 200 * KB,
  'total CSS': 20 * KB,
};

const files = readdirSync(assetsDir).map((name) => ({
  name,
  ext: extname(name),
  size: gzipSync(readFileSync(join(assetsDir, name))).length,
}));
const js = files.filter((f) => f.ext === '.js');
const css = files.filter((f) => f.ext === '.css');
const sum = (list) => list.reduce((total, f) => total + f.size, 0);
const largest = js.reduce((max, f) => (f.size > max.size ? f : max), { name: '-', size: 0 });

const measured = {
  'largest JS chunk': largest.size,
  'total JS': sum(js),
  'total CSS': sum(css),
};

const rows = Object.entries(budgets).map(([label, budget]) => {
  const size = measured[label];
  return { label, size, budget, ok: size <= budget };
});

const kb = (bytes) => `${(bytes / KB).toFixed(1)} kB`;
const table = [
  '### Frontend bundle size (gzip)',
  '',
  '| Metric | Size | Budget | |',
  '| --- | --- | --- | --- |',
  ...rows.map((r) => `| ${r.label} | ${kb(r.size)} | ${kb(r.budget)} | ${r.ok ? 'ok' : 'OVER'} |`),
].join('\n');

console.log(table);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${table}\n`);

const over = rows.filter((r) => !r.ok);
if (over.length > 0) {
  console.error(
    `Bundle budget exceeded: ${over.map((r) => r.label).join(', ')} (largest chunk: ${largest.name}).`,
  );
  process.exit(1);
}
