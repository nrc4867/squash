import type { Box } from "../core/box.ts";
import { components, type Mask } from "../core/mask.ts";
import type { Figure } from "./figure.ts";

/** Pieces outside the core smaller than this share of the figure's pixels are edge crumbs. */
const CRUMB = 0.002;
/** A tail is at least this share of the figure's pixels. */
const TAIL_SHARE = 0.01;
/** An arm starts within this many head heights below the armpit or shoulder. */
const ARM_REACH = 0.35;
/** An ear ends within this many head heights below the crown. */
const EAR_REACH = 0.6;

export type PieceKind = "ear" | "arm" | "tail" | "other";

/** A part of the figure outside its body core. */
export interface Piece {
  kind: PieceKind;
  box: Box;
  area: number;
  /** -1 on the image's left of the axis, +1 on its right. */
  side: -1 | 1;
  /** Its pixel indices in the sheet. */
  pixels: Int32Array;
  /** The point farthest from where it joins the body: an ear's top, an arm's lowest point (the hand tip), a tail's far end. */
  tip: { x: number; y: number };
}

export interface PieceRows {
  crown: number;
  armsFrom: number;
  headLength: number;
}

function centroidX(pixels: Int32Array, width: number): number {
  let sum = 0;
  for (const at of pixels) sum += at % width;
  return sum / Math.max(1, pixels.length);
}

function extremeRow(pixels: Int32Array, width: number, lowest: boolean): { x: number; y: number } {
  let row = lowest ? -1 : Infinity;
  for (const at of pixels) {
    const y = Math.floor(at / width);
    row = lowest ? Math.max(row, y) : Math.min(row, y);
  }
  const xs = [...pixels].filter((at) => Math.floor(at / width) === row).map((at) => at % width);
  return { x: Math.round(xs.reduce((a, b) => a + b, 0) / Math.max(1, xs.length)), y: row };
}

/** The farthest pixel from the point where a piece touches the body, for a tail. */
export function farthestFrom(pixels: Int32Array, width: number, from: { x: number; y: number }): { x: number; y: number } {
  let best = { x: from.x, y: from.y };
  let bestDistance = -1;
  for (const at of pixels) {
    const x = at % width;
    const y = Math.floor(at / width);
    const d = (x - from.x) ** 2 + (y - from.y) ** 2;
    if (d > bestDistance) {
      bestDistance = d;
      best = { x, y };
    }
  }
  return best;
}

/** The pixel of a piece nearest the core: where it joins the body. */
export function joinPoint(pixels: Int32Array, width: number, core: Mask): { x: number; y: number } {
  for (const at of pixels) {
    if (core.data[at - 1] || core.data[at + 1] || core.data[at - width] || core.data[at + width]) return { x: at % width, y: Math.floor(at / width) };
  }
  const first = pixels[0];
  return { x: first % width, y: Math.floor(first / width) };
}

/** Sorts the figure's pixels outside its core into ears, arms, a tail and other pieces. */
export function classifyPieces(figure: Figure, outside: Mask, core: Mask, rows: PieceRows): Piece[] {
  const width = outside.width;
  const figureArea = figure.rows.reduce((sum, runs) => sum + runs.reduce((s, r) => s + r.r - r.l + 1, 0), 0);
  const found = components(outside).pieces.filter((p) => p.area >= CRUMB * figureArea);
  const pieces: Piece[] = found.map((p) => {
    const side: -1 | 1 = centroidX(p.pixels, width) < figure.axis ? -1 : 1;
    const bottom = p.box.y + p.box.h;
    if (p.box.y < rows.crown && bottom <= rows.crown + EAR_REACH * rows.headLength) {
      return { kind: "ear", box: p.box, area: p.area, side, pixels: p.pixels, tip: extremeRow(p.pixels, width, false) };
    }
    if (p.box.y <= rows.armsFrom + ARM_REACH * rows.headLength && bottom > rows.armsFrom && p.box.y >= rows.crown) {
      return { kind: "arm", box: p.box, area: p.area, side, pixels: p.pixels, tip: extremeRow(p.pixels, width, true) };
    }
    return { kind: "other", box: p.box, area: p.area, side, pixels: p.pixels, tip: extremeRow(p.pixels, width, true) };
  });
  const tail = pieces
    .filter((p) => p.kind === "other" && p.area >= TAIL_SHARE * figureArea && p.box.y >= rows.armsFrom)
    .sort((a, b) => b.area - a.area)[0];
  if (tail) {
    tail.kind = "tail";
    tail.tip = farthestFrom(tail.pixels, width, joinPoint(tail.pixels, width, core));
  }
  return pieces;
}
