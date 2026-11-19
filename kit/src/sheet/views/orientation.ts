import { luminance } from "../core/color.ts";
import { maskAt, type Mask } from "../core/mask.ts";
import { clamp } from "../core/solve.ts";
import type { Box } from "../core/box.ts";
import { pixelAt, type Raster } from "../raster/image.ts";

export type Facing = "front" | "three-quarter" | "side" | "back";

export interface FacingGuess {
  facing: Facing;
  /** 0 to 1. */
  confidence: number;
  /** How well the silhouette matches its own mirror image about its best upright axis, 0 to 1. */
  symmetry: number;
  /** The share of the head band's inside that is dark detail (eyes, mouth, brows): a front shows more than a back. */
  detail: number;
}

/** Symmetry is read over the top of the figure, head and body, where tails, props and loose arms disturb it least. */
const SYMMETRY_BANDS = [0.25, 0.55];
/** Symmetry at or above this reads as a front or a back, below the lower as a side, between as three-quarter. */
const SYMMETRIC = 0.86;
const ASYMMETRIC = 0.72;
/** The band of the figure's height, from its top, read for face detail. */
const HEAD_BAND = 0.35;
/** Pixels darker than this luminance are detail. */
const DARK = 0.4;
/** Inner pixels are at least this far, in pixels of the scaled mask, from the background. */
const INSET = 2;
/** The figure is scaled to about this many rows for the symmetry test. */
const SAMPLE_ROWS = 160;
/** A symmetric view with less head detail than this share of the most detailed symmetric view's reads as a back. */
const BACK_DETAIL_SHARE = 0.5;
/** A lone symmetric view with less head detail than this reads as a back. */
const BACK_DETAIL = 0.01;
/** Axis positions tried, as shares of the box's width either side of its middle. */
const AXIS_SEARCH = 0.15;
const AXIS_STEPS = 30;

/** How well the figure matches its mirror image about the best upright axis near the middle of its box. */
export function symmetryOf(mask: Mask, box: Box): number {
  const step = Math.max(1, Math.floor(box.h / SAMPLE_ROWS));
  let best = 0;
  for (let k = -AXIS_STEPS; k <= AXIS_STEPS; k++) {
    const axis2 = 2 * box.x + box.w - 1 + Math.round((2 * AXIS_SEARCH * box.w * k) / AXIS_STEPS);
    let both = 0;
    let either = 0;
    for (let y = box.y; y < box.y + box.h; y += step) {
      for (let x = box.x; x < box.x + box.w; x += step) {
        const a = maskAt(mask, x, y);
        const b = maskAt(mask, axis2 - x, y);
        if (a && b) both++;
        if (a || b) either++;
      }
    }
    if (either > 0) best = Math.max(best, both / either);
  }
  return best;
}

/** The share of the head band's inner pixels that are dark. */
export function headDetail(image: Raster, mask: Mask, box: Box): number {
  let inner = 0;
  let dark = 0;
  const inset = Math.max(INSET, Math.round(box.h / 200));
  for (let y = box.y; y < box.y + box.h * HEAD_BAND; y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      if (!maskAt(mask, x, y)) continue;
      const isInner = maskAt(mask, x - inset, y) && maskAt(mask, x + inset, y) && maskAt(mask, x, y - inset) && maskAt(mask, x, y + inset);
      if (!isInner) continue;
      inner++;
      if (luminance(pixelAt(image, x, y)) < DARK) dark++;
    }
  }
  return dark / Math.max(1, inner);
}

/** The figure's symmetry over its head and upper body. */
export function upperSymmetry(mask: Mask, box: Box): number {
  const bands = SYMMETRY_BANDS.map((share) => symmetryOf(mask, { ...box, h: Math.max(1, Math.round(box.h * share)) }));
  return bands.reduce((a, b) => a + b, 0) / bands.length;
}

/** Guesses each view's facing from its symmetry, and front from back by head detail against the other symmetric views. */
export function guessFacings(image: Raster, views: ReadonlyArray<{ mask: Mask; box: Box }>): FacingGuess[] {
  const read = views.map((v) => ({ symmetry: upperSymmetry(v.mask, v.box), detail: headDetail(image, v.mask, v.box) }));
  const symmetric = read.filter((r) => r.symmetry >= SYMMETRIC);
  const mostDetail = Math.max(0, ...symmetric.map((r) => r.detail));
  return read.map(({ symmetry, detail }) => {
    if (symmetry >= SYMMETRIC) {
      const back = symmetric.length > 1 ? detail < BACK_DETAIL_SHARE * mostDetail : detail < BACK_DETAIL;
      const margin = symmetric.length > 1 ? Math.abs(detail - BACK_DETAIL_SHARE * mostDetail) / Math.max(1e-6, mostDetail) : Math.abs(detail - BACK_DETAIL) / BACK_DETAIL;
      const confidence = clamp(0.4 + 0.3 * Math.min(1, margin) + 2 * (symmetry - SYMMETRIC), 0.2, 0.95);
      return { facing: back ? "back" : "front", confidence, symmetry, detail };
    }
    if (symmetry < ASYMMETRIC) return { facing: "side", confidence: clamp(0.5 + 2 * (ASYMMETRIC - symmetry), 0.3, 0.9), symmetry, detail };
    const middle = (SYMMETRIC + ASYMMETRIC) / 2;
    const confidence = clamp(0.6 - (2 * Math.abs(symmetry - middle)) / (SYMMETRIC - ASYMMETRIC) * 0.3, 0.3, 0.6);
    return { facing: "three-quarter", confidence, symmetry, detail };
  });
}
