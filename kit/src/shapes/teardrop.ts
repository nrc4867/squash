import type { Point } from "../geom/point.ts";
import type { Shape } from "./shape.ts";

export interface DropSpec {
  /** From the round end (x 0) to the point. */
  length: number;
  /** Half width at the widest. */
  width: number;
  /** Where along it the widest is, a share of the length. */
  widest?: number;
}

/** A closed teardrop: round at x 0, pointed at x `length` (a corner), as a tear, a spot or a pad. */
export function teardrop(spec: DropSpec): Shape {
  const L = spec.length;
  const w = spec.width;
  const at = (spec.widest ?? 0.35) * L;
  const points: Point[] = [
    [0, 0],
    [at * 0.35, w * 0.8],
    [at, w],
    [L * 0.7, w * 0.55],
    [L, 0],
    [L * 0.7, -w * 0.55],
    [at, -w],
    [at * 0.35, -w * 0.8],
  ];
  return { points, corners: [4], closed: true };
}

export interface LeafSpec extends DropSpec {
  /** Half width at the socket end, where the part stays open; the widest half width when left out. */
  base?: number;
}

/**
 * An open leaf outline from the socket end to a pointed tip, as an ear's, a fin's or a petal's Top: out along +y from
 * [0, base], through the widest, to the tip [length, 0] (a corner) and back along -y.
 */
export function leaf(spec: LeafSpec): Shape {
  const L = spec.length;
  const w = spec.width;
  const base = spec.base ?? w;
  const at = (spec.widest ?? 0.4) * L;
  const half: Point[] = [
    [0, base],
    [at, w],
    [L * 0.75, w * 0.5],
  ];
  const points: Point[] = [...half, [L, 0], ...half.map(([x, y]): Point => [x, -y]).reverse()];
  return { points, corners: [half.length] };
}
