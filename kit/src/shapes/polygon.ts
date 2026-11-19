import { radians, type Point } from "../geom/point.ts";
import type { Shape } from "./shape.ts";

/** A closed shape straight between its places: every place a corner. */
export function polygon(points: readonly Point[]): Shape {
  return { points: [...points], corners: points.map((_, i) => i), closed: true };
}

/** A regular polygon of `sides` with its places `radius` from `center`, the first at `start` degrees from +x. */
export function regularPolygon(sides: number, radius: number, center: Point = [0, 0], start = 90): Shape {
  const points: Point[] = Array.from({ length: sides }, (_, i) => {
    const a = radians(start + (360 * i) / sides);
    return [center[0] + radius * Math.cos(a), center[1] + radius * Math.sin(a)];
  });
  return polygon(points);
}

/** A rectangle from its lower-left and upper-right places, every place a corner. */
export function rectangle(from: Point, to: Point): Shape {
  return polygon([from, [to[0], from[1]], to, [from[0], to[1]]]);
}
