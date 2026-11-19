import { roundTo } from "../../../geom/point.ts";
import { runsAt } from "../../measure/figure.ts";
import type { Run } from "../../measure/rows.ts";
import type { MeasuredView } from "./types.ts";

const PLACES = 4;
/** The side view's front is the way its feet reach further, read over this lowest share of its height. */
const FOOT_BAND = 0.04;

export const r4 = (v: number): number => roundTo(v, PLACES);

/** Whether the character's left is on the image's right: true for a front or three-quarter view, false for a back. */
export function leftOnImageRight(view: MeasuredView): boolean {
  return view.anatomy.view.guess.facing !== "back";
}

export function rowAt(view: MeasuredView, height: number): number {
  return Math.round(view.measurement.ground - height / view.measurement.bodyPerPixel);
}

/** Signed distances of two edges from a straight axis through the runs' mean middle. */
export function aboutAxis(runs: Run[]): { minus: number[]; plus: number[] } {
  const axis = runs.reduce((s, r) => s + (r.l + r.r) / 2, 0) / runs.length;
  return { minus: runs.map((r) => axis - r.l), plus: runs.map((r) => r.r - axis) };
}

/** Which image side the side view faces: the way its feet reach further from the ankles' middle. */
export function sideFacing(view: MeasuredView): "left" | "right" {
  const { figure } = view.anatomy;
  const from = Math.round(figure.ground - FOOT_BAND * (figure.ground - figure.top));
  const ankle = view.anatomy.marks.ankle?.y ?? from - 1;
  const ankleRuns = runsAt(figure, ankle);
  const middle = ankleRuns.length ? (ankleRuns[0].l + ankleRuns[ankleRuns.length - 1].r) / 2 : figure.axis;
  let reachLeft = 0;
  let reachRight = 0;
  for (let y = from; y < figure.ground; y++) {
    const runs = runsAt(figure, y);
    if (!runs.length) continue;
    reachLeft = Math.max(reachLeft, middle - runs[0].l);
    reachRight = Math.max(reachRight, runs[runs.length - 1].r - middle);
  }
  return reachLeft > reachRight ? "left" : "right";
}

/** Distances of the edges from the straight line through the first and last runs' middles. */
export function aboutLine(runs: Run[]): { minus: number[]; plus: number[] } {
  const first = (runs[0].l + runs[0].r) / 2;
  const last = (runs[runs.length - 1].l + runs[runs.length - 1].r) / 2;
  const mid = (i: number) => first + ((last - first) * i) / Math.max(1, runs.length - 1);
  return { minus: runs.map((r, i) => mid(i) - r.l), plus: runs.map((r, i) => r.r - mid(i)) };
}

