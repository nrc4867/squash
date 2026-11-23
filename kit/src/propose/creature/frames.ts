import { add, cross, dot, length, scale, turned, unit, type V3 } from "../../geom/vec3.ts";
import type { PlanPart, Rest } from "./plan.ts";

/** Body directions by name, x forward, y up, z left. */
export const DIRECTION: Record<string, V3> = {
  forward: [1, 0, 0],
  back: [-1, 0, 0],
  up: [0, 1, 0],
  down: [0, -1, 0],
  left: [0, 0, 1],
  right: [0, 0, -1],
};

const OPPOSITE: Record<string, string> = { forward: "back", back: "forward", up: "down", down: "up", left: "right", right: "left" };

/** Where v 0 and v 0.25 face round a part resting each way, and its Side's +y (frames.md). */
const REST_FRAME: Record<Rest, [string, string, string]> = {
  forward: ["up", "left", "up"],
  back: ["up", "right", "up"],
  up: ["forward", "right", "forward"],
  down: ["forward", "left", "forward"],
  out: ["up", "back", "up"],
};

const REST_ALONG: Record<Rest, string> = { forward: "forward", back: "back", up: "up", down: "down", out: "left" };

/** A part's frame at rest: the way it runs and the named directions round it, turned by its outward and lean. */
export interface PartFrame {
  along: V3;
  round: Array<{ name: string; dir: V3 }>;
}

/** A direction's name as a profile names it: a pair's left and right are its outer and inner, a front is its forward. */
function profileName(kind: PlanPart["kind"], direction: string): string {
  if (direction === "forward") return "front";
  if (direction === "left") return kind === "limb" ? "outer" : "left";
  if (direction === "right") return kind === "limb" ? "inner" : "right";
  return direction;
}

/** A part's rest frame as the engine builds its left side: its rest turned toward its left by `outward` and toward Side's +y by `lean`. */
export function partFrame(part: Pick<PlanPart, "rest" | "outward" | "lean" | "kind">): PartFrame {
  const [v0Name, v25Name, upName] = REST_FRAME[part.rest];
  const restAlong = DIRECTION[REST_ALONG[part.rest]];
  const out = DIRECTION.left;
  let along = restAlong;
  if (part.outward && Math.abs(dot(restAlong, out)) < 0.99) along = add(along, scale(out, Math.tan((part.outward * Math.PI) / 180)));
  if (part.lean) along = add(along, scale(DIRECTION[upName], Math.tan((part.lean * Math.PI) / 180)));
  along = unit(along);
  const axis = cross(restAlong, along);
  const degrees = (Math.acos(Math.max(-1, Math.min(1, dot(restAlong, along)))) * 180) / Math.PI;
  const turn = (d: V3): V3 => (length(axis) < 1e-9 ? d : turned(d, unit(axis), degrees));
  const v0 = turn(DIRECTION[v0Name]);
  const v25 = turn(DIRECTION[v25Name]);
  return {
    along,
    round: [
      { name: profileName(part.kind, v0Name), dir: v0 },
      { name: profileName(part.kind, v25Name), dir: v25 },
      { name: profileName(part.kind, OPPOSITE[v0Name]), dir: scale(v0, -1) },
      { name: profileName(part.kind, OPPOSITE[v25Name]), dir: scale(v25, -1) },
    ],
  };
}

/** The profile a part's radius toward `name` is read from: a trunk's right mirrors its left. */
export function profileFor(part: Pick<PlanPart, "kind" | "radii">, name: string): string {
  if (name in part.radii) return name;
  if (part.kind === "trunk" && name === "right") return "left";
  if (name === "inner") return "outer";
  if (name === "outer") return "inner";
  return name;
}
