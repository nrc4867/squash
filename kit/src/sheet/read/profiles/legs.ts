import { steps } from "../../../geom/point.ts";
import { runsAt } from "../../measure/figure.ts";
import { nearestRun, runAt, type Run } from "../../measure/rows.ts";
import { aboutLine, leftOnImageRight, r4, rowAt } from "./shared.ts";
import type { MeasuredView, PartProfile } from "./types.ts";

function legRuns(view: MeasuredView, heights: number[], side: "left" | "right"): Array<Run | undefined> {
  const legs = view.anatomy.legs;
  return heights.map((h) => {
    const y = rowAt(view, h);
    if (!legs) return undefined;
    const run = (side === "left" ? legs.left : legs.right)[y - legs.from];
    return run ? { l: run.l, r: run.r + 1 } : undefined;
  });
}

function sideLegRuns(view: MeasuredView, heights: number[]): Array<Run | undefined> {
  const { figure } = view.anatomy;
  return heights.map((h) => {
    const runs = runsAt(figure, rowAt(view, h));
    const run = runAt(runs, figure.axis) ?? nearestRun(runs, figure.axis, -1) ?? nearestRun(runs, figure.axis, 1);
    return run ? { l: run.l, r: run.r + 1 } : undefined;
  });
}

/** Each leg from the crotch to the ankle: outer and inner from the front view, front and back from the side view. */
export function legProfiles(front: MeasuredView, side: MeasuredView | undefined, count: number, facing: "left" | "right"): Record<string, PartProfile> | string {
  const plan = front.measurement.plan;
  if (plan.crotch === undefined || plan.ankle === undefined || !front.anatomy.legs) return "legs: no crotch or ankle in the front view";
  const heights = steps(plan.crotch, plan.ankle, count);
  const scale = front.measurement.bodyPerPixel;
  const flip = leftOnImageRight(front);
  const merged = front.anatomy.marks.ankle?.status !== "found";
  const out: Record<string, PartProfile> = {};
  for (const imageSide of ["left", "right"] as const) {
    const runs = legRuns(front, heights, imageSide);
    if (runs.some((r) => !r)) continue;
    const about = aboutLine(runs as Run[]);
    const outer = (imageSide === "left" ? about.minus : about.plus).map((v) => r4(v * scale));
    const inner = (imageSide === "left" ? about.plus : about.minus).map((v) => r4(v * scale));
    const characterSide = flip ? (imageSide === "left" ? "r" : "l") : imageSide === "left" ? "l" : "r";
    const notes = merged ? ["the legs touch along the axis: they are split there, so inner radii are guesses and anything behind them adds to the outer"] : [];
    const profiles: PartProfile["profiles"] = { outer, inner };
    if (side) {
      const sideRuns = sideLegRuns(side, heights);
      if (sideRuns.every((r) => r)) {
        const depth = aboutLine(sideRuns as Run[]);
        const s = side.measurement.bodyPerPixel;
        profiles.front = (facing === "left" ? depth.minus : depth.plus).map((v) => r4(v * s));
        profiles.back = (facing === "left" ? depth.plus : depth.minus).map((v) => r4(v * s));
        notes.push("depths from the side view, where both legs overlap: the nearer leg's outline");
      }
    }
    out[`leg-${characterSide}`] = {
      rest: "down",
      length: r4(plan.crotch - plan.ankle),
      at: heights.map((h) => r4(plan.crotch - h)),
      heights: heights.map(r4),
      profiles,
      confidence: merged ? "low" : side ? "high" : "medium",
      notes,
    };
  }
  return Object.keys(out).length ? out : "legs: the front view's legs could not be followed from the ankles to the crotch";
}

