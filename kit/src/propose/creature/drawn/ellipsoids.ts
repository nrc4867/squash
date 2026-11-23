import { dot, sub, type V3 } from "../../../geom/vec3.ts";
import type { BuiltEnd } from "../body.ts";

/** How far from `p` toward unit direction `d` the farthest of the ends' ellipsoids reaches, in head units: 0 where none lies that way. */
export function reachInEnds(ends: readonly BuiltEnd[], p: V3, d: V3): number {
  let best = 0;
  for (const end of ends) {
    const off = sub(p, end.center);
    const q = end.axes.map((a, i) => dot(off, a) / end.radii[i]);
    const e = end.axes.map((a, i) => dot(d, a) / end.radii[i]);
    const ee = e[0] * e[0] + e[1] * e[1] + e[2] * e[2];
    const qe = q[0] * e[0] + q[1] * e[1] + q[2] * e[2];
    const qq = q[0] * q[0] + q[1] * q[1] + q[2] * q[2];
    const disc = qe * qe - ee * (qq - 1);
    if (disc < 0 || ee === 0) continue;
    best = Math.max(best, (-qe + Math.sqrt(disc)) / ee);
  }
  return best;
}
