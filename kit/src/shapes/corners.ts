import type { Point } from "../geom/point.ts";
import type { Shape } from "./shape.ts";

/** The longest share of an edge a rounding may take, so two roundings on one edge never cross. */
const MAX_EDGE_SHARE = 0.45;

function toward(from: Point, to: Point, distance: number): Point {
  const dx = to[0] - from[0];
  const dy = to[1] - from[1];
  const length = Math.hypot(dx, dy);
  const t = length === 0 ? 0 : Math.min(MAX_EDGE_SHARE, distance / length);
  return [from[0] + dx * t, from[1] + dy * t];
}

/**
 * Rounds sharp corners: each named corner (every corner when `which` is left out) becomes two places `radius` along its
 * edges, joined by the smooth curve. Other corners stay sharp.
 */
export function roundCorners(drawn: Shape, radius: number, which?: readonly number[]): Shape {
  const corners = new Set(drawn.corners ?? []);
  const rounding = new Set(which ?? [...corners]);
  const points: Point[] = [];
  const kept: number[] = [];
  const n = drawn.points.length;
  drawn.points.forEach((p, i) => {
    const hasPrevious = drawn.closed || i > 0;
    const hasNext = drawn.closed || i < n - 1;
    if (rounding.has(i) && hasPrevious && hasNext) {
      points.push(toward(p, drawn.points[(i - 1 + n) % n], radius), toward(p, drawn.points[(i + 1) % n], radius));
    } else {
      if (corners.has(i)) kept.push(points.length);
      points.push(p);
    }
  });
  return { points, corners: kept, closed: drawn.closed };
}
