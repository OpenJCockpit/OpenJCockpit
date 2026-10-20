// Cross-checks CSS-module usage: every `alias.name` read from a *.module.scss import must be a
// class defined in that module, and every class a module defines should be referenced somewhere.
// Also flags custom properties used via var(--x) that are never declared, and classes defined in
// more than one stylesheet.
//   node visual/check-classes.mjs
import * as sass from 'sass';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

const camel = (c) => c.replace(/[-_]+(\w)/g, (_, ch) => ch.toUpperCase());
const walk = (dir) =>
  readdirSync(dir).flatMap((f) => {
    const p = path.join(dir, f);
    return statSync(p).isDirectory() ? walk(p) : [p];
  });
const files = [...walk('src'), ...walk('../../libs')].filter((f) => !f.includes('node_modules'));
const scss = files.filter((f) => f.endsWith('.scss') && !f.includes('src/styles/'));
const sources = files.filter((f) => /\.(tsx?|mjs)$/.test(f));

const defined = new Map(); // scss file -> Set(camel names)
const owners = new Map(); // class -> files
for (const f of scss) {
  const css = sass
    .compile(f, { silenceDeprecations: ['import'] })
    .css.replace(/\/\*[\s\S]*?\*\//g, '');
  const names = new Set();
  for (const m of css.matchAll(/\.([a-zA-Z_][\w-]*)/g)) {
    names.add(camel(m[1]));
    if (!owners.has(m[1])) owners.set(m[1], new Set());
    owners.get(m[1]).add(f);
  }
  defined.set(f, names);
}

const problems = [];
const referenced = new Map(scss.map((f) => [f, new Set()]));
for (const src of sources) {
  const text = readFileSync(src, 'utf8');
  for (const m of text.matchAll(/import\s+(\w+)\s+from\s+'([^']+\.module\.scss)'/g)) {
    const target = path.normalize(path.join(path.dirname(src), m[2]));
    const names = defined.get(target);
    if (!names) {
      problems.push(`${src}: imports missing module ${m[2]}`);
      continue;
    }
    for (const use of text.matchAll(new RegExp(`(?<![\\w.])${m[1]}\\.(\\w+)`, 'g'))) {
      referenced.get(target).add(use[1]);
      if (!names.has(use[1]))
        problems.push(`${src}: ${m[1]}.${use[1]} is not defined in ${target}`);
    }
    for (const use of text.matchAll(new RegExp(`(?<![\\w.])${m[1]}\\[['"](\\w+)['"]\\]`, 'g'))) {
      referenced.get(target).add(use[1]);
    }
  }
  // modules re-exported through helper files (e.g. ui/*/Foo.ts) count as referenced wholesale
  if (/export\s*\{\s*default as \w+\s*\}\s*from\s*'[^']+\.module\.scss'/.test(text)) {
    const m = text.match(/from\s*'([^']+\.module\.scss)'/);
    const target = path.normalize(path.join(path.dirname(src), m[1]));
    for (const n of defined.get(target) ?? []) referenced.get(target).add(n);
  }
}
for (const [f, names] of defined) {
  if (!f.endsWith('.module.scss')) continue;
  for (const n of names) {
    if (!referenced.get(f).has(n)) problems.push(`${f}: .${n} is defined but never referenced`);
  }
}
// Module classes are scoped, so only plain (global) stylesheets can clash.
for (const [cls, fs] of owners) {
  const globals = [...fs].filter((f) => !f.endsWith('.module.scss'));
  if (globals.length > 1) problems.push(`.${cls} is defined in ${globals.join(' and ')}`);
}
const declared = new Set();
const used = new Map();
for (const f of files.filter((x) => /\.(scss|tsx?)$/.test(x))) {
  const text = readFileSync(f, 'utf8');
  for (const m of text.matchAll(/(--[\w-]+)['"\]]*\s*[:=]/g)) declared.add(m[1]);
  for (const m of text.matchAll(/var\((--[\w-]+)/g)) {
    if (!used.has(m[1])) used.set(m[1], f);
  }
}
for (const [name, f] of used)
  if (!declared.has(name)) problems.push(`${f}: var(${name}) is never declared`);

if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
console.log('class and token references are consistent');
