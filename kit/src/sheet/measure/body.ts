import { centralWidth, figureHeight, rowWhere, runsAt, type Figure } from "./figure.ts";
import type { PixelMark } from "./landmark.ts";
import { median } from "../core/solve.ts";
import type { Run } from "./rows.ts";
import { readArmpit, readShoulder } from "./shoulders.ts";

/** Legs or feet touching across the gap for at most this share of the height still leave one gap. */
const CONTACT_SHARE = 0.04;
/** A gap between the legs is at least this share of the height long. */
const LEG_GAP_SHARE = 0.08;
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
/** The waist is a real narrowing only where it is this much narrower than the chest above; wider, clothes (a sweater) hide it. */
const WAIST_NARROWING = 0.92;
/** A hidden waist is placed this share of the way from the armpit down to the crotch. */
const HIDDEN_WAIST_SHARE = 0.4;
/** The chest's width is the median over this share of the span below the armpit (or shoulder). */
const CHEST_ROWS = 0.15;
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
  const shoulderMark = readShoulder(figure, neck, headLength);
  const shoulder = shoulderMark?.y;
  if (shoulderMark) out.shoulder = shoulderMark;
  const foundCrotch = crotchRow(figure, shoulder ?? neck);
  out.legSplit = foundCrotch?.split ?? figure.axis;
  const crotch = foundCrotch?.row ?? Math.round(figure.ground - CROTCH_SHARE * (figure.ground - neck));
  out.crotch = foundCrotch !== undefined ? { y: crotch, status: "found" } : { y: crotch, status: "estimated", reason: "no gap between the legs: placed by proportion" };
  const torsoTop = shoulder ?? neck;
  const torsoBottom = crotch;
  const armpitMark = shoulder !== undefined ? readArmpit(figure, shoulder, torsoBottom, headLength) : undefined;
  if (armpitMark) out.armpit = armpitMark;
  const armpit = armpitMark?.status === "estimated" ? undefined : armpitMark?.y;
  const from = armpit ?? torsoTop;
  const span = torsoBottom - from;
  const waistFrom = armpit !== undefined ? WAIST_RANGE[0] : WAIST_FROM_SHOULDER;
  const waist = rowWhere(from + waistFrom * span, from + WAIST_RANGE[1] * span, (y) => centralWidth(figure, y) || Infinity);
  if (waist !== undefined) {
    const chest = median(Array.from({ length: Math.max(1, Math.round(CHEST_ROWS * span)) }, (_, i) => centralWidth(figure, from + i)).filter((w) => w > 0));
    if (chest > 0 && centralWidth(figure, waist) > WAIST_NARROWING * chest) {
      const hidden = Math.round(from + HIDDEN_WAIST_SHARE * span);
      out.waist = { y: hidden, status: "estimated", reason: `no narrowing below the chest (the waist row is ${Math.round((100 * centralWidth(figure, waist)) / chest)}% of the chest's width): clothes hide the waist, placed ${HIDDEN_WAIST_SHARE} of the way from the ${armpit !== undefined ? "armpit" : "shoulder"} to the crotch` };
    } else {
      out.waist = out.crotch.status === "found" ? { y: waist, status: "found" } : { y: waist, status: "estimated", reason: "read between an estimated armpit and crotch" };
    }
  }
  if (out.waist !== undefined) {
    const waistRow = out.waist.y;
    const widest = rowWhere(waistRow, crotch, (y) => centralWidth(figure, y), true);
    const flat = widest === undefined || centralWidth(figure, widest) <= centralWidth(figure, waistRow) + 1;
    out.hip = flat ? { y: Math.round(crotch - HIP_SHARE * (crotch - waistRow)), status: "estimated", reason: "no widening below the waist: placed by proportion" } : { y: widest, status: "found" };
  }
  if (foundCrotch && out.hip?.status === "found") {
    const hipWidth = centralWidth(figure, out.hip.y);
    if (centralWidth(figure, crotch - 1) < LEGS_TOUCH * hipWidth) {
      let y = out.hip.y;
      while (y < crotch && centralWidth(figure, y) >= LEGS_TOUCH * hipWidth) y++;
      out.crotch = { y, status: "estimated", reason: "legs touch below the hips: placed where the hips end" };
    }
  }
  return out;
}
