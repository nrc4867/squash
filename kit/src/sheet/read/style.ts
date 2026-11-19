import type { Pixel } from "../raster/image.ts";
import type { LandmarkStatus } from "../measure/landmark.ts";

/** Colours the reading images draw in. */
export const INK: Pixel = [20, 20, 28];
export const PAPER: Pixel = [255, 255, 255];
export const MUTED: Pixel = [140, 140, 150];
export const AXIS: Pixel = [120, 60, 200];
export const HEAD_LINE: Pixel = [70, 140, 230];

/** One colour for each view's box on the overview, in turn. */
export const VIEW_COLORS: Pixel[] = [
  [230, 50, 50],
  [30, 150, 60],
  [40, 90, 230],
  [220, 130, 0],
  [170, 40, 190],
  [0, 160, 170],
];

export const STATUS_COLORS: Record<LandmarkStatus, Pixel> = {
  found: [20, 150, 60],
  estimated: [225, 120, 0],
  given: [40, 90, 230],
};

/** The scale labels are drawn at, for an image this many pixels on its longer side. */
export function labelScale(longSide: number): number {
  return Math.max(1, Math.min(4, Math.round(longSide / 600)));
}
