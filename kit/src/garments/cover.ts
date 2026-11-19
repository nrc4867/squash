import type { RecipeCover } from "../schema/recipe.ts";

/** A cover paints the cells of its part's chart: this many along u. */
export const CHART_ALONG = 64;
/** And this many round v. */
export const CHART_ROUND = 32;
/** The fewest rows or columns a cover keeps; a narrower band is widened to this. */
export const MIN_CELLS = 2;

/** u on the nearest cell edge along the chart, which is where a hem lands; every edge is exact in binary. */
export function snapU(u: number): number {
  return Math.round(u * CHART_ALONG) / CHART_ALONG;
}

/** v on the nearest cell edge round the chart. */
export function snapV(v: number): number {
  return Math.round(v * CHART_ROUND) / CHART_ROUND;
}

/** A cover over `part` (a pair by its left side): u from `from` to `to`, v from `fromV` to `toV`, hems on cell edges. */
export function cover(part: string, range: Omit<RecipeCover, "part"> = {}): RecipeCover {
  const result: RecipeCover = { part };
  if (range.from !== undefined) result.from = snapU(range.from);
  if (range.to !== undefined) result.to = snapU(range.to);
  if (range.fromV !== undefined) result.fromV = snapV(range.fromV);
  if (range.toV !== undefined) result.toV = snapV(range.toV);
  if (range.ease !== undefined) result.ease = range.ease;
  if (range.outlines) result.outlines = range.outlines;
  return result;
}

/** A band round `part` centred at u `at`, at least two cells wide, as a belt, a cuff or a strap. */
export function band(part: string, at: number, width: number, range: Omit<RecipeCover, "part" | "from" | "to"> = {}): RecipeCover {
  const half = Math.max(width, MIN_CELLS / CHART_ALONG) / 2;
  return cover(part, { ...range, from: at - half, to: at + half });
}
