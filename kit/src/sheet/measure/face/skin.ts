import type { Mask } from "../../core/mask.ts";
import { pixelAt, type Pixel, type Raster } from "../../raster/image.ts";
import type { Anatomy } from "../anatomy.ts";
import { headRun } from "../head.ts";

/** Skin is sampled between these shares of the head's length below the crown, either side of the middle by this share of its width. */
const SKIN_ROWS: [number, number] = [0.72, 0.92];
const SKIN_HALF_WIDTH = 0.14;

function medianColor(image: Raster, mask: Mask, x0: number, x1: number, y0: number, y1: number): Pixel | undefined {
  const values: number[][] = [[], [], []];
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      if (!mask.data[y * mask.width + x]) continue;
      const p = pixelAt(image, x, y);
      for (let c = 0; c < 3; c++) values[c].push(p[c]);
    }
  }
  if (values[0].length === 0) return undefined;
  return values.map((v) => v.sort((a, b) => a - b)[v.length >> 1]) as Pixel;
}

/** The skin's colour: the median of the face's lower middle, round the chin and below the mouth; undefined where the head is not read. */
export function skinColor(image: Raster, anatomy: Anatomy): Pixel | undefined {
  const widest = headRun(anatomy.figure, anatomy.head.widestRow);
  if (!widest) return undefined;
  const length = anatomy.marks.chin.y - anatomy.marks.crown.y;
  const x0 = Math.round((widest.l + widest.r) / 2);
  const hw = widest.r - widest.l + 1;
  return medianColor(
    image,
    anatomy.figure.mask,
    Math.round(x0 - SKIN_HALF_WIDTH * hw),
    Math.round(x0 + SKIN_HALF_WIDTH * hw),
    Math.round(anatomy.marks.crown.y + SKIN_ROWS[0] * length),
    Math.round(anatomy.marks.crown.y + SKIN_ROWS[1] * length),
  );
}
