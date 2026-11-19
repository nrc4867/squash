import { radians, type Point } from "../geom/point.ts";
import { rotate } from "../geom/transform.ts";
import type { Shape } from "./shape.ts";

export interface OvalSpec {
  center?: Point;
  /** Half width along x. */
  rx: number;
  /** Half height along y; rx when left out. */
  ry?: number;
  /** Places round it; 8 makes a smooth oval. */
  points?: number;
  /** Degrees it turns counter-clockwise. */
  turn?: number;
  /** Degrees round from +x where the first place sits. */
  start?: number;
}

/** A closed oval through evenly spaced places, counter-clockwise from `start`. */
export function oval(spec: OvalSpec): Shape {
  const [cx, cy] = spec.center ?? [0, 0];
  const ry = spec.ry ?? spec.rx;
  const count = spec.points ?? 8;
  const start = spec.start ?? 0;
  const points: Point[] = Array.from({ length: count }, (_, i) => {
    const a = radians(start + (360 * i) / count);
    return [cx + spec.rx * Math.cos(a), cy + ry * Math.sin(a)];
  });
  return { points: spec.turn ? rotate(points, spec.turn, [cx, cy]) : points, closed: true };
}

export interface ArcSpec {
  center?: Point;
  rx: number;
  ry?: number;
  /** Degrees from +x, counter-clockwise, where it starts and ends. */
  from: number;
  to: number;
  points?: number;
}

/** An open arc of an oval from `from` to `to` degrees, both ends included. */
export function arc(spec: ArcSpec): Shape {
  const [cx, cy] = spec.center ?? [0, 0];
  const ry = spec.ry ?? spec.rx;
  const count = Math.max(2, spec.points ?? 5);
  const points: Point[] = Array.from({ length: count }, (_, i) => {
    const a = radians(spec.from + ((spec.to - spec.from) * i) / (count - 1));
    return [cx + spec.rx * Math.cos(a), cy + ry * Math.sin(a)];
  });
  return { points };
}
