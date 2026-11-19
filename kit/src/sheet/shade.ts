import type { Background } from "./background.ts";
import type { Box } from "./core/box.ts";
import { blankMask, erode, type Mask } from "./core/mask.ts";
import { pixelAt, type Raster } from "./raster/image.ts";

/** A shadow darkens the background by a share of each channel between these. */
const SHADE_LIGHTEST = 0.97;
const SHADE_DARKEST = 0.5;
/** How far the channels' shares may differ: a shadow keeps the background's hue, give or take a tint. */
const SHADE_TINT = 0.18;
/** A cast shadow lies in the lowest share of a figure's height. */
const SHADOW_BAND = 0.3;

/** Anti-aliased ink edges read as shade in a ring a pixel or two wide; a cast shadow is thicker than this share of the figure's height. */
const RING_SHARE = 0.004;

/** The thick part of a shade mask and the shade pixels within reach of it: the cast shadow without the thin ring along ink edges. */
function castShadow(shade: Mask, ring: number): Mask {
  const core = erode(shade, ring);
  const reach = ring + 1;
  const out = blankMask(shade.width, shade.height);
  for (let y = 0; y < shade.height; y++) {
    for (let x = 0; x < shade.width; x++) {
      if (!core.data[y * shade.width + x]) continue;
      for (let dy = -reach; dy <= reach; dy++) {
        for (let dx = -reach; dx <= reach; dx++) {
          const nx = x + dx;
          const ny = y + dy;
          if (nx < 0 || ny < 0 || nx >= shade.width || ny >= shade.height) continue;
          const at = ny * shade.width + nx;
          if (shade.data[at]) out.data[at] = 1;
        }
      }
    }
  }
  return out;
}

/** Whether a pixel reads as the background in shadow: the same hue, uniformly darker. */
export function isShade(image: Raster, background: Pick<Background, "colorAt">, x: number, y: number): boolean {
  const p = pixelAt(image, x, y);
  const b = background.colorAt(x, y);
  const ratios = [0, 1, 2].map((c) => p[c] / Math.max(1, b[c]));
  const mean = (ratios[0] + ratios[1] + ratios[2]) / 3;
  return mean >= SHADE_DARKEST && mean <= SHADE_LIGHTEST && Math.max(...ratios) - Math.min(...ratios) <= SHADE_TINT && Math.max(...ratios) <= 1;
}

/** The share of the pixels that read as shade. */
export function shadeShare(image: Raster, background: Background, pixels: Int32Array): number {
  let n = 0;
  for (const at of pixels) if (isShade(image, background, at % image.width, Math.floor(at / image.width))) n++;
  return n / Math.max(1, pixels.length);
}

/**
 * Splits a cast shadow off the bottom of a figure: shade pixels in the lowest band of `box`, reached from the background through
 * shade only. Returns the figure without them and the shadow.
 */
export function splitShadow(image: Raster, background: Background, figure: Mask, box: Box): { figure: Mask; shadow: Mask } {
  const { width, height } = figure;
  const fromRow = Math.floor(box.y + box.h * (1 - SHADOW_BAND));
  const shadow = blankMask(width, height);
  const candidate = (x: number, y: number) =>
    x >= 0 && y >= fromRow && x < width && y < height && figure.data[y * width + x] === 1 && shadow.data[y * width + x] === 0 && isShade(image, background, x, y);
  const stack: Array<[number, number]> = [];
  for (let y = fromRow; y < Math.min(height, box.y + box.h); y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      if (!candidate(x, y)) continue;
      const touchesGround = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => {
        const nx = x + dx;
        const ny = y + dy;
        return nx < 0 || ny < 0 || nx >= width || ny >= height || background.figure.data[ny * width + nx] === 0;
      });
      if (!touchesGround) continue;
      shadow.data[y * width + x] = 1;
      stack.push([x, y]);
    }
  }
  while (stack.length > 0) {
    const [x, y] = stack.pop()!;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      if (!candidate(x + dx, y + dy)) continue;
      shadow.data[(y + dy) * width + x + dx] = 1;
      stack.push([x + dx, y + dy]);
    }
  }
  const cast = castShadow(shadow, Math.max(1, Math.round(box.h * RING_SHARE)));
  shadow.data.set(cast.data);
  const rest = blankMask(width, height);
  for (let i = 0; i < rest.data.length; i++) rest.data[i] = figure.data[i] && !shadow.data[i] ? 1 : 0;
  return { figure: rest, shadow };
}
