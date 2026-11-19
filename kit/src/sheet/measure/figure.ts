import type { Box } from "../core/box.ts";
import type { Mask } from "../core/mask.ts";
import { axisColumn, runAt, runWidth, rowRuns, type Run } from "./rows.ts";

/** The band of the figure's height, from its top, whose pixels set its upright axis: the head and upper body. */
const AXIS_BAND: [number, number] = [0.1, 0.45];

/**
 * A limb's or the torso's edge moves at most this share of the figure's height from one row to the next; a faster jump is something
 * touching it (a tail, a hand, a skirt) and is cut off.
 */
export const EDGE_STEP_SHARE = 0.0025;

/** One view's silhouette, read row by row about its upright axis. */
export interface Figure {
  mask: Mask;
  box: Box;
  /** The runs on each row, `rows[y - top]`. */
  rows: Run[][];
  /** The column the body is centred on. */
  axis: number;
  /** The figure's first row. */
  top: number;
  /** The row just under its lowest pixel: where it stands. */
  ground: number;
}

export function figureOf(mask: Mask, box: Box): Figure {
  return { mask, box, rows: rowRuns(mask, box), axis: axisColumn(mask, box, AXIS_BAND[0], AXIS_BAND[1]), top: box.y, ground: box.y + box.h };
}

export function runsAt(figure: Figure, y: number): Run[] {
  return figure.rows[y - figure.top] ?? [];
}

/** The run on the axis at row y: the head, neck or torso there. */
export function centralRun(figure: Figure, y: number): Run | undefined {
  return runAt(runsAt(figure, y), figure.axis);
}

export function centralWidth(figure: Figure, y: number): number {
  const run = centralRun(figure, y);
  return run ? runWidth(run) : 0;
}

/** The figure's height in pixels, top to ground. */
export function figureHeight(figure: Figure): number {
  return figure.ground - figure.top;
}

/** The row in [from, to) where `score` is least (or most, with `most`); undefined for an empty range. */
export function rowWhere(from: number, to: number, score: (y: number) => number, most = false): number | undefined {
  let best: number | undefined;
  let bestScore = 0;
  for (let y = Math.max(0, Math.round(from)); y < Math.round(to); y++) {
    const s = score(y);
    if (best === undefined || (most ? s > bestScore : s < bestScore)) {
      best = y;
      bestScore = s;
    }
  }
  return best;
}
