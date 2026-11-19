import type { Box } from "../../core/box.ts";
import { channelDistance } from "../../core/color.ts";
import { blankMask, components, dilate, erode, type Mask } from "../../core/mask.ts";
import { countIn, fillHoles, growWithin, maskFromIndices } from "../../core/maskOps.ts";
import { isEyeWhite } from "./colors.ts";
import type { FaceBase } from "./region.ts";

/** A pixel this far from the skin in the largest channel is a feature: an iris, a lid line, a lash. */
const FEATURE = 45;
/** Gaps this share of the head's length wide inside a feature (a pale white inside its ink line) are closed. */
const CLOSE_SHARE = 0.012;
/** Seeds are the features left once lines thinner than twice this share of the head's length are opened away; each is grown back through the feature by this share, to take its lid line and lashes. */
const SEED_SHARE = 0.018;
const GROW_SHARE = 0.03;
/** Whites are found as pieces that survive opening by this share of the head's length, with the iris inside them (a hole up to this share of the face's area) filled; one overlapping an eye already found by this share of the smaller box is that eye, unless that eye fills less than the least share of its box. */
const WHITE_OPEN_SHARE = 0.02;
const IRIS_SHARE = 0.08;
const OVERLAP = 0.3;
/** A hole inside a feature is filled (the white inside its ink line) when it is at most this share of the face's area. */
const HOLE_SHARE = 0.02;
/** A seed that is not eye-shaped is opened further, up to this share of the head's length, to cut it from what it touches (a muzzle, a brow); each piece is grown back by this share. */
const SPLIT_SHARE = 0.07;
const LASH_SPLIT_SHARE = 0.012;
/** An eye fills at least this share of its box (an ellipse fills 0.78; a blob with a stub of muzzle less). */
const MIN_FILL = 0.6;
/** An eye's seed is at least this share of the face's area. */
const SEED_AREA = 0.0015;
/** An eye's middle lies between these shares of the head's length from the crown. */
const EYE_ROWS: [number, number] = [0.28, 0.8];
/** An eye is at most this many times as wide as it is high (a brow or a mouth is wider), at least this share of the head's width across and at most this share. */
const MAX_ASPECT = 3;
const EYE_WIDTH: [number, number] = [0.07, 0.5];
/** The fringe lies over an eye's top when hair touches this share of its width just above it. */
const COVERED_SHARE = 0.2;

/** Two eyes of a pair: how far apart in height (a share of the head's length) and how far from mirrored about the middle (a share of the head's width), and how alike, by view. */
export interface PairLimits {
  height: number;
  mirror: number;
  /** The narrower eye's width over the wider one's, the least a pair has. */
  alike: number;
  apart: number;
}
export const FRONT_PAIR: PairLimits = { height: 0.1, mirror: 0.2, alike: 0.4, apart: 0.1 };
/** A turned head's far eye is narrower and the pair is off the head's middle. */
export const TURNED_PAIR: PairLimits = { height: 0.12, mirror: 0.4, alike: 0.2, apart: 0.08 };

/** One feature that could be an eye, in the window's pixels. */
export interface EyeCandidate {
  box: Box;
  area: number;
  cx: number;
  cy: number;
  coveredTop: boolean;
  /** The share of its box it fills: low where a stub of something else (a muzzle) came with it. */
  fill: number;
}

export interface EyeSearch {
  candidates: EyeCandidate[];
  /** Everything that stands out from the skin and is not hair inside the face. */
  feature: Mask;
}

/** The features of the face that could be eyes: outlines of lid line, lash and white together, seeded where the feature is thick. */
export function eyeCandidates(base: FaceBase): EyeSearch {
  const { win, length, headWidth, face, hair, skinColor } = base;
  const raw = blankMask(win.w, win.h);
  for (let y = 0; y < win.h; y++) {
    for (let x = 0; x < win.w; x++) {
      const at = y * win.w + x;
      if (!face.data[at] || hair.data[at]) continue;
      const p = base.pixel(x, y);
      if (channelDistance(p, skinColor) > FEATURE || isEyeWhite(p, skinColor)) raw.data[at] = 1;
    }
  }
  const close = Math.max(1, Math.round(CLOSE_SHARE * length));
  const closed = erode(dilate(raw, close), close);
  const faceArea = face.data.reduce((s, v) => s + v, 0);
  const feature = fillHoles(closed, HOLE_SHARE * faceArea);
  for (let i = 0; i < feature.data.length; i++) feature.data[i] &= face.data[i] & (hair.data[i] ^ 1);
  const candidates: EyeCandidate[] = [];
  const baseRadius = Math.max(1, Math.round(SEED_SHARE * length));
  const maxRadius = Math.max(baseRadius, Math.round(SPLIT_SHARE * length));
  const lash = Math.max(2, Math.round(GROW_SHARE * length));
  const accept = (seed: Mask, radius: number) => {
    const grown = growWithin(seed, feature, radius === baseRadius ? lash : Math.max(2, Math.round(LASH_SPLIT_SHARE * length)));
    const box = boxOf(grown);
    if (!box) return;
    const cy = box.y + box.h / 2;
    if (cy < EYE_ROWS[0] * length || cy > EYE_ROWS[1] * length) return;
    if (!eyeShaped(box, headWidth, box.w * box.h)) return;
    candidates.push({ box, area: grown.data.reduce((s, v) => s + v, 0), cx: box.x + box.w / 2, cy, coveredTop: coveredTop(hair, box), fill: grown.data.reduce((s, v) => s + v, 0) / (box.w * box.h) });
  };
  const split = (within: Mask, radius: number) => {
    const opened = dilate(erode(within, radius), radius);
    for (const piece of components(opened).pieces) {
      if (piece.area < SEED_AREA * faceArea) continue;
      const seed = maskFromIndices(win.w, win.h, piece.pixels);
      if (eyeShaped(piece.box, headWidth, piece.area)) accept(seed, radius);
      else if (radius < maxRadius) split(seed, Math.min(maxRadius, radius + Math.max(1, Math.round(0.006 * length))));
    }
  };
  split(feature, baseRadius);
  const taken = candidates.length;
  const whites = blankMask(win.w, win.h);
  for (let at = 0; at < whites.data.length; at++) if (face.data[at] && !hair.data[at] && isEyeWhite(base.pixel(at % win.w, (at / win.w) | 0), skinColor)) whites.data[at] = 1;
  const sclera = fillHoles(dilate(erode(whites, Math.max(1, Math.round(WHITE_OPEN_SHARE * length))), Math.max(1, Math.round(WHITE_OPEN_SHARE * length))), IRIS_SHARE * faceArea);
  for (const piece of components(sclera).pieces) {
    if (piece.area < SEED_AREA * faceArea || !eyeShaped(piece.box, headWidth, piece.area)) continue;
    accept(maskFromIndices(win.w, win.h, piece.pixels), baseRadius);
  }
  const feats = candidates.slice(0, taken);
  const extra = candidates.slice(taken);
  const kept = feats.filter((f) => f.fill >= MIN_FILL || !extra.some((w) => overlap(f.box, w.box) > OVERLAP));
  const added = extra.filter((w) => !kept.some((f) => overlap(f.box, w.box) > OVERLAP));
  candidates.splice(0, candidates.length, ...kept, ...added);
  return { candidates, feature };
}

function eyeShaped(box: Box, headWidth: number, area: number): boolean {
  if (box.w > MAX_ASPECT * box.h || box.h > MAX_ASPECT * box.w) return false;
  if (box.w < EYE_WIDTH[0] * headWidth || box.w > EYE_WIDTH[1] * headWidth) return false;
  return area >= MIN_FILL * box.w * box.h;
}

/** The share of the smaller box that the two share. */
function overlap(a: Box, b: Box): number {
  const w = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
  const h = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
  return w <= 0 || h <= 0 ? 0 : (w * h) / Math.min(a.w * a.h, b.w * b.h);
}

function boxOf(mask: Mask): Box | undefined {
  let x0 = mask.width;
  let y0 = mask.height;
  let x1 = -1;
  let y1 = -1;
  for (let y = 0; y < mask.height; y++) {
    for (let x = 0; x < mask.width; x++) {
      if (!mask.data[y * mask.width + x]) continue;
      x0 = Math.min(x0, x);
      x1 = Math.max(x1, x);
      y0 = Math.min(y0, y);
      y1 = Math.max(y1, y);
    }
  }
  return x1 < 0 ? undefined : { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 };
}

/** Whether hair lies along the top of the box, a few pixels above it and inside its width. */
function coveredTop(hair: Mask, box: Box): boolean {
  const band = Math.max(2, Math.round(0.12 * box.h));
  return countIn(hair, box.x, box.y - band, box.w, 2 * band) >= COVERED_SHARE * box.w * band;
}

/** The best pair of candidates level, mirrored about `axis` and alike within `limits`, the pair of the most area; undefined when none. */
export function bestPair(found: readonly EyeCandidate[], axis: number, length: number, headWidth: number, limits: PairLimits): [EyeCandidate, EyeCandidate] | undefined {
  let best: { pair: [EyeCandidate, EyeCandidate]; score: number } | undefined;
  for (const a of found) {
    for (const b of found) {
      if (a.cx >= b.cx || b.cx - a.cx < limits.apart * headWidth) continue;
      if (Math.abs(a.cy - b.cy) > limits.height * length) continue;
      if (Math.abs((a.cx + b.cx) / 2 - axis) > limits.mirror * headWidth) continue;
      const narrow = Math.min(a.box.w, b.box.w);
      if (narrow < limits.alike * Math.max(a.box.w, b.box.w)) continue;
      const alike = Math.min(a.area, b.area) / Math.max(a.area, b.area);
      const score = (a.area + b.area) * (0.5 + alike);
      if (!best || score > best.score) best = { pair: [a, b], score };
    }
  }
  return best?.pair;
}
