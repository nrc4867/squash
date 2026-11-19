import { luminance } from "../core/color.ts";
import type { Mask } from "../core/mask.ts";
import type { Box } from "../core/box.ts";
import { pixelAt, type Raster } from "../raster/image.ts";

/** Pixels darker than this luminance may be ink. */
const INK_DARK = 0.3;
/** Ink lines are thinner than this share of the figure's height, at least a few pixels. */
const INK_THIN = 0.012;
const INK_THIN_MIN = 3;

/** Dark pixels in thin runs across or down: the drawing's ink lines, not dark areas such as black fur. */
export function inkMask(image: Raster, figure: Mask, box: Box): Uint8Array {
  const { width } = figure;
  const thin = Math.max(INK_THIN_MIN, Math.round(INK_THIN * box.h));
  const dark = new Uint8Array(width * figure.height);
  for (let y = box.y; y < box.y + box.h; y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      const at = y * width + x;
      dark[at] = figure.data[at] && luminance(pixelAt(image, x, y)) < INK_DARK ? 1 : 0;
    }
  }
  const ink = new Uint8Array(width * figure.height);
  const markRuns = (count: number, length: number, index: (i: number, j: number) => number) => {
    for (let i = 0; i < count; i++) {
      let start = -1;
      for (let j = 0; j <= length; j++) {
        const on = j < length && dark[index(i, j)] === 1;
        if (on && start < 0) start = j;
        if (!on && start >= 0) {
          if (j - start <= thin) for (let k = start; k < j; k++) ink[index(i, k)] = 1;
          start = -1;
        }
      }
    }
  };
  markRuns(box.h, box.w, (i, j) => (box.y + i) * width + box.x + j);
  markRuns(box.w, box.h, (i, j) => (box.y + j) * width + box.x + i);
  return ink;
}

/** Pixels next to ink or the silhouette's edge, where colours blend: left out of a palette. */
export function blendedMask(figure: Mask, ink: Uint8Array, box: Box): Uint8Array {
  const { width } = figure;
  const out = new Uint8Array(width * figure.height);
  for (let y = box.y; y < box.y + box.h; y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      const at = y * width + x;
      if (!figure.data[at] || ink[at]) continue;
      for (let dy = -1; dy <= 1 && !out[at]; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          const n = (y + dy) * width + x + dx;
          if (n < 0 || n >= out.length || !figure.data[n] || ink[n]) {
            out[at] = 1;
            break;
          }
        }
      }
    }
  }
  return out;
}
