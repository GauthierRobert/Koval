import sharp from 'sharp';
import { existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const OUT = join(ROOT, 'public/assets/icons');
const LOGO = join(ROOT, 'public/assets/logo.svg');
const BG = { r: 15, g: 15, b: 17 };

// Only the sizes that pwa-asset-generator failed to generate
const MISSING = [
  [1179, 2556], [2556, 1179],
  [1170, 2532], [2532, 1170],
  [1284, 2778], [2778, 1284],
  [1125, 2436], [2436, 1125],
  [1242, 2688], [2688, 1242],
  [828, 1792],  [1792, 828],
  [1242, 2208], [2208, 1242],
  [750, 1334],  [1334, 750],
  [640, 1136],  [1136, 640],
];

for (const [w, h] of MISSING) {
  const outPath = join(OUT, `apple-splash-${w}-${h}.jpg`);
  if (existsSync(outPath)) {
    console.log(`skip  apple-splash-${w}-${h}.jpg`);
    continue;
  }

  const logoSize = Math.round(Math.min(w, h) * 0.28);
  const logoBuf = await sharp(LOGO).resize(logoSize, logoSize).png().toBuffer();

  await sharp({ create: { width: w, height: h, channels: 3, background: BG } })
    .composite([{ input: logoBuf, gravity: 'centre' }])
    .jpeg({ quality: 90 })
    .toFile(outPath);

  console.log(`saved apple-splash-${w}-${h}.jpg`);
}

console.log('done');
