import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, extname } from "node:path";
import jpeg from "jpeg-js";
import { PNG } from "pngjs";
import type { Raster } from "./image.ts";

const JPEG_LIMIT_MB = 1024;

/** Reads a PNG or JPEG file; transparent pixels are laid over white. */
export function readRaster(path: string): Raster {
  const bytes = readFileSync(path);
  const isPng = bytes.length > 4 && bytes[0] === 0x89 && bytes[1] === 0x50;
  const decoded = isPng
    ? PNG.sync.read(bytes)
    : jpeg.decode(bytes, { useTArray: true, formatAsRGBA: true, maxMemoryUsageInMB: JPEG_LIMIT_MB });
  const data = new Uint8Array(decoded.data);
  for (let i = 0; i < data.length; i += 4) {
    const a = data[i + 3] / 255;
    if (a === 1) continue;
    for (let c = 0; c < 3; c++) data[i + c] = Math.round(data[i + c] * a + 255 * (1 - a));
    data[i + 3] = 255;
  }
  return { width: decoded.width, height: decoded.height, data };
}

/** Writes a PNG, making its folder; returns the path. */
export function writePng(path: string, image: Raster): string {
  if (extname(path).toLowerCase() !== ".png") throw new Error(`${path}: images are written as .png`);
  mkdirSync(dirname(path), { recursive: true });
  const png = new PNG({ width: image.width, height: image.height });
  png.data = Buffer.from(image.data.buffer, image.data.byteOffset, image.data.byteLength);
  writeFileSync(path, PNG.sync.write(png));
  return path;
}
