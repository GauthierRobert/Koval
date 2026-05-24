#!/usr/bin/env node
// Zero-dep lint for the Koval end-user skills.
// Run:  node skills/lint-skills.mjs
//
// Checks:
//   1. SKILL.md has frontmatter with `name` and `description`.
//   2. Every markdown file/path reference inside skill prose resolves either to
//      the skill's own resources/ OR to skills/_shared/resources/ — the merge
//      `package-skills.mjs` performs at build time.
//   3. Every backticked identifier that looks like an MCP tool call (camelCase,
//      starts with a known verb prefix) matches a tool actually declared in
//      backend/.../mcp/Mcp*Tools.java via @Tool.
//   4. No skill ships a local file at the same relative path as a file in
//      skills/_shared/ — that would silently override the shared copy.
//
// Exits non-zero with a tidy report when anything fails.
// Designed to be called from CI or before `node skills/package-skills.mjs`.

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, dirname, basename, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SKILLS_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = dirname(SKILLS_DIR);
const MCP_DIR = join(REPO_ROOT, 'backend', 'src', 'main', 'java', 'com', 'koval', 'trainingplannerbackend', 'mcp');
const SHARED_DIR = join(SKILLS_DIR, '_shared');

// Verb prefixes that almost always indicate a tool call when found in camelCase
// inside backticks. Picking these vs. a denylist avoids false-positives on
// model field names like `intensityTarget` or `restDurationSeconds`.
const TOOL_VERB_PREFIXES = [
  'get', 'list', 'search', 'create', 'update', 'delete', 'clone',
  'assign', 'unassign', 'schedule', 'reschedule', 'estimate',
  'mark', 'cancel', 'pause', 'resume', 'activate', 'apply',
  'add', 'remove', 'render', 'preview', 'publish', 'discard',
  'record', 'start', 'link', 'leave', 'join', 'post',
  'set', 'append', 'resolve',
];

// camelCase identifiers in skill prose that match the verb-prefix heuristic
// but are actually parameter names or model fields, not tools.
const ALLOWED_NON_TOOLS = new Set([
  'startDate',
  'startTime',
  'setSessionRpe', // confirmed valid — leave to canonical check
]);

// ── helpers ────────────────────────────────────────────────────────────────

function walk(root) {
  const out = [];
  for (const entry of readdirSync(root, { withFileTypes: true })) {
    const abs = join(root, entry.name);
    if (entry.isDirectory()) out.push(...walk(abs));
    else if (entry.isFile()) out.push(abs);
  }
  return out;
}

function parseFrontmatter(rawText) {
  const text = rawText.replace(/\r\n/g, '\n');
  if (!text.startsWith('---\n')) return null;
  const end = text.indexOf('\n---\n', 4);
  if (end === -1) return null;
  const body = text.slice(4, end);
  const fields = {};
  for (const line of body.split('\n')) {
    const m = line.match(/^([A-Za-z_][\w-]*)\s*:\s*(.*)$/);
    if (m) fields[m[1]] = m[2].trim();
  }
  return fields;
}

function looksLikeToolName(token) {
  if (!/^[a-z][A-Za-z0-9]+$/.test(token)) return false;
  return TOOL_VERB_PREFIXES.some((p) =>
    token === p || (token.startsWith(p) && token[p.length] && token[p.length] === token[p.length].toUpperCase()),
  );
}

// ── canonical tool surface from backend Java ───────────────────────────────

function extractToolNamesFromJava(file) {
  const src = readFileSync(file, 'utf8');
  const names = new Set();
  // Match: @Tool(...) (possibly multi-line, with description in """...""")
  // followed by a public <ReturnType> name( signature on a later line.
  const toolPattern = /@Tool\s*\([\s\S]*?\)\s*public\s+[\w<>?,\s.\[\]]+?\s+(\w+)\s*\(/g;
  let m;
  while ((m = toolPattern.exec(src)) !== null) names.add(m[1]);
  return names;
}

function loadCanonicalToolSet() {
  const all = new Set();
  const perFile = new Map();
  let mcpFiles = [];
  try {
    mcpFiles = readdirSync(MCP_DIR)
      .filter((f) => f.startsWith('Mcp') && f.endsWith('Tools.java'))
      .map((f) => join(MCP_DIR, f));
  } catch {
    return { all, perFile, missing: true };
  }
  for (const f of mcpFiles) {
    const names = extractToolNamesFromJava(f);
    perFile.set(basename(f), names);
    for (const n of names) all.add(n);
  }
  return { all, perFile, missing: false };
}

// ── reference extraction ───────────────────────────────────────────────────

function stripCodeFences(text) {
  // Remove fenced code blocks so we don't lint tool names inside JSON examples.
  return text.replace(/```[\s\S]*?```/g, '');
}

function findBacktickTokens(text) {
  const out = [];
  const re = /`([^`\n]+)`/g;
  let m;
  while ((m = re.exec(text)) !== null) out.push(m[1].trim());
  return out;
}

function findMarkdownReferences(text) {
  // `relative/path.md`, `subdir/file.md`, or [text](relative/path.md)
  const refs = new Set();
  for (const m of text.matchAll(/`([\w./-]+\.md)`/g)) refs.add(m[1]);
  for (const m of text.matchAll(/\]\(([\w./-]+\.md)\)/g)) refs.add(m[1]);
  return [...refs];
}

function resolveMdReference(ref, fromFile, skillRoot) {
  // Try same dir, then skill resources/, then skill root, then _shared/resources/
  // — the last one mirrors what package-skills.mjs merges in at build time.
  const candidates = [
    join(dirname(fromFile), ref),
    join(skillRoot, 'resources', ref),
    join(skillRoot, ref),
    join(SHARED_DIR, 'resources', ref),
    join(SHARED_DIR, ref),
  ];
  for (const c of candidates) {
    try { if (statSync(c).isFile()) return c; } catch {}
  }
  return null;
}

// ── shared / skill collision check ─────────────────────────────────────────

// A skill that ships its own file at the same relative path as one in
// skills/_shared/ silently overrides the shared copy at pack time. That's
// almost always an accidental drift (forgotten copy that should've been
// deleted when the file moved to _shared/). Warn so it doesn't go unnoticed.

function listSharedRelPaths() {
  try { statSync(SHARED_DIR); } catch { return new Set(); }
  return new Set(
    walk(SHARED_DIR).map((abs) =>
      relative(SHARED_DIR, abs).split(sep).join('/'),
    ),
  );
}

function checkSharedOverrides(skillDirs, sharedRelPaths) {
  const warnings = [];
  if (sharedRelPaths.size === 0) return warnings;
  for (const dir of skillDirs) {
    const skillName = basename(dir);
    for (const abs of walk(dir)) {
      const rel = relative(dir, abs).split(sep).join('/');
      if (sharedRelPaths.has(rel)) {
        warnings.push(`${skillName}/${rel} overrides _shared/${rel} — delete the local copy or update _shared/`);
      }
    }
  }
  return warnings;
}

// ── lint a single skill ────────────────────────────────────────────────────

function lintSkill(skillDir, canonicalTools) {
  const errors = [];
  const warnings = [];
  const skillName = basename(skillDir);
  const files = walk(skillDir).filter((f) => f.endsWith('.md'));

  for (const file of files) {
    const text = readFileSync(file, 'utf8');
    const relPath = relative(SKILLS_DIR, file).split(sep).join('/');

    // 1) frontmatter on SKILL.md
    if (basename(file) === 'SKILL.md') {
      const fm = parseFrontmatter(text);
      if (!fm) errors.push(`${relPath}: missing or malformed frontmatter`);
      else {
        if (!fm.name) errors.push(`${relPath}: frontmatter missing "name"`);
        if (!fm.description) errors.push(`${relPath}: frontmatter missing "description"`);
      }
    }

    // 2) markdown references resolve
    for (const ref of findMarkdownReferences(text)) {
      // Skip references with placeholders like <slug>.
      if (ref.includes('<') || ref.includes('>')) continue;
      // Skip the profile output filename — written at runtime, not shipped.
      if (ref === 'athlete-profile.md' || ref === 'coach-profile.md') continue;
      if (ref === 'athlete-profile.draft.md' || ref === 'coach-profile.draft.md') continue;
      const resolved = resolveMdReference(ref, file, skillDir);
      if (!resolved) errors.push(`${relPath}: unresolved markdown reference -> ${ref}`);
    }

    // 3) backticked tokens that look like tool calls must exist in @Tool surface
    if (canonicalTools.all.size > 0) {
      const stripped = stripCodeFences(text);
      const tokens = findBacktickTokens(stripped);
      for (const raw of tokens) {
        // Token might be like `getMyProfile`, `getMyProfile()`, `getMyProfile(args)`.
        const head = raw.split('(')[0].trim();
        if (!looksLikeToolName(head)) continue;
        if (canonicalTools.all.has(head)) continue;
        // Allow a small list of camelCase identifiers used in prose that match the
        // tool-name heuristic but are actually parameter names or model fields.
        if (ALLOWED_NON_TOOLS.has(head)) continue;
        warnings.push(`${relPath}: looks like a tool call but no matching @Tool found -> \`${head}\``);
      }
    }
  }

  return { skillName, errors, warnings };
}

// ── main ───────────────────────────────────────────────────────────────────

function main() {
  const skillDirs = readdirSync(SKILLS_DIR, { withFileTypes: true })
    .filter((e) => e.isDirectory() && e.name !== 'dist' && e.name !== 'node_modules' && !e.name.startsWith('_'))
    .map((e) => join(SKILLS_DIR, e.name))
    .filter((d) => {
      try { return statSync(join(d, 'SKILL.md')).isFile(); } catch { return false; }
    })
    .sort();

  if (skillDirs.length === 0) {
    console.error('No skill directories found under', SKILLS_DIR);
    process.exit(1);
  }

  const canonicalTools = loadCanonicalToolSet();
  if (canonicalTools.missing) {
    console.warn(`WARN: backend MCP source not found at ${relative(REPO_ROOT, MCP_DIR)} — skipping tool-name checks.`);
  } else {
    console.log(`Loaded ${canonicalTools.all.size} @Tool names from ${canonicalTools.perFile.size} adapter files.`);
  }

  let totalErrors = 0;
  let totalWarnings = 0;
  for (const dir of skillDirs) {
    const { skillName, errors, warnings } = lintSkill(dir, canonicalTools);
    if (errors.length === 0 && warnings.length === 0) {
      console.log(`✓ ${skillName}`);
      continue;
    }
    console.log(`\n${skillName}`);
    for (const e of errors) console.log(`  ✗ ${e}`);
    for (const w of warnings) console.log(`  ⚠ ${w}`);
    totalErrors += errors.length;
    totalWarnings += warnings.length;
  }

  const overrideWarnings = checkSharedOverrides(skillDirs, listSharedRelPaths());
  if (overrideWarnings.length > 0) {
    console.log('\n_shared overrides');
    for (const w of overrideWarnings) console.log(`  ⚠ ${w}`);
    totalWarnings += overrideWarnings.length;
  }

  console.log(`\n${totalErrors} error(s) · ${totalWarnings} warning(s)`);
  process.exit(totalErrors > 0 ? 1 : 0);
}

main();
