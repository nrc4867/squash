import { lerp, type Point } from "../geom/point.ts";
import type { Shape } from "./shape.ts";

export interface TuftSpec {
  /** How many spikes. */
  spikes: number;
  /** From the base to the longest spike's tip. */
  length: number;
  /** Half width at the base. */
  width: number;
  /** How deep the notches between spikes cut back, a share of the length. */
  notch?: number;
  /** How much shorter the outermost spikes are than the middle, a share of the length. */
  fall?: number;
}

/**
 * A tuft as an open outline from the socket end: `spikes` pointed tips across its width with notches between (all
 * corners), as a fur tuft's Top, a crest or a tail tip's flame.
 */
export function tuft(spec: TuftSpec): Shape {
  const notch = spec.notch ?? 0.3;
  const fall = spec.fall ?? 0.25;
  const n = Math.max(1, Math.round(spec.spikes));
  const spread = spec.width * 0.8;
  const reach = (t: number, cut: number) => spec.length * (1 - cut - fall * Math.abs(t - 0.5) * 2);
  const points: Point[] = [[0, spec.width]];
  const corners: number[] = [];
  for (let i = 0; i < n; i++) {
    const t = n === 1 ? 0.5 : i / (n - 1);
    corners.push(points.length);
    points.push([reach(t, 0), lerp(spread, -spread, t)]);
    if (i < n - 1) {
      const between = (i + 0.5) / (n - 1);
      corners.push(points.length);
      points.push([reach(between, notch), lerp(spread, -spread, between)]);
    }
  }
  points.push([0, -spec.width]);
  return { points, corners };
}

export interface LockSpec {
  /** Along the lock from its root. */
  length: number;
  /** Half width at the root. */
  width: number;
  /** How far its middle bends off the axis by the tip, toward Side's +y; the bend grows along it. */
  curl?: number;
  /** Half width at the tip as a share of the root's; 0 ends it in a point. */
  taper?: number;
  /** Stations along each side. */
  stations?: number;
}

/**
 * A fur lock or hair strand as Side and Top shapes from its root: Side bends its middle by `curl` (a part's Side moves
 * its middle; Top sets width only), and both taper to the tip.
 */
export function furLock(spec: LockSpec): { side: Shape; top: Shape } {
  const count = Math.max(3, Math.round(spec.stations ?? 6));
  const taper = spec.taper ?? 0;
  const curl = spec.curl ?? 0;
  const xs = Array.from({ length: count }, (_, i) => (spec.length * 0.92 * i) / (count - 1));
  const half = (x: number) => spec.width * lerp(1, taper, (x / spec.length) ** 1.3);
  const middle = (x: number) => curl * (x / spec.length) ** 2;
  const corners = taper === 0 ? [count] : [];
  const around = (mid: (x: number) => number): Shape => ({
    points: [
      ...xs.map((x): Point => [x, mid(x) + half(x)]),
      [spec.length, mid(spec.length)],
      ...xs.map((x): Point => [x, mid(x) - half(x)]).reverse(),
    ],
    corners,
  });
  return { side: around(middle), top: around(() => 0) };
}
