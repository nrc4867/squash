import type { Point } from "../geom/point.ts";
import type { Shape } from "./shape.ts";

const ON_AXIS = 1e-9;

/**
 * A closed loop from its +y side and its -y side, each listed from x's start to its end. Places on the axis (y 0) at
 * either end are shared, so a loop from [0, 0] round to [L, 0] passes through each once.
 */
export function twoSidedLoop(plus: readonly Point[], minus: readonly Point[]): Shape {
  const points: Point[] = [...plus];
  const back = [...minus].reverse();
  const last = plus[plus.length - 1];
  const first = plus[0];
  if (back.length > 0 && Math.abs(back[0][1]) < ON_AXIS && Math.abs(last[1]) < ON_AXIS) back.shift();
  if (back.length > 0 && Math.abs(back[back.length - 1][1]) < ON_AXIS && Math.abs(first[1]) < ON_AXIS) back.pop();
  points.push(...back);
  return { points, closed: true };
}

/** A closed loop symmetric across the x axis, from its +y half listed from x's start to its end. */
export function mirroredLoop(half: readonly Point[]): Shape {
  return twoSidedLoop(half, half.map(([x, y]) => [x, -y]));
}

/** An open outline running out along +y and back along -y, as a limb's: from its +y side and its -y side, each from x's start. */
export function twoSidedOpen(plus: readonly Point[], minus: readonly Point[], tip: Point | undefined): Shape {
  const points: Point[] = [...plus];
  if (tip) points.push(tip);
  points.push(...[...minus].reverse());
  return { points };
}
