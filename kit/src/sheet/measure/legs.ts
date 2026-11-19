import { EDGE_STEP_SHARE, figureHeight, rowWhere, runsAt, type Figure } from "./figure.ts";
import type { PixelMark } from "./landmark.ts";
import { holdEdges } from "./edges.ts";
import { nearestRun, runAt, runWidth, type Run } from "./rows.ts";

/** The foot is the widest leg row in the lowest share of the leg; the ankle the narrowest row above it, below this share. */
const FOOT_BAND = 0.25;
const ANKLE_FROM = 0.55;
/** With no clear knee, it sits this share of the way from the ankle up to the hip. */
const KNEE_SHARE = 0.5;
/** A knee is found where the leg's width strays from the straight line between hip and ankle by this share of its mean, within a band. */
const KNEE_STRAY = 0.08;
const KNEE_BAND = 0.12;
/** Legs touching on the axis on more than this share of their rows read as merged. */
const MERGED_MOST = 0.3;

/** Each leg's run on each row from the crotch down, image left leg first, edges held where something touches the leg. */
export interface LegTracks {
  from: number;
  /** `left[y - from]` and `right[y - from]`, undefined where the leg was not seen. */
  left: Array<Run | undefined>;
  right: Array<Run | undefined>;
}

export interface LegReading {
  tracks: LegTracks;
  knee?: PixelMark;
  ankle?: PixelMark;
  /** Whether the legs mostly touch on the axis, so they were split there and their inner edges are guesses. */
  merged?: boolean;
}

/** A run the axis crosses inside this middle share of it is both legs touching; nearer an edge, it is one leg. */
const SPLIT_MIDDLE: [number, number] = [0.2, 0.8];

/** One leg's run on a row: the nearest run on its side, or its half of a run both legs share across the axis. */
function legRun(runs: readonly Run[], axis: number, side: -1 | 1): { run?: Run; merged: boolean } {
  const across = runAt(runs, axis);
  if (!across) return { run: nearestRun(runs, axis, side), merged: false };
  const share = (axis - across.l) / Math.max(1, runWidth(across));
  if (share >= SPLIT_MIDDLE[0] && share <= SPLIT_MIDDLE[1]) {
    return { run: side < 0 ? { l: across.l, r: axis } : { l: axis, r: across.r }, merged: true };
  }
  const acrossSide = share > SPLIT_MIDDLE[1] ? -1 : 1;
  return { run: acrossSide === side ? across : nearestRun(runs, axis, side), merged: false };
}

/** Runs one leg up from `start` to `stop`, each edge kept within a step of the row below. */
function track(figure: Figure, split: number, side: -1 | 1, start: number, stop: number, startRun: Run): Map<number, Run> {
  const step = Math.max(1, Math.round(EDGE_STEP_SHARE * figureHeight(figure)));
  const out = new Map<number, Run>();
  let previous = startRun;
  out.set(start, startRun);
  for (let y = start - 1; y >= stop; y--) {
    const { run } = legRun(runsAt(figure, y), split, side);
    if (!run) break;
    const clamped = { l: Math.max(run.l, previous.l - step), r: Math.min(run.r, previous.r + step) };
    if (clamped.r < clamped.l) break;
    out.set(y, clamped);
    previous = clamped;
  }
  return out;
}

/** The lowest row in [from, to) within a pixel of the narrowest: where a straight shin meets the foot. */
function lowestNarrowest(from: number, to: number, widths: (i: number) => number): number | undefined {
  const narrowest = rowWhere(from, to, widths);
  if (narrowest === undefined) return undefined;
  let lowest = narrowest;
  for (let i = narrowest; i < to; i++) if (widths(i) <= widths(narrowest) + 1) lowest = i;
  return lowest;
}

function widthsOf(tracks: Array<Run | undefined>): (y: number) => number {
  return (i) => (tracks[i] ? runWidth(tracks[i]!) : Infinity);
}

/** Each leg below the crotch: the ankle as its narrowest row above the foot, and the knee by a stray in width or by proportion. */
export function readLegs(figure: Figure, crotch: number, hip: number, split: number): LegReading {
  const length = figure.ground - crotch;
  let merged = 0;
  const raw = (side: -1 | 1) =>
    Array.from({ length }, (_, i) => {
      const read = legRun(runsAt(figure, crotch + i), split, side);
      if (read.merged) merged++;
      return read.run;
    });
  const rawSides = { left: raw(-1), right: raw(1) };
  const status = merged / Math.max(1, 2 * length) > MERGED_MOST ? ("estimated" as const) : ("found" as const);
  const ankles: number[] = [];
  const tracks: LegTracks = { from: crotch, left: [...rawSides.left], right: [...rawSides.right] };
  for (const [key, side] of [["left", -1], ["right", 1]] as const) {
    const widths = widthsOf(rawSides[key]);
    const foot = rowWhere((1 - FOOT_BAND) * length, length, (k) => (Number.isFinite(widths(k)) ? widths(k) : 0), true);
    const i = foot === undefined ? undefined : lowestNarrowest(Math.round(ANKLE_FROM * length), foot, widths);
    if (i === undefined || widths(i) === Infinity) continue;
    ankles.push(crotch + i);
    const held = holdEdges(track(figure, split, side, crotch + i, crotch, rawSides[key][i]!));
    for (let y = crotch; y < crotch + i; y++) tracks[key][y - crotch] = held.get(y);
  }
  if (ankles.length === 0) return { tracks, merged: status === "estimated" };
  const ankle = Math.round(ankles.reduce((a, b) => a + b, 0) / ankles.length);
  const guess = Math.round(ankle - KNEE_SHARE * (ankle - hip));
  const knee = kneeRow(tracks, crotch, ankle, guess);
  return { tracks, merged: status === "estimated", ankle: { y: ankle, status }, knee: knee !== undefined ? { y: knee, status } : { y: guess, status: "estimated" } };
}

/** The row near `guess` where the legs' mean width strays most from the straight line crotch to ankle, if it strays enough. */
function kneeRow(tracks: LegTracks, crotch: number, ankle: number, guess: number): number | undefined {
  const width = (y: number) => {
    const runs = [tracks.left[y - crotch], tracks.right[y - crotch]].filter((r): r is Run => r !== undefined);
    return runs.length ? runs.reduce((s, r) => s + runWidth(r), 0) / runs.length : NaN;
  };
  const top = width(crotch + 1);
  const bottom = width(ankle);
  if (!Number.isFinite(top) || !Number.isFinite(bottom)) return undefined;
  const line = (y: number) => top + ((bottom - top) * (y - crotch - 1)) / Math.max(1, ankle - crotch - 1);
  const band = Math.round(KNEE_BAND * (ankle - crotch));
  const stray = (y: number) => Math.abs(width(y) - line(y)) || 0;
  const best = rowWhere(guess - band, guess + band, stray, true);
  if (best === undefined) return undefined;
  return stray(best) >= KNEE_STRAY * ((top + bottom) / 2) ? best : undefined;
}
