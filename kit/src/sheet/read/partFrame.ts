import { roundTo, type Point } from "../../geom/point.ts";
import { simplifyClosed } from "../core/outline.ts";

/** A recipe outline's pane: Side, Top or Front. */
export type OutlinePane = "side" | "top" | "front";

/** A part's frame in the image: its socket in pixels, and the image directions its outline's +x and +y point. */
export interface ImageFrame {
  origin: { x: number; y: number };
  /** Unit vectors in the image, rows running down. */
  x: { x: number; y: number };
  y: { x: number; y: number };
}

/** A part-shaped loop's few points: at most this many and at least this many when the tolerance is the trace's own. */
export const FEWEST_POINTS = 6;
export const MOST_POINTS = 14;
/** The trace's own tolerance starts at this share of the part's longest side and steps by this factor until the points fit. */
const START_SHARE = 0.02;
const STEP = 1.25;
const SMALLEST_TOLERANCE = 0.25;
const PLACES = 4;

export function unit(x: number, y: number): { x: number; y: number } {
  const length = Math.hypot(x, y) || 1;
  return { x: x / length, y: y / length };
}

/** The perpendicular to `axis` that leans toward `toward`, or the one a quarter turn left of it in the image when `toward` is square to it. */
export function across(axis: { x: number; y: number }, toward?: { x: number; y: number }): { x: number; y: number } {
  const left = { x: axis.y, y: -axis.x };
  if (!toward) return left;
  const dot = left.x * toward.x + left.y * toward.y;
  return Math.abs(dot) < 1e-6 ? left : dot > 0 ? left : { x: -left.x, y: -left.y };
}

/** Pixel places (their centres) in a part's frame, in body units. */
export function inFrame(pixels: readonly Point[], frame: ImageFrame, bodyPerPixel: number): Point[] {
  return pixels.map(([px, py]): Point => {
    const dx = px + 0.5 - frame.origin.x;
    const dy = py + 0.5 - frame.origin.y;
    return [roundTo((dx * frame.x.x + dy * frame.x.y) * bodyPerPixel, PLACES), roundTo((dx * frame.y.x + dy * frame.y.y) * bodyPerPixel, PLACES)];
  });
}

/** Points scaled and moved to fill -1 to 1 on both axes, as a Front section is drawn. */
export function normalized(points: readonly Point[]): Point[] {
  const xs = points.map((p) => p[0]);
  const ys = points.map((p) => p[1]);
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2;
  const cy = (Math.min(...ys) + Math.max(...ys)) / 2;
  const hx = (Math.max(...xs) - Math.min(...xs)) / 2 || 1;
  const hy = (Math.max(...ys) - Math.min(...ys)) / 2 || 1;
  return points.map(([x, y]): Point => [roundTo((x - cx) / hx, PLACES), roundTo((y - cy) / hy, PLACES)]);
}

export function mirrorY(points: readonly Point[]): Point[] {
  return points.map(([x, y]): Point => [x, roundTo(-y, PLACES)]);
}

export function mirrorX(points: readonly Point[]): Point[] {
  return points.map(([x, y]): Point => [roundTo(-x, PLACES), y]);
}

/** The span of the points along x and along y. */
export function extent(points: readonly Point[]): { x: number; y: number } {
  const xs = points.map((p) => p[0]);
  const ys = points.map((p) => p[1]);
  return { x: roundTo(Math.max(...xs) - Math.min(...xs), PLACES), y: roundTo(Math.max(...ys) - Math.min(...ys), PLACES) };
}

/**
 * A closed loop's few points: from a tolerance of `START_SHARE` of its longest side, made coarser until it holds at most `MOST_POINTS`
 * and finer while it holds fewer than `FEWEST_POINTS`. Returns the points and the tolerance in pixels.
 */
export function fewPoints(loop: readonly Point[]): { points: Point[]; tolerance: number } {
  const xs = loop.map((p) => p[0]);
  const ys = loop.map((p) => p[1]);
  const longest = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys), 1);
  let tolerance = START_SHARE * longest;
  let points = simplifyClosed(loop, tolerance);
  while (points.length > MOST_POINTS) {
    tolerance *= STEP;
    points = simplifyClosed(loop, tolerance);
  }
  while (points.length < FEWEST_POINTS && tolerance > SMALLEST_TOLERANCE) {
    const finer = simplifyClosed(loop, tolerance / STEP);
    if (finer.length > MOST_POINTS) break;
    tolerance /= STEP;
    points = finer;
  }
  return { points, tolerance };
}

/** The outline as a line of TypeScript to paste into a part's `outlines`. */
export function outlineLine(pane: OutlinePane, points: readonly Point[]): string {
  return `${pane}: { through: [${points.map(([x, y]) => `[${x}, ${y}]`).join(", ")}], closed: true }`;
}

/** The middle of some pixel places and the unit direction they spread along most. */
export function principalAxis(pixels: readonly Point[]): { centre: { x: number; y: number }; axis: { x: number; y: number } } {
  const n = Math.max(1, pixels.length);
  const mx = pixels.reduce((s, p) => s + p[0], 0) / n;
  const my = pixels.reduce((s, p) => s + p[1], 0) / n;
  let sxx = 0;
  let syy = 0;
  let sxy = 0;
  for (const [x, y] of pixels) {
    sxx += (x - mx) ** 2;
    syy += (y - my) ** 2;
    sxy += (x - mx) * (y - my);
  }
  const angle = 0.5 * Math.atan2(2 * sxy, sxx - syy);
  return { centre: { x: mx + 0.5, y: my + 0.5 }, axis: { x: Math.cos(angle), y: Math.sin(angle) } };
}
