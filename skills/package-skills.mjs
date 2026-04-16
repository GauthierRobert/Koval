#!/usr/bin/env node
// Zero-dependency ZIP packager for the Koval end-user skills.
// Walks every immediate subdirectory of skills/ that contains a SKILL.md
// and writes skills/dist/<skill-name>.zip with the skill folder at the archive root.
//
// Run:  node skills/package-skills.mjs
//
// The output ZIPs match the layout Claude Desktop and Claude.ai expect:
//   koval-analyze-last-ride.zip
//     └── koval-analyze-last-ride/
//           └── SKILL.md
//           └── resources/  (only if the skill ships resources)

import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateRawSync } from 'node:zlib';
import { Buffer } from 'node:buffer';

const SKILLS_DIR = dirname(fileURLToPath(import.meta.url));
const DIST_DIR = join(SKILLS_DIR, 'dist');

let crcTable = null;
function crc32(buf) {
  if (!crcTable) {
    crcTable = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c >>> 0;
    }
  }
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function walk(root, prefix = '') {
  const out = [];
  for (const entry of readdirSync(root, { withFileTypes: true })) {
    const abs = join(root, entry.name);
    const rel = prefix ? prefix + '/' + entry.name : entry.name;
    if (entry.isDirectory()) out.push(...walk(abs, rel));
    else if (entry.isFile()) out.push({ relPath: rel, absPath: abs });
  }
  return out;
}

function dosTime(date) {
  const time =
    ((date.getHours() & 0x1f) << 11) |
    ((date.getMinutes() & 0x3f) << 5) |
    ((date.getSeconds() / 2) & 0x1f);
  const dt =
    (((date.getFullYear() - 1980) & 0x7f) << 9) |
    (((date.getMonth() + 1) & 0xf) << 5) |
    (date.getDate() & 0x1f);
  return { time, date: dt };
}

function buildZip(files) {
  const local = [];
  const central = [];
  let offset = 0;
  const { time: dosT, date: dosD } = dosTime(new Date());

  for (const { name, data } of files) {
    const compressed = deflateRawSync(data);
    const crc = crc32(data);
    const nameBuf = Buffer.from(name, 'utf8');

    const lfh = Buffer.alloc(30);
    lfh.writeUInt32LE(0x04034b50, 0);
    lfh.writeUInt16LE(20, 4);
    lfh.writeUInt16LE(0x0800, 6);
    lfh.writeUInt16LE(8, 8);
    lfh.writeUInt16LE(dosT, 10);
    lfh.writeUInt16LE(dosD, 12);
    lfh.writeUInt32LE(crc, 14);
    lfh.writeUInt32LE(compressed.length, 18);
    lfh.writeUInt32LE(data.length, 22);
    lfh.writeUInt16LE(nameBuf.length, 26);
    lfh.writeUInt16LE(0, 28);
    local.push(lfh, nameBuf, compressed);

    const cdh = Buffer.alloc(46);
    cdh.writeUInt32LE(0x02014b50, 0);
    cdh.writeUInt16LE(20, 4);
    cdh.writeUInt16LE(20, 6);
    cdh.writeUInt16LE(0x0800, 8);
    cdh.writeUInt16LE(8, 10);
    cdh.writeUInt16LE(dosT, 12);
    cdh.writeUInt16LE(dosD, 14);
    cdh.writeUInt32LE(crc, 16);
    cdh.writeUInt32LE(compressed.length, 20);
    cdh.writeUInt32LE(data.length, 24);
    cdh.writeUInt16LE(nameBuf.length, 28);
    cdh.writeUInt16LE(0, 30);
    cdh.writeUInt16LE(0, 32);
    cdh.writeUInt16LE(0, 34);
    cdh.writeUInt16LE(0, 36);
    cdh.writeUInt32LE(0, 38);
    cdh.writeUInt32LE(offset, 42);
    central.push(cdh, nameBuf);

    offset += lfh.length + nameBuf.length + compressed.length;
  }

  const localPart = Buffer.concat(local);
  const centralPart = Buffer.concat(central);

  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(files.length, 8);
  eocd.writeUInt16LE(files.length, 10);
  eocd.writeUInt32LE(centralPart.length, 12);
  eocd.writeUInt32LE(localPart.length, 16);
  eocd.writeUInt16LE(0, 20);

  return Buffer.concat([localPart, centralPart, eocd]);
}

function packSkill(skillDir) {
  const skillName = basename(skillDir);
  const files = walk(skillDir)
    .sort((a, b) => a.relPath.localeCompare(b.relPath))
    .map(({ relPath, absPath }) => ({
      name: skillName + '/' + relPath,
      data: readFileSync(absPath),
    }));
  if (!files.some((f) => f.name === skillName + '/SKILL.md')) {
    throw new Error(`${skillName}: missing SKILL.md at the skill root`);
  }
  return buildZip(files);
}

function main() {
  if (!existsSync(DIST_DIR)) mkdirSync(DIST_DIR, { recursive: true });

  const skillDirs = readdirSync(SKILLS_DIR, { withFileTypes: true })
    .filter((e) => e.isDirectory() && e.name !== 'dist' && e.name !== 'node_modules')
    .map((e) => join(SKILLS_DIR, e.name))
    .filter((d) => existsSync(join(d, 'SKILL.md')))
    .sort();

  if (skillDirs.length === 0) {
    console.error('No skill directories found under', SKILLS_DIR);
    process.exit(1);
  }

  let totalBytes = 0;
  for (const dir of skillDirs) {
    const skillName = basename(dir);
    const zip = packSkill(dir);
    writeFileSync(join(DIST_DIR, skillName + '.zip'), zip);
    totalBytes += zip.length;
    console.log(`packed ${skillName}.zip (${(zip.length / 1024).toFixed(1)} KB)`);
  }
  console.log(`\n${skillDirs.length} skills · ${(totalBytes / 1024).toFixed(1)} KB total · dist/`);
}

main();
