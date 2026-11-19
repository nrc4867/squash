import type { Point } from "../../geom/point.ts";
import { components, maskAt, type Mask } from "./mask.ts";

/** Moore neighbourhood, clockwise from west on an image whose rows run down. */
const AROUND: Array<[number, number]> = [
  [-1, 0],
  [-1, -1],
  [0, -1],
  [1, -1],
  [1, 0],
  [1, 1],
  [0, 1],
  [-1, 1],
];

/** The outer boundary of the mask's largest piece, as pixel places in order round it. */
export function traceBoundary(mask: Mask): Point[] {
  const largest = components(mask).pieces[0];
  if (!largest) return [];
  const start = largest.pixels.reduce((best, at) => (at < best ? at : best), Infinity);
  const sx = start % mask.width;
  const sy = Math.floor(start / mask.width);
  const inside = (x: number, y: number) => maskAt(mask, x, y);
  const out: Point[] = [[sx, sy]];
  let x = sx;
  let y = sy;
  let from = 0;
  const limit = 4 * largest.area + 8;
  for (let n = 0; n < limit; n++) {
    let moved = false;
    for (let k = 0; k < 8; k++) {
      const dir = (from + k) % 8;
      const nx = x + AROUND[dir][0];
      const ny = y + AROUND[dir][1];
      if (!inside(nx, ny)) continue;
      x = nx;
      y = ny;
      from = (dir + 6) % 8;
      moved = true;
      break;
    }
    if (!moved || (x === sx && y === sy)) break;
    out.push([x, y]);
  }
  return out;
}

function distanceToSegment(p: Point, a: Point, b: Point): number {
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  const length = dx * dx + dy * dy;
  const t = length === 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length));
  return Math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy);
}

/** Fewer points along an open polyline, none of the dropped ones further than `tolerance` from it (Douglas and Peucker). */
export function simplifyOpen(points: readonly Point[], tolerance: number): Point[] {
  if (points.length < 3) return [...points];
  let worst = 0;
  let at = 0;
  for (let i = 1; i < points.length - 1; i++) {
    const d = distanceToSegment(points[i], points[0], points[points.length - 1]);
    if (d > worst) {
      worst = d;
      at = i;
    }
  }
  if (worst <= tolerance) return [points[0], points[points.length - 1]];
  return [...simplifyOpen(points.slice(0, at + 1), tolerance).slice(0, -1), ...simplifyOpen(points.slice(at), tolerance)];
}

/** Fewer points round a closed loop: split at its two most distant points, each half simplified. */
export function simplifyClosed(points: readonly Point[], tolerance: number): Point[] {
  if (points.length < 4) return [...points];
  let far = 0;
  let farthest = 0;
  points.forEach((p, i) => {
    const d = Math.hypot(p[0] - points[0][0], p[1] - points[0][1]);
    if (d > farthest) {
      farthest = d;
      far = i;
    }
  });
  const first = simplifyOpen(points.slice(0, far + 1), tolerance);
  const second = simplifyOpen([...points.slice(far), points[0]], tolerance);
  return [...first.slice(0, -1), ...second.slice(0, -1)];
}
