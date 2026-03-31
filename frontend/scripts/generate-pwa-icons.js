#!/usr/bin/env node

const sharp = require('sharp');
const path = require('path');
const fs = require('fs');

const svgPath = path.join(__dirname, '..', 'public', 'assets', 'logo.svg');
const outDir = path.join(__dirname, '..', 'public', 'assets', 'icons');

if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

const sizes = [72, 96, 128, 144, 152, 192, 384, 512];

(async () => {
  for (const size of sizes) {
    await sharp(svgPath).resize(size, size).png().toFile(path.join(outDir, `icon-${size}x${size}.png`));
    console.log(`Generated icon-${size}x${size}.png`);
  }
})();
