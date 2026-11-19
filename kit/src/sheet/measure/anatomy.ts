import type { Mask } from "../core/mask.ts";
import type { Raster } from "../raster/image.ts";
import type { SheetView } from "../views/sheet.ts";
import { readBody } from "./body.ts";
import { clothEdges, markOnEdges, type ClothEdge } from "./cloth.ts";
import { coreMask, outsideCore } from "./core.ts";
import { figureOf, type Figure } from "./figure.ts";
import { readHead, type HeadReading } from "./head.ts";
import type { PixelMark } from "./landmark.ts";
import { readLegs, type LegTracks } from "./legs.ts";
import { checkLandmarks, type LandmarkIssue } from "./sanity.ts";
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
  /** The proportions the landmarks break, each landmark it names already marked unsure. */
  issues: LandmarkIssue[];
  /** Rows where the colour across the body changes (hems, waistbands, cuffs), when the image was given. */
  edges: ClothEdge[];
  /** What the view is, or shows, that makes its readings less reliable: a turned figure, a crown raised by tufts. */
  warnings: string[];
}

/** Landmarks the body below the head sets: in a turned view the silhouette's rows are not slices across the body. */
const BELOW_THE_HEAD = ["shoulder", "armpit", "waist", "hip", "crotch", "knee", "ankle"] as const;

function meanMark(points: ReadonlyArray<{ x: number; y: number }>): PixelMark | undefined {
  if (points.length === 0) return undefined;
  return { y: Math.round(points.reduce((s, p) => s + p.y, 0) / points.length), status: "found" };
}

/** Reads a view's landmarks and parts; `given` marks, in the image's pixels, replace what is found and steer what follows. */
export function readAnatomy(view: SheetView, given: Record<string, PixelMark> = {}, image?: Raster): Anatomy {
  const figure = figureOf(view.mask, view.box);
  const head = readHead(figure);
  const marks: Record<string, PixelMark> = { top: { y: figure.top, status: "found" }, ground: { y: figure.ground, status: "found" } };
  marks.crown = given.crown ?? head.crown;
  marks.chin = given.chin ?? head.chin ?? { y: marks.crown.y + ROUND_HEAD * (head.widestRow - head.crown.y), status: "estimated", reason: "no neck narrows the head: placed at twice the crown to the widest row" };
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
  const partedArmpit = marks.armpit?.status === "estimated" ? undefined : marks.armpit;
  const core = coreMask(figure, marks.crown.y, partedArmpit?.y, marks.crotch?.y, legs?.tracks);
  const armsFrom = (partedArmpit ?? marks.shoulder ?? marks.neck ?? marks.chin).y;
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
  const checked = checkLandmarks(marks, headLength);
  const edges = image ? clothEdges(image, core, figure, (marks.neck ?? marks.chin).y) : [];
  const warnings: string[] = [];
  let read = markOnEdges(checked.marks, edges, figure);
  if (view.guess.facing === "three-quarter") {
    const why = `the view is guessed three-quarter (${Math.round(view.guess.confidence * 100)}%): its rows are not slices across the body, so the body's landmarks and widths are unsure; read proportions from a front or side view`;
    warnings.push(why);
    read = Object.fromEntries(
      Object.entries(read).map(([name, mark]) =>
        (BELOW_THE_HEAD as readonly string[]).includes(name) && mark.status === "found" ? [name, { ...mark, status: "unsure" as const, reason: "three-quarter view: the silhouette's rows are not slices across the body" }] : [name, mark],
      ),
    );
  }
  if (head.earTips.length > 0 && head.crown.status === "found" && head.earTips.some((t) => t.y < marks.crown.y - 0.05 * headLength)) {
    warnings.push(`the outline rises above the skull (hair tufts, ears or buns, ${Math.round(((marks.crown.y - Math.min(...head.earTips.map((t) => t.y))) / headLength) * 100)}% of a head): the crown is read as where a round skull would top out, so the head is only as sure as that`);
  }
  return { view, figure, head, marks: read, headLength: Math.max(1, marks.chin.y - marks.crown.y), core, legs: legs?.tracks, pieces, issues: checked.issues, edges, warnings };
}
