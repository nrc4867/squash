import { blankMask, components, type Component } from "../core/mask.ts";
import type { Anatomy } from "./anatomy.ts";
import type { Run } from "./rows.ts";

/** With no ankle mark, the ankle is this share of the figure's height (ground to crown) above the ground. */
const ANKLE_SHARE = 0.08;
/** A piece of the band below the ankle is a foot when its lowest row is within this many pixels of the ground. */
const GROUND_REACH = 2;
/** The notch is counted over the band's rows from this share of the way from its top to the ground: the feet, not the ankles above them. */
const NOTCH_FROM = 0.5;
/** The notch between two feet is looked for between these shares of their joint span. */
const NOTCH_SEARCH: [number, number] = [0.15, 0.85];
/** A column is a notch when it holds at most this share of the lower of the two peaks either side of it. */
const NOTCH_DEPTH = 0.6;
/** The gap between the legs splits joined feet when it lies within this middle share of their span. */
const LEGS_SPLIT_MIDDLE: [number, number] = [0.2, 0.8];

/** How two feet were told apart: by a gap of paper, by a notch in their outline, by the gap between the legs above, or not at all. */
export type FootSplitHow = "gap" | "notch" | "legs" | "one";

export interface FootPixels {
  side: "image-left" | "image-right";
  /** The foot's pixel indices in the sheet, from the ankle row to the ground. */
  pixels: Int32Array;
  /** Each row's stretch of this foot, `rows.get(y)`. */
  rows: Map<number, Run>;
}

export interface FootSplit {
  feet: FootPixels[];
  /** The column between the two feet; pixels left of it are the image-left foot's. */
  divider?: number;
  how: FootSplitHow;
}

/** The ankle's row: its mark, or `ANKLE_SHARE` of the figure's height above the ground. */
export function ankleRow(anatomy: Anatomy): number {
  const { ground } = anatomy.figure;
  return anatomy.marks.ankle?.y ?? Math.round(ground - ANKLE_SHARE * (ground - anatomy.marks.crown.y));
}

/** The column between the legs at the crotch, or the figure's axis. */
function legsSplit(anatomy: Anatomy): number {
  const legs = anatomy.legs;
  const left = legs?.left.find((r) => r !== undefined);
  const right = legs?.right.find((r) => r !== undefined);
  return left && right && left.r < right.l ? Math.round((left.r + right.l) / 2) : anatomy.figure.axis;
}

/** Where to split the feet's band: the gap or deepest notch in its column counts, else the gap between the legs. */
function dividerOf(counts: Map<number, number>, l: number, r: number, legs: number): { divider?: number; how: FootSplitHow } {
  const span = r - l + 1;
  const at = (x: number) => counts.get(x) ?? 0;
  const from = Math.round(l + NOTCH_SEARCH[0] * span);
  const to = Math.round(l + NOTCH_SEARCH[1] * span);
  const middle = (l + r) / 2;
  let best: number | undefined;
  for (let x = from; x <= to; x++) {
    if (best === undefined || at(x) < at(best) || (at(x) === at(best) && Math.abs(x - middle) < Math.abs(best - middle))) best = x;
  }
  if (best !== undefined) {
    let peakLeft = 0;
    let peakRight = 0;
    for (let x = l; x < best; x++) peakLeft = Math.max(peakLeft, at(x));
    for (let x = best + 1; x <= r; x++) peakRight = Math.max(peakRight, at(x));
    if (at(best) === 0) return { divider: best, how: "gap" };
    if (at(best) <= NOTCH_DEPTH * Math.min(peakLeft, peakRight)) return { divider: best, how: "notch" };
  }
  const share = (legs - l) / Math.max(1, span);
  return share >= LEGS_SPLIT_MIDDLE[0] && share <= LEGS_SPLIT_MIDDLE[1] ? { divider: legs, how: "legs" } : { how: "one" };
}

function rowsOf(pixels: readonly number[], width: number): Map<number, Run> {
  const rows = new Map<number, Run>();
  for (const at of pixels) {
    const x = at % width;
    const y = Math.floor(at / width);
    const run = rows.get(y);
    if (!run) rows.set(y, { l: x, r: x });
    else {
      run.l = Math.min(run.l, x);
      run.r = Math.max(run.r, x);
    }
  }
  return rows;
}

/** The pieces of the figure below row `top` that reach the ground: feet, shoes or socks, not a hand or tail hanging clear of it. */
export function standingBelow(anatomy: Anatomy, top: number): Component[] {
  const { figure } = anatomy;
  const { width, height } = figure.mask;
  const band = blankMask(width, height);
  for (let y = Math.max(figure.top, top); y < figure.ground; y++) {
    for (let x = figure.box.x; x < figure.box.x + figure.box.w; x++) {
      const at = y * width + x;
      if (figure.mask.data[at]) band.data[at] = 1;
    }
  }
  return components(band).pieces.filter((p) => p.box.y + p.box.h >= figure.ground - GROUND_REACH);
}

/**
 * The figure below row `top` that stands on the ground, split into the two feet of a front or back view: at the gap between them, at
 * the deepest notch in their outline where they touch (over the band's lower rows, below the ankles), or, where they join with no notch, under the gap between the legs. Feet that cannot be told apart are one.
 */
export function splitFeet(anatomy: Anatomy, top: number): FootSplit {
  const { figure } = anatomy;
  const { width } = figure.mask;
  const pixels = standingBelow(anatomy, top).flatMap((p) => Array.from(p.pixels));
  if (pixels.length === 0) return { feet: [], how: "one" };
  let l = Infinity;
  let r = -Infinity;
  for (const at of pixels) {
    l = Math.min(l, at % width);
    r = Math.max(r, at % width);
  }
  const counts = new Map<number, number>();
  const countFrom = top + NOTCH_FROM * (figure.ground - top);
  for (const at of pixels) if (Math.floor(at / width) >= countFrom) counts.set(at % width, (counts.get(at % width) ?? 0) + 1);
  const { divider, how } = dividerOf(counts, l, r, legsSplit(anatomy));
  if (divider === undefined) {
    const side = (l + r) / 2 < figure.axis ? ("image-left" as const) : ("image-right" as const);
    return { feet: [{ side, pixels: Int32Array.from(pixels), rows: rowsOf(pixels, width) }], how };
  }
  const left = pixels.filter((at) => at % width < divider);
  const right = pixels.filter((at) => at % width > divider || (how !== "notch" && how !== "gap" && at % width === divider));
  const feet: FootPixels[] = [];
  if (left.length) feet.push({ side: "image-left", pixels: Int32Array.from(left), rows: rowsOf(left, width) });
  if (right.length) feet.push({ side: "image-right", pixels: Int32Array.from(right), rows: rowsOf(right, width) });
  return { feet, divider, how };
}
