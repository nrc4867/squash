import { median } from "../core/solve.ts";
import type { Anatomy } from "./anatomy.ts";
import { runsAt } from "./figure.ts";
import type { PixelMark } from "./landmark.ts";
import { joinReasons, sizeReading, type Proportions, type ReadingLine, type ReadingScale } from "./readings.ts";
import { runWidth, type Run } from "./rows.ts";
import { ankleRow, splitFeet, type FootPixels, type FootSplitHow } from "./footSplit.ts";
import { FOOT_WIDTH_OF_LENGTH } from "./sizeChecks.ts";

/** A foot is read over the rows from this share of the way from the ankle to the ground, down to the ground. */
const FOOT_FROM = 0.35;
/** The two feet's widths differ by at most this share before the reading is unsure. */
const SIDES_AGREE = 0.3;
/** A foot is this many heads long, and this many wide, or the reading is unsure. */
const FOOT_HEADS: [number, number] = [0.15, 1.2];
const WIDTH_HEADS: [number, number] = [0.08, 0.7];

/** Each foot's widest row from row `from` down, the feet split where they touch (`splitFeet`). */
function footWidths(anatomy: Anatomy, ankle: number, from: number): { widths: Array<{ side: FootPixels["side"]; width: number; row: number; run: Run }>; how: FootSplitHow; divider?: number } {
  const split = splitFeet(anatomy, ankle);
  const widths = split.feet.flatMap((foot) => {
    let best: { side: FootPixels["side"]; width: number; row: number; run: Run } | undefined;
    for (const [y, run] of foot.rows) if (y >= from && (!best || runWidth(run) > best.width)) best = { side: foot.side, width: runWidth(run), row: y, run };
    return best ? [best] : [];
  });
  return { widths, how: split.how, ...(split.divider !== undefined ? { divider: split.divider } : {}) };
}

/** The longest run on the figure's lowest rows: a foot from heel to toe in a side view, the two feet's span when they overlap. */
function footSpan(anatomy: Anatomy, from: number): { length: number; row: number; run: Run; runs: number } | undefined {
  let best: { length: number; row: number; run: Run; runs: number } | undefined;
  for (let y = from; y < anatomy.figure.ground; y++) {
    const runs = runsAt(anatomy.figure, y);
    for (const run of runs) if (!best || runWidth(run) > best.length) best = { length: runWidth(run), row: y, run, runs: runs.length };
  }
  return best;
}

function given(marks: Record<string, PixelMark>): { length: number; line: ReadingLine } | undefined {
  const heel = marks.heel;
  const toe = marks.toeTip;
  if (heel?.status !== "given" || toe?.status !== "given" || heel.x === undefined || toe.x === undefined) return undefined;
  return { length: Math.hypot(toe.x - heel.x, toe.y - heel.y), line: { from: { x: heel.x, y: heel.y }, to: { x: toe.x, y: toe.y } } };
}

/**
 * Both feet's width, each foot's own widest row below the ankle, the feet split where they touch (read in a front or back view, where
 * the feet face the viewer or away; unsure past a given length's share, `FOOT_WIDTH_OF_LENGTH`), and
 * a foot's length, read in a side view as the longest stretch of the figure on the rows near the ground and unsure in a three-quarter
 * view, where the foot is foreshortened. A front or back view cannot tell a foot's length: it is left out with the reason.
 */
export function readFeet(anatomy: Anatomy, scale: ReadingScale): Proportions {
  const out: Proportions = { readings: {}, missing: {} };
  const facing = anatomy.view.guess.facing;
  const ankle = ankleRow(anatomy);
  const from = Math.round(ankle + FOOT_FROM * (scale.ground - ankle));
  const reasonAnkle = anatomy.marks.ankle && anatomy.marks.ankle.status !== "found" ? `the ankle row is ${anatomy.marks.ankle.status}, so where the foot starts is too` : undefined;
  const givenLength = given(anatomy.marks);
  if (facing === "front" || facing === "back") {
    const { widths: read, how, divider } = footWidths(anatomy, ankle, from);
    if (read.length > 0) {
      const values = read.map((w) => w.width);
      let status: "found" | "unsure" = reasonAnkle ? "unsure" : "found";
      const reasons: Array<string | undefined> = [reasonAnkle];
      if (how === "notch") reasons.push(`the feet touch: split at the notch between them, column ${divider}`);
      if (how === "legs") {
        status = "unsure";
        reasons.push(`the feet join with no notch between them: split under the gap between the legs, column ${divider}`);
      }
      if (read.length === 2 && Math.abs(values[0] - values[1]) > SIDES_AGREE * Math.max(...values)) {
        status = "unsure";
        reasons.push(`the two feet differ by ${Math.round((100 * Math.abs(values[0] - values[1])) / Math.max(...values))}% (one is turned or hidden): the mean is shown`);
      }
      if (read.length === 1) {
        status = "unsure";
        reasons.push("only one foot is read: the feet stand together with nothing to split them at, or one is hidden");
      }
      if (values.some((v) => v / anatomy.headLength < WIDTH_HEADS[0] || v / anatomy.headLength > WIDTH_HEADS[1])) {
        status = "unsure";
        reasons.push(`a foot is ${(median(values) / anatomy.headLength).toFixed(2)} heads wide, expected ${WIDTH_HEADS[0]} to ${WIDTH_HEADS[1]}: socks or shoes, or the feet merge`);
      }
      if (givenLength && median(values) > FOOT_WIDTH_OF_LENGTH * givenLength.length) {
        status = "unsure";
        reasons.push(`a foot is ${(median(values) / givenLength.length).toFixed(2)} of its given length wide, more than ${FOOT_WIDTH_OF_LENGTH}: the width may span both feet or a turned foot`);
      }
      out.readings.footWidth = sizeReading(
        "footWidth",
        median(values),
        scale,
        status,
        read.map((w) => ({ from: { x: w.run.l, y: w.row }, to: { x: w.run.r + 1, y: w.row } })),
        joinReasons(...reasons),
        read.length === 2 ? "both" : read[0].side,
      );
    } else {
      out.missing.footWidth = "no foot is read standing on the ground in this view";
    }
    if (givenLength) out.readings.footLength = sizeReading("footLength", givenLength.length, scale, "given", [givenLength.line]);
    else out.missing.footLength = `a ${facing} view foreshortens the foot (the toes point ${facing === "front" ? "at the viewer" : "away"}): read it from a side view, or give landmarks=heel:x,y;toeTip:x,y`;
    return out;
  }
  const span = footSpan(anatomy, from);
  if (givenLength) {
    out.readings.footLength = sizeReading("footLength", givenLength.length, scale, "given", [givenLength.line]);
    return out;
  }
  if (!span) {
    out.missing.footLength = "no figure is read near the ground";
    return out;
  }
  const reasons: Array<string | undefined> = [reasonAnkle];
  let status: "found" | "unsure" = reasonAnkle ? "unsure" : "found";
  if (facing === "three-quarter") {
    status = "unsure";
    reasons.push("three-quarter view: the foot is foreshortened, so its true length is longer than this");
  }
  if (span.runs > 1) {
    status = "unsure";
    reasons.push("two feet stand apart on the row: the longer is read");
  }
  const heads = span.length / anatomy.headLength;
  if (heads < FOOT_HEADS[0] || heads > FOOT_HEADS[1]) {
    status = "unsure";
    reasons.push(`the foot is ${heads.toFixed(2)} heads long, expected ${FOOT_HEADS[0]} to ${FOOT_HEADS[1]}`);
  }
  out.readings.footLength = sizeReading("footLength", span.length, scale, status, [{ from: { x: span.run.l, y: span.row }, to: { x: span.run.r + 1, y: span.row } }], joinReasons(...reasons));
  out.missing.footWidth = `a ${facing} view hides the foot's width: read it from a front or back view`;
  return out;
}
