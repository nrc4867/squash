import type { RecipeJoint, RecipePath, RecipeSocket, RecipeSpread } from "../schema/recipe.ts";
import { roundTo } from "../geom/point.ts";

/** A hand-made socket `socket.<n>` at u and v; mirrored for a pair to sit on; sink 0 seats a child on the surface, `outset` stands it off. */
export function socket(n: number, u: number, v: number, options: Omit<RecipeSocket, "n" | "u" | "v"> = {}): RecipeSocket {
  return { n, u: roundTo(u), v: roundTo(v), ...options };
}

/** A row of `count` sockets `socket.<n>.0` and on, evenly round a part at u from v 0: studs round a belt. */
export function ringSocket(n: number, u: number, count: number, options: Omit<RecipeSocket, "n" | "u" | "v" | "count" | "toV" | "to"> = {}): RecipeSocket {
  return { n, u: roundTo(u), v: 0, count, toV: 1, ...options };
}

/** The area a spread covers: u from `u[0]` to `u[1]` along the part and v from `v[0]` to `v[1]` round it; `v: [0, 1]` runs all round. */
export type SpreadArea = { u: [number, number]; v: [number, number] };

/**
 * A socket showing `spread.count` copies of the one part on it scattered over `area` of this part, each an instance of that part: wool,
 * spines, tufts, scales. `size` and `falloff` size the copies and shrink them toward the area's edges.
 */
export function spreadSocket(n: number, area: SpreadArea, spread: RecipeSpread, options: Omit<RecipeSocket, "n" | "u" | "v" | "to" | "toV" | "spread" | "path" | "count"> = {}): RecipeSocket {
  return { n, u: roundTo(area.u[0]), to: roundTo(area.u[1]), v: roundTo(area.v[0]), toV: roundTo(area.v[1]), spread, ...options };
}

/**
 * A socket showing copies of the one part on it along a path on this part from `from` to `to`, each `[u, v]`, through `path.via`, or
 * between `path.between`'s two sockets: beads, studs, buttons, chain links (`roll: 90`, `sag` to hang).
 */
export function pathSocket(n: number, from: [number, number], to: [number, number], path: RecipePath, options: Omit<RecipeSocket, "n" | "u" | "v" | "to" | "toV" | "spread" | "path" | "count"> = {}): RecipeSocket {
  return { n, u: roundTo(from[0]), v: roundTo(from[1]), to: roundTo(to[0]), toV: roundTo(to[1]), path, ...options };
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
