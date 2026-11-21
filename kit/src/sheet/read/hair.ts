// The hair's outline in one view of a sheet: the region the hair's colours cover, traced, simplified, its tips and valleys found, and
// carried into the head's drawn units as a recipe's hair[].outline view. See docs/hld/parts.md, Hair locks.

import type { RecipeOutlineView } from "../../schema/recipe.ts";
import { roundTo, type Point } from "../../geom/point.ts";
import type { Box } from "../core/box.ts";
import { hexToPixel, labDistance, pixelToHex, toLab, type Lab } from "../core/color.ts";
import { components, dilate, erode, type Mask } from "../core/mask.ts";
import { fillHoles } from "../core/maskOps.ts";
import { simplifyClosed, traceBoundary } from "../core/outline.ts";
import { pixelAt, type Pixel, type Raster } from "../raster/image.ts";

export type HairView = RecipeOutlineView["view"];

export interface HairTraceOptions {
  /** Which view the outline is of. */
  view: HairView;
  /** The region of the sheet the hair lies in, in pixels. */
  box: Box;
  /** The hair's colours; the commonest colour of the region's top rows when left out. */
  colors?: Pixel[];
  /** How far in Lab a pixel may lie from a hair colour and still be hair. */
  tolerance?: number;
  /** The head's origin in the sheet's pixels: the bottom of the chin under the head's middle. */
  origin: [number, number];
  /** The head's drawn units a pixel. */
  unitsPerPixel: number;
  /** How far, in pixels, a simplified outline may stray from the traced one. */
  simplify?: number;
  /** The radius, in pixels, ink lines between locks are closed over. */
  close?: number;
}

export interface HairTrace extends RecipeOutlineView {
  /** The outline in the sheet's pixels, point for point. */
  pixels: Point[];
  /** The colours keyed as hair. */
  colors: string[];
  /** The head's origin in pixels and its drawn units a pixel. */
  origin: [number, number];
  unitsPerPixel: number;
}

/** How far a colour may lie from a hair colour, in Lab, when no tolerance is given. */
const DEFAULT_TOLERANCE = 28;
/** The share of the region's height whose top rows give the hair's colour when none is given. */
const SEED_ROWS = 0.08;
/** The least turn, in degrees, that makes a point a tip, and a notch a valley; and how far either side, as a share of the length round, it is read. */
const TIP_TURN = 50;
const VALLEY_TURN = 35;
const TURN_REACH = 0.02;
/** Holes in the hair smaller than this share of its region are shine and streaks, filled. */
const HOLE_SHARE = 0.02;

/** The hair region of `options.box` in `image`, traced: its outline in the head's drawn units with its tips and valleys. */
export function traceHair(image: Raster, options: HairTraceOptions): HairTrace {
  const { box } = options;
  const keys = (options.colors ?? [seedColor(image, box)]).map(toLab);
  const tolerance = options.tolerance ?? DEFAULT_TOLERANCE;
  let mask = keyed(image, box, keys, tolerance);
  const r = options.close ?? 2;
  if (r > 0) mask = erode(dilate(mask, r), r);
  mask = highest(mask);
  mask = fillHoles(mask, box.w * box.h * HOLE_SHARE);
  const traced = traceBoundary(mask).map(([x, y]) => [x + box.x, y + box.y] as Point);
  if (traced.length < 3) throw new Error("no hair found in the region: give box= round the hair, or colors= of the hair");
  const pixels = simplifyClosed(traced, options.simplify ?? 1.5);
  const [ox, oy] = options.origin;
  const u = options.unitsPerPixel;
  const points: Point[] = pixels.map(([x, y]) => [roundTo((x - ox) * u), roundTo((oy - y) * u)]);
  const { tips, valleys } = outlineFeatures(points);
  return {
    view: options.view,
    points,
    tips,
    valleys,
    pixels,
    colors: (options.colors ?? [seedColor(image, box)]).map(pixelToHex),
    origin: options.origin,
    unitsPerPixel: u,
  };
}

/**
 * The tips and valleys of a closed outline, y up: each point where the outline, read a short way either side, turns at least 50 degrees
 * out of its region (a spike's or a flick's point) or 35 into it (a notch), the sharpest within that reach. The same rule the build uses
 * for an outline given without them.
 */
export function outlineFeatures(points: readonly Point[]): { tips: number[]; valleys: number[] } {
  const n = points.length;
  if (n < 3) return { tips: [], valleys: [] };
  const lengths = [0];
  for (let i = 0; i < n; i++) lengths.push(lengths[i] + dist(points[i], points[(i + 1) % n]));
  const total = lengths[n];
  const reach = total * TURN_REACH;
  let area = 0;
  for (let i = 0; i < n; i++) {
    const [ax, ay] = points[i];
    const [bx, by] = points[(i + 1) % n];
    area += ax * by - bx * ay;
  }
  const orientation = area >= 0 ? 1 : -1;
  const at = (s: number, from: number, direction: number): Point => {
    const wrapped = ((s % total) + total) % total;
    let k = 0;
    while (k < n - 1 && lengths[k + 1] < wrapped) k++;
    const span = lengths[k + 1] - lengths[k];
    const t = span > 0 ? (wrapped - lengths[k]) / span : 0;
    const a = points[k];
    const b = points[(k + 1) % n];
    const p: Point = [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
    return dist(points[from], p) < total * 1e-5 ? points[(((from + direction) % n) + n) % n] : p;
  };
  const turns = points.map((p, i) => {
    const back = at(lengths[i] - reach, i, -1);
    const ahead = at(lengths[i] + reach, i, 1);
    const ax = p[0] - back[0];
    const ay = p[1] - back[1];
    const bx = ahead[0] - p[0];
    const by = ahead[1] - p[1];
    return ((Math.atan2(ax * by - ay * bx, ax * bx + ay * by) * 180) / Math.PI) * orientation;
  });
  const strongest = (i: number, sign: number): boolean => {
    const mine = turns[i] * sign;
    for (let j = 0; j < n; j++) {
      if (j === i) continue;
      const gap = Math.min(Math.abs(lengths[j] - lengths[i]), total - Math.abs(lengths[j] - lengths[i]));
      if (gap > reach) continue;
      const theirs = turns[j] * sign;
      if (theirs > mine || (theirs === mine && j < i)) return false;
    }
    return true;
  };
  const tips: number[] = [];
  const valleys: number[] = [];
  for (let i = 0; i < n; i++) {
    if (turns[i] >= TIP_TURN && strongest(i, 1)) tips.push(i);
    if (-turns[i] >= VALLEY_TURN && strongest(i, -1)) valleys.push(i);
  }
  return { tips, valleys };
}

/** Parses `#rrggbb,#rrggbb` into colours. */
export function parseColors(text: string): Pixel[] {
  return text.split(",").map((s) => s.trim()).filter(Boolean).map(hexToPixel);
}

function dist(a: Point, b: Point): number {
  return Math.hypot(b[0] - a[0], b[1] - a[1]);
}

/** The commonest colour, in coarse bins, of the region's top rows that are not the background's lightest. */
function seedColor(image: Raster, box: Box): Pixel {
  const counts = new Map<number, { n: number; sum: [number, number, number] }>();
  const rows = Math.max(2, Math.round(box.h * SEED_ROWS));
  for (let y = box.y; y < box.y + rows; y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      if (x < 0 || y < 0 || x >= image.width || y >= image.height) continue;
      const p = pixelAt(image, x, y);
      const [l] = toLab(p);
      if (l > 85) continue;
      const key = ((p[0] >> 4) << 8) | ((p[1] >> 4) << 4) | (p[2] >> 4);
      const held = counts.get(key) ?? { n: 0, sum: [0, 0, 0] };
      held.n++;
      held.sum[0] += p[0];
      held.sum[1] += p[1];
      held.sum[2] += p[2];
      counts.set(key, held);
    }
  }
  let best: { n: number; sum: [number, number, number] } | undefined;
  for (const held of counts.values()) if (!best || held.n > best.n) best = held;
  if (!best) throw new Error("the region's top rows hold no colour to key the hair by: give colors=");
  return [Math.round(best.sum[0] / best.n), Math.round(best.sum[1] / best.n), Math.round(best.sum[2] / best.n)];
}

function keyed(image: Raster, box: Box, keys: Lab[], tolerance: number): Mask {
  const width = box.w;
  const height = box.h;
  const data = new Uint8Array(width * height);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const sx = box.x + x;
      const sy = box.y + y;
      if (sx < 0 || sy < 0 || sx >= image.width || sy >= image.height) continue;
      const lab = toLab(pixelAt(image, sx, sy));
      if (keys.some((k) => labDistance(lab, k) <= tolerance)) data[y * width + x] = 1;
    }
  }
  return { width, height, data };
}

/** The piece of [mask] reaching highest, among the larger ones: the hair over the head rather than a brow or a stray patch below it. */
function highest(mask: Mask): Mask {
  const { pieces, labels } = components(mask);
  if (pieces.length === 0) return mask;
  const big = pieces.filter((p) => p.area >= pieces[0].area * 0.25);
  const top = big.reduce((a, b) => (b.box.y < a.box.y ? b : a));
  const data = new Uint8Array(mask.width * mask.height);
  for (let i = 0; i < data.length; i++) if (labels[i] === top.label) data[i] = 1;
  return { width: mask.width, height: mask.height, data };
}
