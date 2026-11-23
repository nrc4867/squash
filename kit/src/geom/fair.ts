/** A place on a plane curve. */
export type Place = readonly [number, number];

/** Cumulative arc length at each place of a run. */
export function arcLengths(run: readonly Place[]): number[] {
  const out = [0];
  for (let i = 1; i < run.length; i++) out.push(out[i - 1] + Math.hypot(run[i][0] - run[i - 1][0], run[i][1] - run[i - 1][1]));
  return out;
}

/** The place at arc length `s` along a run whose arc lengths are `lengths`. */
function placeAt(run: readonly Place[], lengths: readonly number[], s: number): [number, number] {
  if (s <= 0) return [run[0][0], run[0][1]];
  const total = lengths[lengths.length - 1];
  if (s >= total) return [run[run.length - 1][0], run[run.length - 1][1]];
  let lo = 0;
  let hi = lengths.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (lengths[mid] <= s) lo = mid;
    else hi = mid;
  }
  const w = (s - lengths[lo]) / (lengths[hi] - lengths[lo] || 1);
  return [run[lo][0] + (run[hi][0] - run[lo][0]) * w, run[lo][1] + (run[hi][1] - run[lo][1]) * w];
}

/** A run resampled at `count` places evenly spaced along its length, its ends kept. */
export function evenly(run: readonly Place[], count: number): Array<[number, number]> {
  const lengths = arcLengths(run);
  const total = lengths[lengths.length - 1];
  const n = Math.max(2, count);
  return Array.from({ length: n }, (_, i) => placeAt(run, lengths, (total * i) / (n - 1)));
}

/** A run resampled every `step` along its length (at least two places), its ends kept. */
export function resampled(run: readonly Place[], step: number): Array<[number, number]> {
  const total = arcLengths(run).at(-1)!;
  return evenly(run, Math.max(2, Math.round(total / step) + 1));
}

/**
 * A run smoothed by a Gaussian of `sigma` places along it, its ends held: each place the weighted mean of its neighbours over a window
 * kept even on both sides, so a place near an end averages over fewer and the ends stay where they are.
 */
export function gaussianSmoothed(run: readonly Place[], sigma: number): Array<[number, number]> {
  const n = run.length;
  if (sigma <= 0 || n < 3) return run.map(([x, y]) => [x, y]);
  const reach = Math.ceil(3 * sigma);
  const weights = Array.from({ length: reach + 1 }, (_, k) => Math.exp(-(k * k) / (2 * sigma * sigma)));
  return run.map((p, i) => {
    const half = Math.min(reach, i, n - 1 - i);
    let x = 0;
    let y = 0;
    let sum = 0;
    for (let k = -half; k <= half; k++) {
      const w = weights[Math.abs(k)];
      x += run[i + k][0] * w;
      y += run[i + k][1] * w;
      sum += w;
    }
    return half === 0 ? [p[0], p[1]] : [x / sum, y / sum];
  });
}

/** The heading change, in degrees, across each place of an evenly sampled run, measured over `span` places either side. */
export function turning(run: readonly Place[], span: number): number[] {
  return run.map((_, i) => {
    const a = run[Math.max(0, i - span)];
    const b = run[i];
    const c = run[Math.min(run.length - 1, i + span)];
    if (a === b || b === c) return 0;
    const h0 = Math.atan2(b[1] - a[1], b[0] - a[0]);
    const h1 = Math.atan2(c[1] - b[1], c[0] - b[0]);
    let d = h1 - h0;
    while (d > Math.PI) d -= 2 * Math.PI;
    while (d < -Math.PI) d += 2 * Math.PI;
    return (Math.abs(d) * 180) / Math.PI;
  });
}

/** Indices of an evenly sampled run where it turns by more than `degrees` over `span` places either side, one per turn. */
export function sharpTurns(run: readonly Place[], span: number, degrees: number): number[] {
  const turns = turning(run, span);
  const out: number[] = [];
  let i = span;
  while (i < run.length - span) {
    if (turns[i] <= degrees) {
      i++;
      continue;
    }
    let best = i;
    while (i < run.length - span && turns[i] > degrees) {
      if (turns[i] > turns[best]) best = i;
      i++;
    }
    out.push(best);
  }
  return out;
}

/** What a run is faired into: a few places through which a smooth curve runs, and which of them are corners. */
export interface FairRun {
  places: Array<[number, number]>;
  corners: number[];
}

export interface FairOptions {
  /** Spacing of the dense samples the run is smoothed on. */
  step: number;
  /** Gaussian width, in run length, the run is smoothed by between its corners. */
  smoothing: number;
  /** Spacing of the places kept, in run length: each piece between corners keeps places about this far apart. */
  spacing: number;
  /** A turn sharper than this, in degrees over `cornerSpan` of length either side, is kept as a corner. */
  cornerDegrees: number;
  cornerSpan: number;
}

/**
 * A traced run faired into a few evenly spaced places on a smooth curve: sampled densely, split at its sharp turns (an armpit, a crotch),
 * each piece smoothed with its ends held, then each piece kept at places about `spacing` apart, the split places corners.
 */
export function faired(run: readonly Place[], options: FairOptions): FairRun {
  const dense = resampled(run, options.step);
  const span = Math.max(1, Math.round(options.cornerSpan / options.step));
  const splits = sharpTurns(dense, span, options.cornerDegrees);
  const bounds = [0, ...splits, dense.length - 1];
  const places: Array<[number, number]> = [];
  const corners: number[] = [];
  for (let k = 0; k + 1 < bounds.length; k++) {
    const piece = dense.slice(bounds[k], bounds[k + 1] + 1);
    const smooth = gaussianSmoothed(piece, options.smoothing / options.step);
    const length = arcLengths(smooth).at(-1)!;
    const few = evenly(smooth, Math.max(2, Math.round(length / options.spacing) + 1));
    if (k > 0) {
      corners.push(places.length - 1);
      few.shift();
    }
    places.push(...few);
  }
  return { places, corners };
}
