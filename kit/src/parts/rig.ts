import type { RecipeJoint, RecipeSocket } from "../schema/recipe.ts";
import { roundTo } from "../geom/point.ts";

/** A hand-made socket `socket.<n>` at u and v; mirrored for a pair to sit on; sink 0 seats a child on the surface. */
export function socket(n: number, u: number, v: number, options: Omit<RecipeSocket, "n" | "u" | "v"> = {}): RecipeSocket {
  return { n, u: roundTo(u), v: roundTo(v), ...options };
}

/** A hand-made joint `joint.<n>` at u, posed by that name. */
export function joint(n: number, u: number, options: Omit<RecipeJoint, "n" | "u"> = {}): RecipeJoint {
  return { n, u: roundTo(u), ...options };
}

/** The name a part's `socket` field gives a hand-made socket. */
export function socketName(n: number): string {
  return `socket.${n}`;
}

/** The name a pose gives a hand-made joint. */
export function jointName(n: number): string {
  return `joint.${n}`;
}
