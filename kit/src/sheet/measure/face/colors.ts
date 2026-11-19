import { channelDistance } from "../../core/color.ts";
import type { Mask } from "../../core/mask.ts";
import { pixelAt, type Pixel, type Raster } from "../../raster/image.ts";

/** The colour space is cut into cells this wide in each channel to find the commonest colour. */
const CELL = 16;
/** Pixels this near the commonest cell's middle make up its colour. */
const CELL_REACH = 24;
/** A pale white is this much lighter than the skin or less than this far under its brightness, and as colourless as this share of the skin's colour or this many levels. */
const WHITE_DARKER = 6;
const WHITE_CHROMA_SHARE = 0.45;
const WHITE_CHROMA_FLOOR = 14;
/** A white nearer the skin than this is the skin. */
const WHITE_APART = 12;

/** The mask pixels in the pixel box `x0..x1, y0..y1` of the mask's own pixels (image pixels when the mask covers the image). */
export function maskedPixels(image: Raster, mask: Mask, x0: number, y0: number, x1: number, y1: number, ox = 0, oy = 0): Pixel[] {
  const out: Pixel[] = [];
  for (let y = Math.max(0, y0); y < Math.min(mask.height, y1); y++) {
    for (let x = Math.max(0, x0); x < Math.min(mask.width, x1); x++) if (mask.data[y * mask.width + x]) out.push(pixelAt(image, x + ox, y + oy));
  }
  return out;
}

/** The commonest colour of `pixels`: the median of those within reach of the fullest cell of the colour space (smoothed with its neighbours), undefined for none. */
export function dominantColor(pixels: readonly Pixel[]): Pixel | undefined {
  if (pixels.length === 0) return undefined;
  const cells = 256 / CELL;
  const counts = new Float64Array(cells * cells * cells);
  const index = (p: Pixel) => ((p[0] / CELL) | 0) * cells * cells + ((p[1] / CELL) | 0) * cells + ((p[2] / CELL) | 0);
  for (const p of pixels) counts[index(p)]++;
  let best = 0;
  let bestScore = -1;
  for (let r = 0; r < cells; r++) {
    for (let g = 0; g < cells; g++) {
      for (let b = 0; b < cells; b++) {
        let score = 0;
        for (let dr = -1; dr <= 1; dr++) for (let dg = -1; dg <= 1; dg++) for (let db = -1; db <= 1; db++) {
          const rr = r + dr;
          const gg = g + dg;
          const bb = b + db;
          if (rr >= 0 && gg >= 0 && bb >= 0 && rr < cells && gg < cells && bb < cells) score += counts[rr * cells * cells + gg * cells + bb];
        }
        if (score > bestScore) {
          bestScore = score;
          best = r * cells * cells + g * cells + b;
        }
      }
    }
  }
  const centre: Pixel = [(((best / (cells * cells)) | 0) + 0.5) * CELL, ((((best / cells) | 0) % cells) + 0.5) * CELL, ((best % cells) + 0.5) * CELL];
  const near = pixels.filter((p) => channelDistance(p, centre) <= CELL_REACH);
  const pick = near.length > 0 ? near : pixels;
  return [0, 1, 2].map((c) => pick.map((p) => p[c]).sort((a, b) => a - b)[pick.length >> 1]) as Pixel;
}

export function chroma(p: Pixel): number {
  return Math.max(p[0], p[1], p[2]) - Math.min(p[0], p[1], p[2]);
}

function brightness(p: Pixel): number {
  return (p[0] + p[1] + p[2]) / 3;
}

/** Whether `p` could be the white of an eye on skin of colour `skin`: no darker than it, much less coloured, and not the skin itself. */
export function isEyeWhite(p: Pixel, skin: Pixel): boolean {
  if (brightness(p) < brightness(skin) - WHITE_DARKER) return false;
  if (chroma(p) > Math.max(WHITE_CHROMA_FLOOR, WHITE_CHROMA_SHARE * chroma(skin))) return false;
  return channelDistance(p, skin) >= WHITE_APART;
}

/** Hue in degrees 0 to 360 and saturation 0 to 1 of a pixel. */
export function hueSaturation(p: Pixel): { hue: number; saturation: number } {
  const hi = Math.max(p[0], p[1], p[2]);
  const lo = Math.min(p[0], p[1], p[2]);
  const d = hi - lo;
  if (d === 0) return { hue: 0, saturation: 0 };
  const h = hi === p[0] ? ((p[1] - p[2]) / d + 6) % 6 : hi === p[1] ? (p[2] - p[0]) / d + 2 : (p[0] - p[1]) / d + 4;
  return { hue: h * 60, saturation: d / hi };
}

/** The smaller angle between two hues, in degrees. */
export function hueGap(a: number, b: number): number {
  const d = Math.abs(a - b) % 360;
  return d > 180 ? 360 - d : d;
}
