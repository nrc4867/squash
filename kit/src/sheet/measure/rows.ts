import type { Box } from "../core/box.ts";
import type { Mask } from "../core/mask.ts";

/** A row's stretch of figure, from `l` to `r`, both included, in sheet pixels. */
export interface Run {
  l: number;
  r: number;
}

export function runWidth(run: Run): number {
  return run.r - run.l + 1;
}

/** The figure's runs on each row of the box, top row first. */
export function rowRuns(mask: Mask, box: Box): Run[][] {
  const rows: Run[][] = [];
  for (let y = box.y; y < box.y + box.h; y++) {
    const runs: Run[] = [];
    let start = -1;
    for (let x = box.x; x <= box.x + box.w; x++) {
      const on = x < box.x + box.w && mask.data[y * mask.width + x] === 1;
      if (on && start < 0) start = x;
      if (!on && start >= 0) {
        runs.push({ l: start, r: x - 1 });
        start = -1;
      }
    }
    rows.push(runs);
  }
  return rows;
}

/** The run holding column x, if any. */
export function runAt(runs: readonly Run[], x: number): Run | undefined {
  return runs.find((run) => run.l <= x && x <= run.r);
}

/** The run nearest column x on one side of it: wholly left of x for -1, wholly right for +1. */
export function nearestRun(runs: readonly Run[], x: number, side: -1 | 1): Run | undefined {
  const candidates = runs.filter((run) => (side < 0 ? run.r < x : run.l > x));
  if (candidates.length === 0) return undefined;
  return side < 0 ? candidates[candidates.length - 1] : candidates[0];
}

/** The figure's upright axis: the mean column of its pixels between two shares of its height. */
export function axisColumn(mask: Mask, box: Box, from: number, to: number): number {
  let sum = 0;
  let n = 0;
  for (let y = box.y + Math.floor(box.h * from); y < box.y + Math.ceil(box.h * to); y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      if (mask.data[y * mask.width + x] !== 1) continue;
      sum += x;
      n++;
    }
  }
  return n > 0 ? Math.round(sum / n) : box.x + Math.floor(box.w / 2);
}
