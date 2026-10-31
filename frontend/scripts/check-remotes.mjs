// Governance gate for federated remotes. Run after `nx run-many -t build -p remote-*`.
// Remotes without a build output are skipped (pull requests only build affected projects);
// pass --require-built to fail on them instead.
// Fails when a remote's build output could break the hub at runtime:
//   - mf-manifest.json must expose ./app,
//   - react, react-dom, react-router, react-query, i18next, react-i18next, api, i18n and the hub
//     contract must be shared singletons (a private copy breaks hooks, router context, the query
//     cache, the i18n instance and HubSessionContext),
//   - the identity SDK must not be bundled (remotes get the session from the host),
//   - the federated entry (app-entry) must reach the contract and the i18n lib only through the share-scope
//     (loadShare) chunk. A direct import is a private copy: its HubSessionContext would differ from the host's.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const appsDir = new URL('../apps/', import.meta.url).pathname;
const REQUIRED_SHARED = [
  'react',
  'react-dom',
  'react-router',
  '@tanstack/react-query',
  'i18next',
  'react-i18next',
  '@openjcockpit/api',
  '@openjcockpit/i18n',
  '@openjcockpit/hub-contract',
];
const FORBIDDEN_IN_OUTPUT = ['keycloak-js', 'Keycloak'];

function jsFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return jsFiles(path);
    return name.endsWith('.js') ? [path] : [];
  });
}

// A string only the shared lib's own module contains. A chunk holding one is that lib's code.
const SHARED_LIBS = [
  { name: '@openjcockpit/hub-contract', sentinel: 'needs a HubSessionContext provider' },
  { name: '@openjcockpit/i18n', sentinel: 'useLocale must be used inside <I18nProvider>' },
  // The api lib holds the auth token; a private copy would send remote requests unauthenticated.
  { name: '@openjcockpit/api', sentinel: '"ApiError"' },
];

// Chunk files reachable from `entry` by relative imports, not following imports out of `stop` chunks.
function reachable(assets, entry, stop) {
  const seen = new Set();
  const queue = [entry];
  while (queue.length > 0) {
    const name = queue.pop();
    if (seen.has(name) || !existsSync(join(assets, name))) continue;
    seen.add(name);
    if (stop(name)) continue;
    const source = readFileSync(join(assets, name), 'utf8');
    for (const [, ref] of source.matchAll(/["']\.\/([^"']+\.js)["']/g)) queue.push(ref);
  }
  return seen;
}

const remotes = readdirSync(appsDir).filter((name) => name.startsWith('remote-'));
const requireBuilt = process.argv.includes('--require-built');
const errors = [];

for (const remote of remotes) {
  const dist = join(appsDir, remote, 'dist');
  const manifestPath = join(dist, 'mf-manifest.json');
  if (!existsSync(manifestPath)) {
    if (requireBuilt) errors.push(`${remote}: no dist/mf-manifest.json (not built?)`);
    else console.log(`${remote}: not built, skipped`);
    continue;
  }
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

  if (!manifest.exposes?.some((e) => e.path === './app')) {
    errors.push(`${remote}: manifest does not expose ./app`);
  }
  const shared = new Map((manifest.shared ?? []).map((s) => [s.name, s]));
  for (const name of REQUIRED_SHARED) {
    if (!shared.get(name)?.singleton) errors.push(`${remote}: ${name} is not a shared singleton`);
  }
  for (const file of jsFiles(dist)) {
    const source = readFileSync(file, 'utf8');
    for (const marker of FORBIDDEN_IN_OUTPUT) {
      if (source.includes(marker))
        errors.push(`${remote}: ${file.slice(dist.length + 1)} contains "${marker}"`);
    }
  }
  const assets = join(dist, 'assets');
  const files = readdirSync(assets).filter((n) => n.endsWith('.js'));
  const entry = files.find((n) => n.startsWith('app-entry'));
  if (!entry) {
    errors.push(`${remote}: no app-entry chunk`);
  }
  for (const lib of SHARED_LIBS) {
    const chunks = files.filter((n) =>
      readFileSync(join(assets, n), 'utf8').includes(lib.sentinel),
    );
    if (chunks.length !== 1) {
      errors.push(`${remote}: expected one chunk defining ${lib.name}, found ${chunks.length}`);
    } else if (entry) {
      const viaShare = (n) => n.includes('loadShare');
      // The share wrapper may hold the lib as its fallback copy; reaching that wrapper is the shared path.
      if (!viaShare(chunks[0]) && reachable(assets, entry, viaShare).has(chunks[0])) {
        errors.push(
          `${remote}: app-entry imports ${lib.name} directly instead of through the shared scope`,
        );
      }
    }
  }
  console.log(`${remote}: checked`);
}

if (remotes.length === 0) errors.push('no apps/remote-* projects found');
if (errors.length > 0) {
  for (const e of errors) console.error(`::error::${e}`);
  process.exit(1);
}
