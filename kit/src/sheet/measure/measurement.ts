import { roundTo } from "../../geom/point.ts";
import type { Box } from "../core/box.ts";
import type { FacingGuess } from "../views/orientation.ts";
import type { Anatomy } from "./anatomy.ts";
import type { LandmarkIssue } from "./sanity.ts";
import { centralRun, runsAt } from "./figure.ts";
import type { Landmark, PixelMark } from "./landmark.ts";
import type { PieceKind } from "./pieces.ts";
import { runWidth, type Run } from "./rows.ts";

/** One row of the silhouette: its outer edges, and the body core's, as offsets from the axis in body units (image left negative). */
export interface ProfileRow {
  /** The row in the image's pixels. */
  y: number;
  /** Height above the ground, body units. */
  height: number;
  left: number;
  right: number;
  /** The core's edges: the head, neck and torso on the axis, or the legs' outer edges below the crotch. */
  coreLeft?: number;
  coreRight?: number;
  /** How many separate stretches the row crosses: 1 through the torso, 2 between the legs, more beside a loose arm. */
  runs: number;
}

export interface MeasuredPiece {
  kind: PieceKind;
  side: "image-left" | "image-right";
  box: Box;
  /** Its tip: an ear's top, a hand's lowest point, a tail's far end. */
  tip: { x: number; y: number; height: number; across: number };
}

export interface Measurement {
  view: { index: number; name: string; box: Box; guess: FacingGuess };
  /** The figure's height in body units, as asked, and what it measures up to. */
  height: number;
  heightTo: HeightTo;
  /** Body units a pixel. */
  bodyPerPixel: number;
  /** One head, crown to chin, in pixels and in body units. */
  headPixels: number;
  head: number;
  /** The figure's height in heads, ground to crown. */
  heads: number;
  /** The upright axis's column, and the ground's row, in pixels. */
  axis: number;
  ground: number;
  landmarks: Record<string, Landmark>;
  /** Every landmark's height above the ground in body units, for `bodyPlan`. */
  plan: Record<string, number>;
  /** The proportions the landmarks break against each other and a standing figure. */
  checks: LandmarkIssue[];
  /** Half widths in body units at the landmarks, from the core: head, neck, chest, waist, hip, thigh, knee, ankle. */
  widths: Record<string, number>;
  pieces: MeasuredPiece[];
  profile: ProfileRow[];
}

const BODY_PLACES = 4;

function landmarkOf(mark: PixelMark, ground: number, crown: number, headPixels: number, scale: number): Landmark {
  return {
    ...mark,
    height: roundTo((ground - mark.y) * scale, BODY_PLACES),
    heads: roundTo((ground - mark.y) / headPixels, 2),
    headsDown: roundTo((mark.y - crown) / headPixels, 2),
  };
}

function coreRun(anatomy: Anatomy, y: number): Run | undefined {
  const { legs } = anatomy;
  if (legs && y > legs.from) {
    const sides = [legs.left[y - legs.from], legs.right[y - legs.from]].filter((r): r is Run => r !== undefined);
    if (sides.length === 0) return undefined;
    return { l: Math.min(...sides.map((r) => r.l)), r: Math.max(...sides.map((r) => r.r)) };
  }
  let l = Infinity;
  let r = -Infinity;
  for (let x = anatomy.figure.box.x; x < anatomy.figure.box.x + anatomy.figure.box.w; x++) {
    if (!anatomy.core.data[y * anatomy.core.width + x]) continue;
    l = Math.min(l, x);
    r = Math.max(r, x);
  }
  return r >= l ? { l, r } : undefined;
}

function legHalfWidth(anatomy: Anatomy, y: number | undefined): number | undefined {
  const legs = anatomy.legs;
  if (!legs || y === undefined) return undefined;
  const sides = [legs.left[y - legs.from], legs.right[y - legs.from]].filter((r): r is Run => r !== undefined);
  return sides.length ? sides.reduce((s, r) => s + runWidth(r), 0) / sides.length / 2 : undefined;
}

/** What `height` measures up to: the crown, the top of the skull (ears, buns and tufts above it left out), or the figure's highest point. */
export type HeightTo = "crown" | "top";

/** A view's anatomy in body units for a figure `height` tall, from the ground to its crown or to its top. */
export function measurementOf(anatomy: Anatomy, height: number, heightTo: HeightTo = "crown"): Measurement {
  const { figure, marks, view } = anatomy;
  const ground = figure.ground;
  const scale = height / Math.max(1, ground - (heightTo === "crown" ? marks.crown.y : figure.top));
  const headPixels = anatomy.headLength;
  const across = (x: number) => roundTo((x - figure.axis) * scale, BODY_PLACES);
  const landmarks = Object.fromEntries(Object.entries(marks).map(([name, mark]) => [name, landmarkOf(mark, ground, marks.crown.y, headPixels, scale)]));
  const plan = Object.fromEntries(Object.entries(landmarks).filter(([name]) => name !== "ground").map(([name, l]) => [name, l.height]));
  const half = (y: number | undefined) => {
    const run = y === undefined ? undefined : centralRun(figure, y);
    return run ? roundTo((runWidth(run) / 2) * scale, BODY_PLACES) : undefined;
  };
  const leg = (y: number | undefined) => {
    const w = legHalfWidth(anatomy, y);
    return w === undefined ? undefined : roundTo(w * scale, BODY_PLACES);
  };
  const widthEntries: Array<[string, number | undefined]> = [
    ["head", roundTo((anatomy.head.headWidth / 2) * scale, BODY_PLACES)],
    ["neck", half(marks.neck?.y)],
    ["chest", half(marks.armpit?.y)],
    ["waist", half(marks.waist?.y)],
    ["hip", half(marks.hip?.y)],
    ["thigh", leg(marks.crotch ? marks.crotch.y + 1 : undefined)],
    ["knee", leg(marks.knee?.y)],
    ["ankle", leg(marks.ankle?.y)],
  ];
  const widths = Object.fromEntries(widthEntries.filter((e): e is [string, number] => e[1] !== undefined));
  const profile: ProfileRow[] = [];
  for (let y = figure.top; y < ground; y++) {
    const runs = runsAt(figure, y);
    if (runs.length === 0) continue;
    const core = coreRun(anatomy, y);
    profile.push({
      y,
      height: roundTo((ground - y) * scale, BODY_PLACES),
      left: across(runs[0].l),
      right: across(runs[runs.length - 1].r + 1),
      ...(core ? { coreLeft: across(core.l), coreRight: across(core.r + 1) } : {}),
      runs: runs.length,
    });
  }
  const pieces = anatomy.pieces.map((p) => ({
    kind: p.kind,
    side: p.side < 0 ? ("image-left" as const) : ("image-right" as const),
    box: p.box,
    tip: { x: p.tip.x, y: p.tip.y, height: roundTo((ground - p.tip.y) * scale, BODY_PLACES), across: across(p.tip.x) },
  }));
  return {
    view: { index: view.index, name: view.name, box: view.box, guess: view.guess },
    height,
    heightTo,
    bodyPerPixel: scale,
    headPixels,
    head: roundTo(headPixels * scale, BODY_PLACES),
    heads: roundTo((ground - marks.crown.y) / headPixels, 2),
    axis: figure.axis,
    ground,
    landmarks,
    plan,
    checks: anatomy.issues,
    widths,
    pieces,
    profile,
  };
}
