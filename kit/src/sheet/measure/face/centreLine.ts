import type { Box } from "../../core/box.ts";
import { channelDistance } from "../../core/color.ts";
import { blankMask, components, dilate, erode, type Mask } from "../../core/mask.ts";
import type { Pixel } from "../../raster/image.ts";
import { fillHoles, maskFromIndices } from "../../core/maskOps.ts";
import { dominantColor } from "./colors.ts";
import type { FaceBase } from "./region.ts";

/** The nose and mouth's middles lie within this share of the head's width of the face's middle line; in a turned head anywhere between the eyes' outer edges. */
const ON_AXIS = 0.16;
/** Marks are looked for this share of the head's width either side of the middle line, so a line wider than the nose band (a smile) is whole. */
const SEARCH_HALF = 0.32;
/** A pixel this far from the skin in the largest channel is a mark: a shade, a dot, a line. */
const MARK = 20;
/** Marks start this share of the head's length below the eyes' middle; what lies inside an eye's box is not a mark. */
const BELOW_MIDDLE = 0.03;
/** A mark is at least this share of the face's area and never under this many pixels; gaps this share of the head's length wide are closed. */
const MARK_AREA = 0.0003;
const MARK_PIXELS = 6;
const CLOSE_SHARE = 0.012;
/** A patch is a muzzle when it spans this share of the head's width, is this share of its length tall and no darker than this many levels under the skin (an open mouth is darker). */
const MUZZLE_DARKER = 60;
const MUZZLE_WIDTH = 0.18;
const MUZZLE_HEIGHT = 0.07;
/** A muzzle fills at least this share of its box: a line or a curve does not. */
const MUZZLE_FILL = 0.4;
/** A lone mark at least this share of the head's width across is a mouth; the nose is at least this share of the head's length above the mouth. */
const MOUTH_WIDTH = 0.12;
const NOSE_ABOVE_MOUTH = 0.03;
/** A muzzle's outline, this share of the head's length wide, is not looked into. */
const MUZZLE_EDGE = 0.012;

export interface FaceMark {
  box: Box;
  cy: number;
  area: number;
  /** The mark's pixels, as indices of the window. */
  pixels: Int32Array;
}

export interface CentreMarks {
  nose?: FaceMark;
  mouth?: FaceMark;
  muzzle?: Box;
  noseWhy?: string;
  mouthWhy?: string;
  /** More than one mark competed for the nose, or the mouth, so the pick is a guess. */
  crowded: boolean;
}

/** Whether a pixel of the piece has a neighbour outside the face: a thin piece that does is an outline's crescent, not a mark. */
function touchesEdge(face: Mask, pixels: Int32Array): boolean {
  const w = face.width;
  for (const at of pixels) {
    const x = at % w;
    if (x === 0 || x === w - 1 || at < w || at >= face.data.length - w) return true;
    if (!face.data[at - 1] || !face.data[at + 1] || !face.data[at - w] || !face.data[at + w]) return true;
  }
  return false;
}

function marksIn(base: FaceBase, strip: Box, reference: Pixel, skip: readonly Box[], within?: Mask): FaceMark[] {
  const { win, face, hair, length } = base;
  const mask = blankMask(win.w, win.h);
  let faceArea = 0;
  for (const v of face.data) faceArea += v;
  for (let y = strip.y; y < strip.y + strip.h; y++) {
    for (let x = strip.x; x < strip.x + strip.w; x++) {
      const at = y * win.w + x;
      if (!face.data[at] || hair.data[at] || (within && !within.data[at]) || skip.some((e) => x >= e.x && x < e.x + e.w && y >= e.y && y < e.y + e.h)) continue;
      if (channelDistance(base.pixel(x, y), reference) > MARK) mask.data[at] = 1;
    }
  }
  const close = Math.max(1, Math.round(CLOSE_SHARE * length));
  const closed = erode(dilate(mask, close), close);
  return components(closed)
    .pieces.filter((p) => p.area >= Math.max(MARK_PIXELS, MARK_AREA * faceArea) && (p.box.h >= MUZZLE_HEIGHT * length || !touchesEdge(face, p.pixels)))
    .map((p) => ({ box: p.box, cy: p.box.y + p.box.h / 2, area: p.area, pixels: p.pixels }));
}

function brightnessOf(base: FaceBase, mark: FaceMark): number {
  let total = 0;
  for (const at of mark.pixels) {
    const p = base.pixel(at % base.win.w, (at / base.win.w) | 0);
    total += (p[0] + p[1] + p[2]) / 3;
  }
  return total / mark.pixels.length;
}

function muzzleOf(base: FaceBase, found: FaceMark[]): FaceMark | undefined {
  const skin = (base.skinColor[0] + base.skinColor[1] + base.skinColor[2]) / 3;
  return found.find(
    (m) => m.box.w >= MUZZLE_WIDTH * base.headWidth && m.box.h >= MUZZLE_HEIGHT * base.length && m.area >= MUZZLE_FILL * m.box.w * m.box.h && brightnessOf(base, m) >= skin - MUZZLE_DARKER,
  );
}

/**
 * Picks the mouth, the lowest mark, and the nose, the highest mark above it; a single mark is a mouth when it is wide, else a nose (a
 * dot). More than two marks leave the pick a guess.
 */
function pick(base: FaceBase, found: FaceMark[]): { nose?: FaceMark; mouth?: FaceMark; crowded: boolean } {
  if (found.length === 0) return { crowded: false };
  const down = [...found].sort((a, b) => a.cy - b.cy);
  if (down.length === 1) return down[0].box.w >= MOUTH_WIDTH * base.headWidth ? { mouth: down[0], crowded: false } : { nose: down[0], crowded: false };
  const mouth = down[down.length - 1];
  const above = down.filter((m) => m.cy < mouth.cy - NOSE_ABOVE_MOUTH * base.length);
  return { nose: above[0], mouth, crowded: down.length > 2 };
}

/**
 * The nose and mouth: small marks on the face's middle line below the eyes. A nose is a dot or a small shade, a mouth a line or a small
 * shape. A patch of another colour spanning the line (a muzzle) is looked into as a face of its own: its marks are measured against its colour.
 */
export function readCentreMarks(base: FaceBase, axis: number, eyes: readonly Box[], turned: boolean): CentreMarks {
  const { win, length, headWidth } = base;
  if (eyes.length === 0) return { noseWhy: "the eyes are not read, so the nose is not looked for", mouthWhy: "the eyes are not read, so the mouth is not looked for", crowded: false };
  const outer = turned ? { from: Math.min(...eyes.map((e) => e.x)), to: Math.max(...eyes.map((e) => e.x + e.w)) } : undefined;
  const centreFrom = outer ? outer.from : axis - ON_AXIS * headWidth;
  const centreTo = outer ? outer.to : axis + ON_AXIS * headWidth;
  const x0 = Math.max(0, Math.round(outer ? outer.from - ON_AXIS * headWidth : axis - SEARCH_HALF * headWidth));
  const x1 = Math.min(win.w, Math.round(outer ? outer.to + ON_AXIS * headWidth : axis + SEARCH_HALF * headWidth));
  const middle = eyes.reduce((t, e) => t + e.y + e.h / 2, 0) / eyes.length;
  const y0 = Math.max(0, Math.round(middle + BELOW_MIDDLE * length));
  const strip: Box = { x: x0, y: y0, w: x1 - x0, h: win.h - y0 };
  if (strip.h < 4 || strip.w < 4) return { noseWhy: "no room below the eyes", mouthWhy: "no room below the eyes", crowded: false };
  let found = marksIn(base, strip, base.skinColor, eyes);
  let muzzle: Box | undefined;
  const patch = muzzleOf(base, found);
  if (patch) {
    muzzle = patch.box;
    const patchMask = fillHoles(maskFromIndices(win.w, win.h, patch.pixels));
    const inside = erode(patchMask, Math.max(2, Math.round(MUZZLE_EDGE * length)));
    const colours: Pixel[] = [];
    for (let at = 0; at < inside.data.length; at++) if (inside.data[at]) colours.push(base.pixel(at % win.w, (at / win.w) | 0));
    const own = dominantColor(colours);
    found = own ? marksIn(base, patch.box, own, eyes, inside) : found.filter((m) => m !== patch);
  }
  const central = found.filter((m) => m.box.x + m.box.w / 2 >= centreFrom && m.box.x + m.box.w / 2 <= centreTo);
  const { nose, mouth, crowded } = pick(base, central);
  const none = muzzle ? "the muzzle shows nothing darker than itself" : "no mark stands out from the skin on the middle line";
  return {
    nose,
    mouth,
    muzzle,
    noseWhy: nose ? undefined : found.length === 0 ? `${none} (a nose drawn as a faint shade, or too small to read)` : "the marks on the middle line below the eyes all lie where a mouth would",
    mouthWhy: mouth ? undefined : found.length === 0 ? `${none} (a mouth drawn as a hairline, or hidden)` : "no mark on the middle line lies low enough, or wide enough, to be a mouth",
    crowded,
  };
}
