#!/usr/bin/env node
// check-docs.mjs: keeps specs, tasks, tests and links consistent (Constitution, Article 1).
//
// Usage:
//   node scripts/check-docs.mjs            # requirement IDs <-> tasks, unknown IDs, broken links
//   node scripts/check-docs.mjs --strict   # also: every M1/M2 requirement appears in at least one test file
//
// Rules:
//   - A requirement is *defined* by a table row whose first cell is its ID, in docs/SPEC.md or specs/*/spec.md.
//     The last cell of that row is its milestone (M1, M2, M3, Later, Withdrawn).
//   - Every defined requirement, except Later/Withdrawn, must be referenced by at least one tasks file.
//   - Every ID referenced anywhere (docs, specs, tests, contract) must be defined.
//   - Every relative Markdown link must point to an existing file, and its #anchor to an existing heading.
//
// No dependencies: plain Node.js 18+.

import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname, relative, resolve, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const STRICT = process.argv.includes('--strict');
const STRICT_MILESTONES = new Set(['M1', 'M2']);
const NO_TASK_NEEDED = new Set(['Later', 'Withdrawn']);
const ID_RE = /\b(?:FR|NFR)-[A-Z0-9]+-\d{3}\b/g;
const ID_EXACT = /^(?:FR|NFR)-[A-Z0-9]+-\d{3}$/;
const SKIP_DIRS = new Set(['.git', 'node_modules', 'target', 'dist', 'build', '.idea', '.vscode', 'coverage']);

const errors = [];
const warnings = [];
const rel = (p) => relative(ROOT, p).split(sep).join('/');

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir)) {
    if (SKIP_DIRS.has(name)) continue;
    const full = join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

const allFiles = walk(ROOT);
const mdFiles = allFiles.filter((f) => extname(f) === '.md');
const read = (f) => readFileSync(f, 'utf8');

// Removes fenced code blocks (and, unless keepInline, inline code), keeping line numbers stable.
function stripCode(text, { keepInline = false } = {}) {
  const lines = text.split('\n');
  let inFence = false;
  return lines
    .map((line) => {
      if (/^\s*(```|~~~)/.test(line)) {
        inFence = !inFence;
        return '';
      }
      if (inFence) return '';
      return keepInline ? line : line.replace(/`[^`]*`/g, '');
    })
    .join('\n');
}

// ---------- 1. Requirement definitions ----------
const specFiles = mdFiles.filter((f) => {
  const r = rel(f);
  return r === 'docs/SPEC.md' || /^specs\/[^/]+\/spec\.md$/.test(r);
});

const defined = new Map(); // id -> { milestone, file }
for (const file of specFiles) {
  for (const line of read(file).split('\n')) {
    if (!line.trim().startsWith('|')) continue;
    const cells = line.split('|').slice(1, -1).map((c) => c.trim());
    if (cells.length < 2 || !ID_EXACT.test(cells[0])) continue;
    const id = cells[0];
    const milestone = cells[cells.length - 1];
    if (defined.has(id)) {
      errors.push(`Requirement ${id} is defined twice: ${defined.get(id).file} and ${rel(file)}`);
      continue;
    }
    if (!/^(M\d|Later|Withdrawn)$/.test(milestone)) {
      errors.push(`Requirement ${id} in ${rel(file)} has no valid milestone in its last column (got "${milestone}")`);
    }
    defined.set(id, { milestone, file: rel(file) });
  }
}

// ---------- 2. References ----------
function collectIds(files) {
  const found = new Map(); // id -> Set(files)
  for (const file of files) {
    const text = read(file);
    for (const id of text.match(ID_RE) ?? []) {
      if (!found.has(id)) found.set(id, new Set());
      found.get(id).add(rel(file));
    }
  }
  return found;
}

const taskFiles = mdFiles.filter((f) => {
  const r = rel(f);
  return r === 'docs/TASKS.md' || /^specs\/[^/]+\/tasks\.md$/.test(r);
});
const inTasks = collectIds(taskFiles);

const isTestFile = (f) => {
  const r = rel(f);
  return (
    /\/src\/test\//.test('/' + r) ||
    /\.(test|spec)\.[cm]?[jt]sx?$/.test(r) ||
    /(^|\/)e2e\/.*\.[jt]s$/.test(r)
  );
};
const testFiles = allFiles.filter(isTestFile);
const inTests = collectIds(testFiles);

const refSources = [
  ...mdFiles.filter((f) => !rel(f).startsWith('docs/templates/')),
  ...allFiles.filter((f) => /\.(ya?ml|json)$/.test(f) && rel(f).startsWith('api/')),
  ...testFiles,
];
const referenced = collectIds(refSources);

// ---------- 3. Checks ----------
for (const [id, files] of referenced) {
  if (!defined.has(id)) {
    errors.push(`Unknown requirement ${id} referenced in: ${[...files].join(', ')}`);
  }
}

for (const [id, { milestone, file }] of defined) {
  if (NO_TASK_NEEDED.has(milestone)) continue;
  if (!inTasks.has(id)) errors.push(`Requirement ${id} (${milestone}, ${file}) has no task`);
}

let missingTests = 0;
for (const [id, { milestone }] of defined) {
  if (!STRICT_MILESTONES.has(milestone)) continue;
  if (!inTests.has(id)) {
    missingTests++;
    if (STRICT) errors.push(`Requirement ${id} (${milestone}) has no test that mentions it`);
  }
}
if (!STRICT && missingTests > 0) {
  warnings.push(
    `${missingTests} M1/M2 requirement(s) have no test yet (expected before code exists; enforced with --strict from M2)`,
  );
}

// ---------- 4. Links and anchors ----------
function slugify(heading) {
  return heading
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') // links -> text
    .replace(/[`*]/g, '')
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .replace(/ /g, '-');
}

const anchorCache = new Map();
function anchorsOf(file) {
  if (anchorCache.has(file)) return anchorCache.get(file);
  const seen = new Map();
  const anchors = new Set();
  for (const line of stripCode(read(file), { keepInline: true }).split('\n')) {
    const m = /^(#{1,6})\s+(.*?)\s*#*\s*$/.exec(line);
    if (!m) continue;
    const base = slugify(m[2]);
    const n = seen.get(base) ?? 0;
    anchors.add(n === 0 ? base : `${base}-${n}`);
    seen.set(base, n + 1);
  }
  anchorCache.set(file, anchors);
  return anchors;
}

for (const file of mdFiles) {
  if (rel(file).startsWith('docs/templates/')) continue; // templates link to files that exist only after copying
  const lines = stripCode(read(file)).split('\n');
  lines.forEach((line, i) => {
    for (const m of line.matchAll(/\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)) {
      const target = m[1];
      if (/^(https?:|mailto:)/.test(target)) continue;
      const [pathPart, anchor] = target.split('#');
      const where = `${rel(file)}:${i + 1}`;
      const targetFile = pathPart ? resolve(dirname(file), decodeURIComponent(pathPart)) : file;
      if (!existsSync(targetFile)) {
        errors.push(`Broken link at ${where}: ${target}`);
        continue;
      }
      if (anchor && extname(targetFile) === '.md' && !anchorsOf(targetFile).has(anchor)) {
        errors.push(`Broken anchor at ${where}: ${target}`);
      }
    }
  });
}

// ---------- 5. Report ----------
const byMilestone = {};
for (const { milestone } of defined.values()) byMilestone[milestone] = (byMilestone[milestone] ?? 0) + 1;

console.log(`Requirements defined: ${defined.size} ${JSON.stringify(byMilestone)}`);
console.log(`Spec files: ${specFiles.length} · Task files: ${taskFiles.length} · Test files: ${testFiles.length} · Markdown files: ${mdFiles.length}`);
for (const w of warnings) console.log(`WARN  ${w}`);
for (const e of errors) console.log(`ERROR ${e}`);
console.log(errors.length ? `\n✗ ${errors.length} problem(s) found` : '\n✓ Docs are consistent');
process.exit(errors.length ? 1 : 0);
