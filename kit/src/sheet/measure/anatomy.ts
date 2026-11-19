import type { Mask } from "../core/mask.ts";
import type { SheetView } from "../views/sheet.ts";
import { readBody } from "./body.ts";
import { coreMask, outsideCore } from "./core.ts";
import { figureOf, type Figure } from "./figure.ts";
import { readHead, type HeadReading } from "./head.ts";
import type { PixelMark } from "./landmark.ts";
import { readLegs, type LegTracks } from "./legs.ts";
import { classifyPieces, type Piece } from "./pieces.ts";

/** With no neck to close the head, it is taken as this many times the crown-to-widest depth. */
const ROUND_HEAD = 2;

/** One view read as a body: its silhouette, its landmarks in pixels, its core, legs and outside pieces. */
export interface Anatomy {
  view: SheetView;
  figure: Figure;
  head: HeadReading;
  /** Landmarks by name, in the image's pixels. */
  marks: Record<string, PixelMark>;
  /** The crown to the chin in pixels: one head. */
  headLength: number;
  core: Mask;
  legs?: LegTracks;
  pieces: Piece[];
}

function meanMark(points: ReadonlyArray<{ x: number; y: number }>): PixelMark | undefined {
  if (points.length === 0) return undefined;
  return { y: Math.round(points.reduce((s, p) => s + p.y, 0) / points.length), status: "found" };
}

/** Reads a view's landmarks and parts; `given` marks, in the image's pixels, replace what is found and steer what follows. */
export function readAnatomy(view: SheetView, given: Record<string, PixelMark> = {}): Anatomy {
  const figure = figureOf(view.mask, view.box);
  const head = readHead(figure);
  const marks: Record<string, PixelMark> = { top: { y: figure.top, status: "found" }, ground: { y: figure.ground, status: "found" } };
  marks.crown = given.crown ?? head.crown;
  marks.chin = given.chin ?? head.chin ?? { y: marks.crown.y + ROUND_HEAD * (head.widestRow - head.crown.y), status: "estimated" };
  const neck = given.neck ?? head.neck;
  if (neck) marks.neck = neck;
  const headLength = Math.max(1, marks.chin.y - marks.crown.y);
  const body = readBody(figure, (marks.neck ?? marks.chin).y, headLength);
  for (const key of ["shoulder", "armpit", "waist", "hip", "crotch"] as const) {
    const mark = given[key] ?? body[key];
    if (mark) marks[key] = mark;
  }
  const legs = marks.crotch ? readLegs(figure, marks.crotch.y, (marks.hip ?? marks.crotch).y, body.legSplit ?? figure.axis) : undefined;
  for (const key of ["knee", "ankle"] as const) {
    const mark = given[key] ?? legs?.[key];
    if (mark) marks[key] = mark;
  }
  const core = coreMask(figure, marks.crown.y, marks.armpit?.y, marks.crotch?.y, legs?.tracks);
  const armsFrom = (marks.armpit ?? marks.shoulder ?? marks.neck ?? marks.chin).y;
  const pieces = classifyPieces(figure, outsideCore(figure, core), core, { crown: marks.crown.y, armsFrom, headLength });
  const earTip = given.earTip ?? meanMark(pieces.some((p) => p.kind === "ear") ? pieces.filter((p) => p.kind === "ear").map((p) => p.tip) : head.earTips);
  if (earTip) marks.earTip = earTip;
  const handTip = given.handTip ?? meanMark(pieces.filter((p) => p.kind === "arm").map((p) => p.tip));
  if (handTip) marks.handTip = handTip;
  const tail = pieces.find((p) => p.kind === "tail");
  const tailTop = given.tailTop ?? (tail ? { y: tail.box.y, status: "found" as const } : undefined);
  const tailBottom = given.tailBottom ?? (tail ? { y: tail.box.y + tail.box.h - 1, status: "found" as const } : undefined);
  if (tailTop) marks.tailTop = tailTop;
  if (tailBottom) marks.tailBottom = tailBottom;
  for (const [name, mark] of Object.entries(given)) marks[name] = mark;
  return { view, figure, head, marks, headLength: Math.max(1, marks.chin.y - marks.crown.y), core, legs: legs?.tracks, pieces };
}
