import { channelDistance, luminance } from "../core/color.ts";
import { figureOf } from "../measure/figure.ts";
import { readHead } from "../measure/head.ts";
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
  /** The share of the head's inside within reach of the colour of the head's top: a back is nearly all hair, a front has a face. */
  cover: number;
  /** What the guess rests on and why it may be wrong, when it is a weak call: front and three-quarter are told by silhouette symmetry alone. */
  reason?: string;
}

/** Symmetry is read over the top of the figure, head and body, where tails, props and loose arms disturb it least. */
const SYMMETRY_BANDS = [0.2, 0.27, 0.55];
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
/** The colour of the head's top is the median of this band of the head, as shares of its length from the crown. */
const TOP_BAND: [number, number] = [0.05, 0.2];
/** Pixels within this many channel levels of the head's top colour are covered by it. */
const COVER_REACH = 80;
/** A symmetric view whose head is covered at least this much by one colour reads as a back; at most the lower, as a face. */
const BACK_COVER = 0.88;
const FACE_COVER = 0.82;
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

/** The share of the head's inner pixels within reach of the colour of its top, and the inner pixels' count. */
export function headCover(image: Raster, mask: Mask, box: Box): number {
  const head = readHead(figureOf(mask, box));
  const crown = head.crown.y;
  const length = Math.max(8, (head.chin?.y ?? crown + 0.2 * box.h) - crown);
  const inset = Math.max(INSET, Math.round(box.h / 200));
  const inside = (x: number, y: number) => maskAt(mask, x, y) && maskAt(mask, x - inset, y) && maskAt(mask, x + inset, y) && maskAt(mask, x, y - inset) && maskAt(mask, x, y + inset);
  const top: number[][] = [[], [], []];
  for (let y = Math.round(crown + TOP_BAND[0] * length); y < crown + TOP_BAND[1] * length; y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      if (!inside(x, y)) continue;
      const p = pixelAt(image, x, y);
      for (let c = 0; c < 3; c++) top[c].push(p[c]);
    }
  }
  if (top[0].length === 0) return 0;
  const middle = top.map((values) => values.sort((a, b) => a - b)[values.length >> 1]) as [number, number, number];
  let covered = 0;
  let all = 0;
  for (let y = Math.round(crown + TOP_BAND[0] * length); y < crown + length; y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      if (!inside(x, y)) continue;
      all++;
      if (channelDistance(pixelAt(image, x, y), middle) <= COVER_REACH) covered++;
    }
  }
  return covered / Math.max(1, all);
}

/** The figure's symmetry over its head and upper body. */
export function upperSymmetry(mask: Mask, box: Box): number {
  const bands = SYMMETRY_BANDS.map((share) => symmetryOf(mask, { ...box, h: Math.max(1, Math.round(box.h * share)) }));
  return bands.reduce((a, b) => a + b, 0) / bands.length;
}

const WEAK_CALL = "front and three-quarter are told apart by silhouette symmetry alone: look at the view to confirm";

/**
 * Guesses each view's facing: a back from a front by how much of the head is one colour, side, three-quarter and front by the
 * silhouette's symmetry. A call that rests on symmetry alone, or on a head that is neither clearly a face nor clearly hair, carries a
 * `reason` and a low confidence.
 */
export function guessFacings(image: Raster, views: ReadonlyArray<{ mask: Mask; box: Box }>): FacingGuess[] {
  return views.map((v) => {
    const symmetry = upperSymmetry(v.mask, v.box);
    const detail = headDetail(image, v.mask, v.box);
    const cover = headCover(image, v.mask, v.box);
    const base = { symmetry, detail, cover };
    if (symmetry >= SYMMETRIC) {
      const clear = cover >= BACK_COVER || cover <= FACE_COVER;
      const back = cover >= BACK_COVER;
      const margin = Math.min(1, back ? (cover - BACK_COVER) / (1 - BACK_COVER) : (FACE_COVER - cover) / FACE_COVER);
      const confidence = clamp((clear ? 0.45 : 0.3) + 0.3 * margin + 2 * (symmetry - SYMMETRIC), 0.2, 0.95);
      if (back) return { facing: "back", confidence, ...base, reason: "the head is one colour: a bald or helmeted front would read the same" };
      return { facing: "front", confidence, ...base, ...(clear ? { reason: WEAK_CALL } : { reason: `the head is neither clearly a face nor clearly hair (${Math.round(cover * 100)}% one colour); ${WEAK_CALL}` }) };
    }
    if (symmetry < ASYMMETRIC) return { facing: "side", confidence: clamp(0.5 + 2 * (ASYMMETRIC - symmetry), 0.3, 0.9), ...base };
    const middle = (SYMMETRIC + ASYMMETRIC) / 2;
    const confidence = clamp(0.6 - (2 * Math.abs(symmetry - middle)) / (SYMMETRIC - ASYMMETRIC) * 0.3, 0.3, 0.6);
    return { facing: "three-quarter", confidence, ...base, reason: "a lifted arm or a tilted head can pull a front down to this; " + WEAK_CALL };
  });
}
