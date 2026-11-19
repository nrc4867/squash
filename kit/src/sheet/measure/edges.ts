import { leastSquares, median } from "../core/solve.ts";
import type { Run } from "./rows.ts";

/** Rows whose edge strays from the fitted line by more than this many robust deviations are something touching the limb. */
const OUTLIER_SPREAD = 3;
/** Edges may always stray this many pixels from the line. */
const SLACK = 3;
/** Median absolute deviation to standard deviation. */
const MAD_SCALE = 1.4826;

/** A straight line through (y, x) samples, fitted twice: the second time without the first fit's outliers. */
function robustLine(points: Array<[number, number]>): { at: (y: number) => number; spread: number } {
  const fit = (pts: Array<[number, number]>) => {
    const [a, b] = leastSquares(
      pts.map(([y]) => [1, y]),
      pts.map(([, x]) => x),
    );
    return (y: number) => a + b * y;
  };
  let line = fit(points);
  const deviation = (pts: Array<[number, number]>, f: (y: number) => number) => median(pts.map(([y, x]) => Math.abs(x - f(y)))) * MAD_SCALE;
  let spread = deviation(points, line);
  const kept = points.filter(([y, x]) => Math.abs(x - line(y)) <= Math.max(SLACK, OUTLIER_SPREAD * spread));
  if (kept.length >= 2 && kept.length < points.length) {
    line = fit(kept);
    spread = deviation(kept, line);
  }
  return { at: line, spread };
}

/**
 * A limb's runs with each edge held near a straight line fitted through it robustly: where a tail, hand or skirt touches the limb and
 * pushes an edge out, the edge is pulled back to the line.
 */
export function holdEdges(runs: Map<number, Run>): Map<number, Run> {
  if (runs.size < 4) return runs;
  const rows = [...runs.keys()];
  const left = robustLine(rows.map((y) => [y, runs.get(y)!.l]));
  const right = robustLine(rows.map((y) => [y, runs.get(y)!.r]));
  const out = new Map<number, Run>();
  for (const y of rows) {
    const run = runs.get(y)!;
    const l = Math.max(run.l, Math.round(left.at(y) - Math.max(SLACK, OUTLIER_SPREAD * left.spread)));
    const r = Math.min(run.r, Math.round(right.at(y) + Math.max(SLACK, OUTLIER_SPREAD * right.spread)));
    out.set(y, r >= l ? { l, r } : run);
  }
  return out;
}
