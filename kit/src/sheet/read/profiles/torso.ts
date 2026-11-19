import { steps } from "../../../geom/point.ts";
import { centralRun } from "../../measure/figure.ts";
import type { Run } from "../../measure/rows.ts";
import { aboutAxis, leftOnImageRight, r4, rowAt } from "./shared.ts";
import type { Confidence, MeasuredView, PartProfile } from "./types.ts";

/** The torso's run at a row of a view: held below the armpits, and no wider than at the armpits above them, where arms join. */
function torsoRun(view: MeasuredView, y: number): Run | undefined {
  const { anatomy } = view;
  const armpit = anatomy.marks.armpit?.y;
  let l = Infinity;
  let r = -Infinity;
  for (let x = anatomy.figure.box.x; x < anatomy.figure.box.x + anatomy.figure.box.w; x++) {
    if (!anatomy.core.data[y * anatomy.core.width + x]) continue;
    l = Math.min(l, x);
    r = Math.max(r, x);
  }
  const run = r >= l ? { l, r: r + 1 } : undefined;
  if (!run || armpit === undefined || y >= armpit || anatomy.marks.shoulder === undefined || y < anatomy.marks.shoulder.y) return run;
  const below = centralRun(anatomy.figure, armpit);
  return below ? { l: Math.max(run.l, below.l), r: Math.min(run.r, below.r + 1) } : run;
}

/** The torso from the crotch to the neck: left and right from the front view, front and back from the side view. */
export function torsoProfile(front: MeasuredView, side: MeasuredView | undefined, count: number, facing: "left" | "right"): PartProfile | string {
  const plan = front.measurement.plan;
  if (plan.crotch === undefined || plan.neck === undefined) return "torso: no crotch or neck in the front view";
  const heights = steps(plan.crotch, plan.neck, count);
  const scale = front.measurement.bodyPerPixel;
  const runs = heights.map((h) => torsoRun(front, rowAt(front, h)));
  if (runs.some((r) => !r)) return "torso: the front view's torso is missing at a station";
  const about = aboutAxis(runs as Run[]);
  const imageRight = about.plus.map((v) => r4(v * scale));
  const imageLeft = about.minus.map((v) => r4(v * scale));
  const flip = leftOnImageRight(front);
  const notes: string[] = [];
  let confidence: Confidence = "high";
  if (front.anatomy.marks.crotch?.status !== "found") {
    notes.push("the crotch is estimated by proportion: no gap between the legs");
    confidence = "medium";
  }
  if (front.anatomy.marks.armpit === undefined) {
    notes.push("no armpit found: arms against the body widen the upper torso");
    confidence = "low";
  } else {
    notes.push("above the armpits the width is held to the armpits' where the arms join");
  }
  const profiles: PartProfile["profiles"] = { left: flip ? imageRight : imageLeft, right: flip ? imageLeft : imageRight };
  if (side) {
    const sideRuns = heights.map((h) => torsoRun(side, rowAt(side, h)));
    if (sideRuns.every((r) => r)) {
      const depth = aboutAxis(sideRuns as Run[]);
      const s = side.measurement.bodyPerPixel;
      const toward = (facing === "left" ? depth.minus : depth.plus).map((v) => r4(v * s));
      const away = (facing === "left" ? depth.plus : depth.minus).map((v) => r4(v * s));
      profiles.front = toward;
      profiles.back = away;
      notes.push(`side view faces image ${facing}; arms in front of or behind the torso there add to its depth`);
    } else {
      notes.push("the side view's torso is missing at a station; no depths");
    }
  }
  const at = heights.map((h) => r4(h - plan.crotch));
  return { rest: "up", length: r4(plan.neck - plan.crotch), at, heights: heights.map(r4), profiles, confidence, notes };
}

