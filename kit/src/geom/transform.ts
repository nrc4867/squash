import { radians, type Point } from "./point.ts";

export function translate(points: readonly Point[], dx: number, dy: number): Point[] {
  return points.map(([x, y]) => [x + dx, y + dy]);
}

export function scale(points: readonly Point[], sx: number, sy: number = sx): Point[] {
  return points.map(([x, y]) => [x * sx, y * sy]);
}

/** Turns the points by `degrees` counter-clockwise about `about`. */
export function rotate(points: readonly Point[], degrees: number, about: Point = [0, 0]): Point[] {
  const c = Math.cos(radians(degrees));
  const s = Math.sin(radians(degrees));
  return points.map(([x, y]) => {
    const dx = x - about[0];
    const dy = y - about[1];
    return [about[0] + dx * c - dy * s, about[1] + dx * s + dy * c];
  });
}

/** Mirrors across the x axis (y to -y). */
export function flipY(points: readonly Point[]): Point[] {
  return points.map(([x, y]) => [x, -y]);
}

/** Mirrors across the y axis (x to -x): a face place on the character's left to its right. */
export function flipX(points: readonly Point[]): Point[] {
  return points.map(([x, y]) => [-x, y]);
}
