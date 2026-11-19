/** A place in a drawing: [x, y]. In a part's outline x runs along the part from its socket end and y across it. */
export type Point = [number, number];

/** Decimal places the kit writes; finer than any outline needs and short enough to read. */
export const PLACES = 4;

export function roundTo(value: number, places: number = PLACES): number {
  const factor = 10 ** places;
  const rounded = Math.round(value * factor) / factor;
  return Object.is(rounded, -0) ? 0 : rounded;
}

export function roundPoints(points: readonly Point[], places: number = PLACES): Point[] {
  return points.map(([x, y]) => [roundTo(x, places), roundTo(y, places)]);
}

export function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

/** Evenly spaced values from `from` to `to`, both included. */
export function steps(from: number, to: number, count: number): number[] {
  if (count < 2) return [from];
  return Array.from({ length: count }, (_, i) => lerp(from, to, i / (count - 1)));
}

export function radians(degrees: number): number {
  return (degrees * Math.PI) / 180;
}
