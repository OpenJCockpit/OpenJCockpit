// Fails when translations drift apart. Run via `nx run i18n:check-i18n` (part of `npm run check`).
//
// Locale files live with each lib/app at `<project>/src/locales/<locale>.json`, shaped
// `{ "<namespace>": { ...nested keys } }`. English is the source: every other locale must have
// the same keys and `{{placeholders}}`, and every English key must be used by a literal `t()` call.
// Keys built at runtime (`t(\`status.${id}\`)`) are not traceable, so their static prefix marks
// every key beneath it as used.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, join, relative } from 'node:path';

const SOURCE_LOCALE = 'en';
const PLURAL_SUFFIXES = ['zero', 'one', 'two', 'few', 'many', 'other'];
const root = new URL('../../..', import.meta.url).pathname;

function walk(dir, visit) {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name === 'dist' || name === 'coverage') continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) walk(path, visit);
    else visit(path);
  }
}

function flatten(value, prefix = '') {
  if (typeof value === 'string') return [[prefix, value]];
  return Object.entries(value).flatMap(([key, child]) =>
    flatten(child, prefix ? `${prefix}.${key}` : key),
  );
}

/** `items_one` and `items_other` are one key as far as callers and parity are concerned. */
function baseKey(key) {
  const suffix = PLURAL_SUFFIXES.find((form) => key.endsWith(`_${form}`));
  return suffix ? key.slice(0, -(suffix.length + 1)) : key;
}

const placeholders = (text) =>
  [...text.matchAll(/{{\s*([\w.]+)[\s,}]/g)]
    .map((match) => match[1])
    .sort()
    .join(',');

const localeFiles = [];
const sourceFiles = [];
for (const base of ['libs', 'apps']) {
  walk(join(root, base), (path) => {
    if (/\/src\/locales\/[^/]+\.json$/.test(path)) localeFiles.push(path);
    else if (/\/src\/.*\.(ts|tsx)$/.test(path) && !/\.(test|d)\.tsx?$/.test(path)) {
      sourceFiles.push(path);
    }
  });
}

const problems = [];

// Group locale files per project, then compare each against that project's English file.
const byDir = Map.groupBy(localeFiles, (path) => path.slice(0, path.lastIndexOf('/')));
const sourceKeys = new Map(); // `namespace:key` base key -> flattened per-project English entry
for (const [dir, files] of byDir) {
  const load = (file) =>
    new Map(flatten(JSON.parse(readFileSync(file, 'utf8'))).map(([key, text]) => [key, text]));
  const english = files.find((file) => basename(file, '.json') === SOURCE_LOCALE);
  if (!english) {
    problems.push(`${relative(root, dir)}: missing ${SOURCE_LOCALE}.json`);
    continue;
  }
  const reference = load(english);
  for (const key of reference.keys()) sourceKeys.set(baseKey(key), relative(root, english));

  for (const file of files.filter((candidate) => candidate !== english)) {
    const where = relative(root, file);
    const entries = load(file);
    const bases = new Set([...entries.keys()].map(baseKey));
    for (const key of new Set([...reference.keys()].map(baseKey))) {
      if (!bases.has(key)) problems.push(`${where}: missing key "${key}"`);
    }
    for (const [key, text] of entries) {
      const source = reference.get(key);
      if (source === undefined) {
        if (![...reference.keys()].some((k) => baseKey(k) === baseKey(key))) {
          problems.push(`${where}: extra key "${key}" not in ${SOURCE_LOCALE}.json`);
        }
      } else if (placeholders(source) !== placeholders(text)) {
        problems.push(`${where}: placeholders of "${key}" differ from ${SOURCE_LOCALE}.json`);
      }
    }
  }
}

// Key usage: literal t('ns:a.b') / t("a.b") calls, plus the static prefix of template keys.
const used = new Set();
const dynamicPrefixes = [];
for (const file of sourceFiles) {
  const text = readFileSync(file, 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
  for (const [, key] of text.matchAll(/\bt\(\s*['"]([\w.:-]+)['"]/g)) {
    used.add(key.slice(key.indexOf(':') + 1));
  }
  for (const [, prefix] of text.matchAll(/\bt\(\s*`([\w.:-]*)\$\{/g)) {
    dynamicPrefixes.push(prefix.slice(prefix.indexOf(':') + 1));
  }
}
const isUsed = (key) => used.has(key) || dynamicPrefixes.some((prefix) => key.startsWith(prefix));

for (const [key, file] of sourceKeys) {
  // Namespaces are the first key segment (`common.close`); callers pass them separately.
  const withoutNamespace = key.slice(key.indexOf('.') + 1);
  if (!isUsed(key) && !isUsed(withoutNamespace)) problems.push(`${file}: unused key "${key}"`);
}
for (const key of used) {
  const known = [...sourceKeys.keys()].some(
    (candidate) => candidate === key || candidate.slice(candidate.indexOf('.') + 1) === key,
  );
  if (!known) problems.push(`source: t("${key}") has no key in ${SOURCE_LOCALE}.json`);
}

if (problems.length > 0) {
  console.error(`check:i18n found ${problems.length} problem(s):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}
console.log(`check:i18n ok (${localeFiles.length} locale files, ${sourceKeys.size} keys)`);
