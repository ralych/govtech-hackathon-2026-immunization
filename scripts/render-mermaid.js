#!/usr/bin/env node
/**
 * Render all .mmd files under a directory (or a single .mmd file) to SVGs.
 * - Searches recursively under the provided root (default: `docs`).
 * - SVG is created next to the .mmd file with the same name (e.g. foo.mmd -> foo.svg).
 * - Always overwrites; no timestamp or caching.
 * - Does NOT modify any Markdown files.
 *
 * Usage:
 *   node scripts/render-mermaid.js [rootDir]
 *
 * Examples:
 *   node scripts/render-mermaid.js        # scans ./docs
 *   node scripts/render-mermaid.js docs   # same as above
 */

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

function usage() {
  console.error('Usage: node scripts/render-mermaid.js [rootDir]');
  process.exit(2);
}

const args = process.argv.slice(2);

const rootArg = args[0] ? args[0] : 'docs';
const rootPath = path.resolve(rootArg);

if (!fs.existsSync(rootPath)) {
  console.error('Path not found:', rootPath);
  usage();
}

function walk(dir) {
  const out = [];
  const entries = fs.readdirSync(dir);
  for (const e of entries) {
    const full = path.join(dir, e);
    const st = fs.statSync(full);
    if (st.isDirectory()) out.push(...walk(full));
    else if (st.isFile() && path.extname(e).toLowerCase() === '.mmd') out.push(full);
  }
  return out;
}

let mmdFiles = [];
const rootStat = fs.statSync(rootPath);
if (rootStat.isFile()) {
  if (path.extname(rootPath).toLowerCase() === '.mmd') mmdFiles = [rootPath];
  else {
    console.error('Provided file is not a .mmd:', rootPath);
    process.exit(2);
  }
} else if (rootStat.isDirectory()) {
  mmdFiles = walk(rootPath);
}

if (mmdFiles.length === 0) {
  console.log('No .mmd files found under', rootPath);
  process.exit(0);
}

const mmdcCheck = spawnSync('mmdc', ['-v'], { stdio: 'ignore' });
if (mmdcCheck.error) {
  console.error('`mmdc` (mermaid-cli) not found in PATH.');
  console.error('Install locally for testing: npm install -g @mermaid-js/mermaid-cli');
  console.error('In CI the provided workflow installs mermaid-cli automatically.');
  process.exit(1);
}

let created = 0;
for (const src of mmdFiles) {
  const base = path.basename(src, path.extname(src)).replace(/\s+/g, '-');
  const svgPath = path.join(path.dirname(src), `${base}.svg`);

  console.log(`Rendering: ${path.relative(process.cwd(), src)} -> ${path.relative(process.cwd(), svgPath)}`);
  const res = spawnSync('mmdc', ['-i', src, '-o', svgPath, '--backgroundColor', 'transparent'], { stdio: 'inherit' });
  if (res.status !== 0) {
    console.error('mmdc failed for', src);
    process.exit(res.status || 1);
  }
  created++;
}

console.log(`Created ${created} SVG(s).`);
