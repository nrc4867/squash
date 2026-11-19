import { centralRun, centralWidth, figureHeight, rowWhere, runsAt, type Figure } from "./figure.ts";
import type { PixelMark } from "./landmark.ts";
import { median } from "../core/solve.ts";
import { runWidth, type Run } from "./rows.ts";

/** The window, as a share of the figure's height, over which a width's rise is read. */
const RISE_WINDOW = 0.02;
/** The shoulder is where the rise below the neck falls under this share of its steepest. */
const RISE_SETTLED = 0.3;
/** The search for the shoulders runs this many neck-to-crown heights below the neck. */
const SHOULDER_SEARCH = 1;
/** An arm parts from the torso where one edge of the central run moves in by this share of its width from one row to the next. */
const ARM_PARTS = 0.1;
/** Legs or feet touching across the gap for at most this share of the height still leave one gap. */
const CONTACT_SHARE = 0.04;
/** A gap between the legs is at least this share of the height long. */
const LEG_GAP_SHARE = 0.08;
/** The armpits lie within this many heads below the shoulders. */
const ARMPIT_REACH = 1;
/** With no gap between the legs, the crotch sits this share of the way from the ground up to the neck, as in most figures. */
const CROTCH_SHARE = 0.55;
/** The waist is searched between these shares of the way from the shoulders (or armpits) to the crotch. */
const WAIST_RANGE: [number, number] = [0.15, 0.9];
/** With no armpits, arms may hug the chest: the waist search starts this share of the way from the shoulders. */
const WAIST_FROM_SHOULDER = 0.35;
/** Above the gap, a width under this share of the hips' is the legs touching, not the hips: the crotch is where the hips end. */
const LEGS_TOUCH = 0.9;
/** The gap between the legs comes within this share of the height of the axis. */
const GAP_BAND = 0.04;
/** With no widest hip row, the hip joint sits this share of the way from the crotch up to the waist. */
const HIP_SHARE = 0.3;

export interface BodyReading {
  shoulder?: PixelMark;
  armpit?: PixelMark;
  waist?: PixelMark;
  hip?: PixelMark;
  crotch?: PixelMark;
  /** The column the legs part along: the middle of the gap between them, or the axis. */
  legSplit?: number;
}

function shoulderRow(figure: Figure, neck: number, headLength: number): number | undefined {
  const window = Math.max(1, Math.round(RISE_WINDOW * figureHeight(figure)));
  const end = Math.min(figure.ground - window, neck + SHOULDER_SEARCH * headLength);
  const rise = (y: number) => centralWidth(figure, y + window) - centralWidth(figure, y);
  const steepest = rowWhere(neck, end, rise, true);
  if (steepest === undefined || rise(steepest) <= 0) return undefined;
  const settled = RISE_SETTLED * rise(steepest);
  for (let y = steepest; y < end; y++) if (rise(y) < settled) return y + window;
  return steepest + window;
}

/** The lower of the rows where each side's edge of the torso jumps inward, as an arm parts from it. */
function armpitRow(figure: Figure, from: number, to: number): number | undefined {
  const found: number[] = [];
  for (const side of [-1, 1] as const) {
    for (let y = from + 1; y < to; y++) {
      const before = centralRun(figure, y - 1);
      const now = centralRun(figure, y);
      if (!before || !now) continue;
      const inward = side < 0 ? now.l - before.l : before.r - now.r;
      if (inward > ARM_PARTS * runWidth(before)) {
        found.push(y);
        break;
      }
    }
  }
  return found.length ? Math.max(...found) : undefined;
}

/** The middle of the gap between two runs that comes within `band` of `axis`, nearest it; undefined when no gap does. */
function gapNear(runs: readonly Run[], axis: number, band: number): number | undefined {
  let best: number | undefined;
  for (let i = 1; i < runs.length; i++) {
    const from = runs[i - 1].r + 1;
    const to = runs[i].l - 1;
    if (to < axis - band || from > axis + band) continue;
    const middle = (from + to) / 2;
    if (best === undefined || Math.abs(middle - axis) < Math.abs(best - axis)) best = middle;
  }
  return best;
}

/**
 * The row above the gap between the legs: the longest stretch of rows below `above` with a gap near the axis, short contacts (knees or
 * feet touching) bridged; with the column the gap runs down, where the legs part.
 */
function crotchRow(figure: Figure, above: number): { row: number; split: number } | undefined {
  const height = figureHeight(figure);
  const bridge = Math.round(CONTACT_SHARE * height);
  const band = Math.round(GAP_BAND * height);
  const gaps: Array<{ top: number; bottom: number; middles: number[] }> = [];
  for (let y = above + 1; y < figure.ground; y++) {
    const middle = gapNear(runsAt(figure, y), figure.axis, band);
    if (middle === undefined) continue;
    const last = gaps[gaps.length - 1];
    if (last && y - last.bottom - 1 <= bridge) {
      last.bottom = y;
      last.middles.push(middle);
    } else {
      gaps.push({ top: y, bottom: y, middles: [middle] });
    }
  }
  const longest = gaps.sort((a, b) => b.bottom - b.top - (a.bottom - a.top))[0];
  if (!longest || longest.bottom - longest.top + 1 < LEG_GAP_SHARE * height) return undefined;
  return { row: longest.top - 1, split: Math.round(median(longest.middles)) };
}

/** Shoulders, armpits, waist, hip and crotch, read down the axis from the neck. */
export function readBody(figure: Figure, neck: number, headLength: number): BodyReading {
  const out: BodyReading = {};
  const shoulder = shoulderRow(figure, neck, headLength);
  if (shoulder !== undefined) out.shoulder = { y: shoulder, status: "found" };
  const foundCrotch = crotchRow(figure, shoulder ?? neck);
  out.legSplit = foundCrotch?.split ?? figure.axis;
  const crotch = foundCrotch?.row ?? Math.round(figure.ground - CROTCH_SHARE * (figure.ground - neck));
  out.crotch = { y: crotch, status: foundCrotch !== undefined ? "found" : "estimated" };
  const torsoTop = shoulder ?? neck;
  const torsoBottom = crotch;
  const armpit = armpitRow(figure, torsoTop, Math.min(torsoBottom, torsoTop + ARMPIT_REACH * headLength));
  if (armpit !== undefined) out.armpit = { y: armpit, status: "found" };
  const from = armpit ?? torsoTop;
  const span = torsoBottom - from;
  const waistFrom = armpit !== undefined ? WAIST_RANGE[0] : WAIST_FROM_SHOULDER;
  const waist = rowWhere(from + waistFrom * span, from + WAIST_RANGE[1] * span, (y) => centralWidth(figure, y) || Infinity);
  if (waist !== undefined) out.waist = { y: waist, status: out.crotch.status };
  if (waist !== undefined) {
    const widest = rowWhere(waist, crotch, (y) => centralWidth(figure, y), true);
    const flat = widest === undefined || centralWidth(figure, widest) <= centralWidth(figure, waist) + 1;
    out.hip = flat ? { y: Math.round(crotch - HIP_SHARE * (crotch - waist)), status: "estimated" } : { y: widest, status: "found" };
  }
  if (foundCrotch && out.hip?.status === "found") {
    const hipWidth = centralWidth(figure, out.hip.y);
    if (centralWidth(figure, crotch - 1) < LEGS_TOUCH * hipWidth) {
      let y = out.hip.y;
      while (y < crotch && centralWidth(figure, y) >= LEGS_TOUCH * hipWidth) y++;
      out.crotch = { y, status: "estimated" };
    }
  }
  return out;
}
