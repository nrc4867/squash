import { centralRun, centralWidth, figureHeight, rowWhere, runsAt, type Figure } from "./figure.ts";
import type { PixelMark } from "./landmark.ts";
import { runWidth } from "./rows.ts";

/** The rows over which the outline's slope is read, as a share of one head. */
const SLOPE_WINDOW = 0.03;
/** The outline begins the shoulder where it moves outward by at least this many pixels a row. */
const SHOULDER_SLOPE = 0.5;
/** The outline has turned into the arm where its slope falls under this share of the shoulder's steepest. */
const SHOULDER_SETTLED = 0.2;
/** The shoulder is searched this many heads below the neck. */
const SHOULDER_SEARCH = 0.9;
/** The width's rise (the second method) is read over this share of the figure's height. */
const RISE_WINDOW = 0.02;
/** ...and has settled where it falls under this share of its steepest. */
const RISE_SETTLED = 0.3;
/** Two readings of one landmark that differ by more than this many heads disagree. */
export const READINGS_AGREE = 0.12;
/** The sides of the body differ if their readings are further apart than this many heads. */
export const SIDES_AGREE = 0.15;
/** An arm parts from the torso where one edge of the central run moves in by this share of its width from one row to the next... */
const ARM_PARTS = 0.1;
/** ...and stays in over this share of a head below. */
const GAP_HOLDS = 0.05;
/** The armpit lies within this share of the way from the shoulder to the crotch. */
export const ARMPIT_REACH = 0.5;
/** With no armpit to read, it sits this share of the way from the shoulder to the crotch. */
export const ARMPIT_SHARE = 0.3;
/** An arm part further than this many heads below the shoulder is not read as parting at the armpit. */
const ARMPIT_MAX_HEADS = 1.1;

/** Arms stand over the shoulders if a row this share of a head above them crosses at least this many separate stretches. */
const RAISED_PROBE = 0.1;
const RAISED_RUNS = 3;

type Side = -1 | 1;
const SIDES: readonly Side[] = [-1, 1];
const SIDE_NAMES = { "-1": "image-left", "1": "image-right" } as const;

interface OutlinePoint {
  x: number;
  y: number;
}

/** The central run's outer edge on a side, growing outward. */
function outward(figure: Figure, side: Side, y: number): number | undefined {
  const run = centralRun(figure, y);
  if (!run) return undefined;
  return side < 0 ? -run.l : run.r;
}

const edgeX = (side: Side, edge: number): number => (side < 0 ? -edge : edge);

/** Where one side's outline turns from the neck, across the shoulder slope, into the arm. */
function shoulderTurn(figure: Figure, side: Side, neck: number, headLength: number): OutlinePoint | undefined {
  const window = Math.max(2, Math.round(SLOPE_WINDOW * headLength));
  const end = Math.min(figure.ground - window - 1, Math.round(neck + SHOULDER_SEARCH * headLength));
  const slope = (y: number): number => {
    const a = outward(figure, side, y);
    const b = outward(figure, side, y + window);
    return a === undefined || b === undefined ? 0 : (b - a) / window;
  };
  let start: number | undefined;
  for (let y = neck; y < end; y++) {
    if (slope(y) >= SHOULDER_SLOPE) {
      start = y;
      break;
    }
  }
  if (start === undefined) return undefined;
  const peak = rowWhere(start, end, slope, true) ?? start;
  const settled = Math.max(SHOULDER_SETTLED * slope(peak), SHOULDER_SLOPE / 2);
  let turn = Math.min(end, peak + window);
  for (let y = peak; y < end; y++) {
    if (slope(y) < settled) {
      turn = y;
      break;
    }
  }
  const edge = outward(figure, side, turn);
  return edge === undefined ? undefined : { y: turn, x: edgeX(side, edge) };
}

/** The row below the neck where the width's steepest rise settles: the shoulder read from widths alone. */
function widthRise(figure: Figure, neck: number, headLength: number): number | undefined {
  const window = Math.max(1, Math.round(RISE_WINDOW * figureHeight(figure)));
  const end = Math.min(figure.ground - window, Math.round(neck + (SHOULDER_SEARCH + 0.1) * headLength));
  const rise = (y: number) => centralWidth(figure, y + window) - centralWidth(figure, y);
  const steepest = rowWhere(neck, end, rise, true);
  if (steepest === undefined || rise(steepest) <= 0) return undefined;
  const settled = RISE_SETTLED * rise(steepest);
  for (let y = steepest; y < end; y++) if (rise(y) < settled) return y + window;
  return steepest + window;
}

const headsApart = (pixels: number, headLength: number): string => (Math.abs(pixels) / headLength).toFixed(2);

/**
 * The shoulder: where the silhouette's outline turns from the neck and trapezius into the arm, read on each side from the outline's
 * slope and checked against the width's rise. Clothes count only by their outline: a collar or a hem is no landmark.
 */
export function readShoulder(figure: Figure, neck: number, headLength: number): PixelMark | undefined {
  const turns = SIDES.map((side) => shoulderTurn(figure, side, neck, headLength));
  const found = turns.filter((t): t is OutlinePoint => t !== undefined);
  const rise = widthRise(figure, neck, headLength);
  if (found.length === 0) {
    return rise === undefined ? undefined : { y: rise, status: "estimated", reason: "no outline turn from neck to arm: placed where the width stops rising" };
  }
  const y = Math.round(found.reduce((s, p) => s + p.y, 0) / found.length);
  const reasons: string[] = [];
  if (found.length === 2 && Math.abs(found[0].y - found[1].y) > SIDES_AGREE * headLength) {
    reasons.push(`the two shoulders differ by ${headsApart(found[0].y - found[1].y, headLength)} heads`);
  } else if (found.length === 1) {
    reasons.push(`only the ${SIDE_NAMES[turns[0] ? "-1" : "1"]} shoulder's outline turns`);
  }
  if (rise !== undefined && Math.abs(rise - y) > READINGS_AGREE * headLength) {
    reasons.push(`two methods disagree by ${headsApart(rise - y, headLength)} heads: the outline turns at y ${y}, the width stops rising at y ${rise}`);
  }
  const points = found.map((p) => ({ x: p.x, y: p.y }));
  return reasons.length > 0 ? { y, status: "unsure", reason: reasons.join("; "), points } : { y, status: "found", points };
}

/** The row an arm parts from the torso on one side: the first row below `from` where that edge moves in and stays in. */
function armParts(figure: Figure, side: Side, from: number, to: number, headLength: number): OutlinePoint | undefined {
  const hold = Math.max(1, Math.round(GAP_HOLDS * headLength));
  for (let y = from + 1; y < to; y++) {
    const before = centralRun(figure, y - 1);
    const now = outward(figure, side, y);
    const was = outward(figure, side, y - 1);
    if (!before || now === undefined || was === undefined) continue;
    const jump = was - now;
    if (jump <= ARM_PARTS * runWidth(before)) continue;
    let holds = true;
    for (let k = 1; k <= hold && holds; k++) {
      const later = outward(figure, side, y + k);
      if (later !== undefined && was - later < jump / 2) holds = false;
    }
    if (holds) return { y, x: edgeX(side, now) };
  }
  return undefined;
}

/**
 * The armpit: the first row below the shoulder where background parts an arm from the torso, read on each side. Arms with no
 * background between them and the body give no such row: the armpit is then placed by proportion and says so.
 */
export function readArmpit(figure: Figure, shoulder: number, crotch: number, headLength: number): PixelMark {
  const to = Math.min(Math.round(shoulder + ARMPIT_REACH * (crotch - shoulder)), Math.round(shoulder + ARMPIT_MAX_HEADS * headLength));
  const parts = SIDES.map((side) => armParts(figure, side, shoulder, to, headLength));
  const found = parts.filter((p): p is OutlinePoint => p !== undefined);
  if (found.length === 0) {
    const y = Math.round(shoulder + ARMPIT_SHARE * (crotch - shoulder));
    const raised = runsAt(figure, shoulder - Math.max(2, Math.round(RAISED_PROBE * headLength))).length >= RAISED_RUNS;
    const why = raised ? "arms raised: no arm parts from the torso below the shoulder" : "no gap between an arm and the torso";
    return { y, status: "estimated", reason: `${why}: placed by proportion below the shoulder` };
  }
  const points = found.map((p) => ({ x: p.x, y: p.y }));
  const upper = Math.min(...found.map((p) => p.y));
  if (found.length === 2 && Math.abs(found[0].y - found[1].y) > SIDES_AGREE * headLength) {
    const reason = `the arms part ${headsApart(found[0].y - found[1].y, headLength)} heads apart (y ${found[0].y} and ${found[1].y}): one arm may touch the torso lower; took the higher`;
    return { y: upper, status: "unsure", reason, points };
  }
  if (found.length === 1) {
    const side = SIDE_NAMES[parts[0] ? "-1" : "1"];
    return { y: upper, status: "unsure", reason: `only the ${side} arm parts from the torso; the other touches it`, points };
  }
  return { y: Math.round((found[0].y + found[1].y) / 2), status: "found", points };
}
