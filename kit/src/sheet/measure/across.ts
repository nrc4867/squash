import { roundTo } from "../../geom/point.ts";
import { median } from "../core/solve.ts";
import type { Facing } from "../views/orientation.ts";
import type { Measurement } from "./measurement.ts";
import type { Reading } from "./readings.ts";
import { FOOT_WIDTH_OF_LENGTH, HAND_WIDTH_OF_LENGTH, VIEWS_AGREE } from "./sizeChecks.ts";

/** The sizes compared across views, each with the facings that read it truly; the others' readings only check it. */
const SIZES: Record<string, readonly Facing[]> = {
  footWidth: ["front", "back"],
  footLength: ["side"],
  handLength: ["front", "back", "side"],
  handWidth: ["front", "back", "side"],
};

/** A width read against a length, with the share past which the width is suspect. */
const WIDTH_OF_LENGTH: Array<[string, string, number]> = [
  ["footWidth", "footLength", FOOT_WIDTH_OF_LENGTH],
  ["handWidth", "handLength", HAND_WIDTH_OF_LENGTH],
];

/** One view's reading of a size, for the comparison. */
export interface ViewReading {
  view: string;
  facing: Facing;
  ofHead: number;
  status: Reading["status"];
}

/** A size read across several views: the views that read it truly set it, all of them check it. */
export interface AcrossReading {
  name: string;
  /** As a share of the head, the median of the views that set it. */
  ofHead: number;
  ofHeight: number;
  /** In body units, the median of the views that set it. */
  value: number;
  status: Reading["status"];
  reason?: string;
  /** Every view's reading, those that set it first. */
  views: ViewReading[];
}

const PLACES = 4;

function percent(a: number, b: number): number {
  return Math.round((100 * Math.abs(a - b)) / Math.max(a, b));
}

/** One size across the views that read it, or undefined when none does. */
function combine(name: string, truly: readonly Facing[], measurements: readonly Measurement[]): AcrossReading | undefined {
  const all = measurements.flatMap((m) => {
    const r = m.proportions[name];
    return r ? [{ reading: r, view: { view: m.view.name, facing: m.view.guess.facing, ofHead: r.ofHead, status: r.status } }] : [];
  });
  const kept = all.filter((a) => a.reading.status !== "estimated");
  if (kept.length === 0) return undefined;
  const given = kept.filter((a) => a.reading.status === "given");
  const sure = kept.filter((a) => truly.includes(a.view.facing) && a.reading.status === "found");
  const fromTrue = kept.filter((a) => truly.includes(a.view.facing));
  const setting = given.length ? given : sure.length ? sure : fromTrue.length ? fromTrue : kept;
  const reasons: string[] = [];
  let status: Reading["status"] = given.length ? "given" : sure.length ? "found" : "unsure";
  if (!given.length && !sure.length) {
    reasons.push(fromTrue.length ? `no ${truly.join(" or ")} view reads it surely` : `no ${truly.join(" or ")} view reads it: taken from ${kept.map((a) => a.view.view).join(", ")}`);
  }
  const sorted = [...kept].sort((a, b) => a.reading.ofHead - b.reading.ofHead);
  const low = sorted[0];
  const high = sorted[sorted.length - 1];
  if (kept.length > 1 && high.reading.ofHead - low.reading.ofHead > VIEWS_AGREE * high.reading.ofHead) {
    if (status !== "given") status = "unsure";
    reasons.push(`the views disagree: ${high.view.view} reads ${high.reading.ofHead.toFixed(2)} heads, ${low.view.view} ${low.reading.ofHead.toFixed(2)} (${percent(high.reading.ofHead, low.reading.ofHead)}% apart)`);
  }
  const settingViews = new Set(setting.map((a) => a.view.view));
  return {
    name,
    ofHead: roundTo(median(setting.map((a) => a.reading.ofHead)), 3),
    ofHeight: roundTo(median(setting.map((a) => a.reading.ofHeight)), PLACES),
    value: roundTo(median(setting.map((a) => a.reading.value)), PLACES),
    status,
    ...(reasons.length ? { reason: reasons.join("; ") } : {}),
    views: [...setting, ...all.filter((a) => !settingViews.has(a.view.view))].map((a) => a.view),
  };
}

/**
 * The hand and foot sizes of several views of one character, checked against each other: each size from the views that read it
 * truly (a foot's width from the front or back, its length from the side), unsure where two views differ by more than `VIEWS_AGREE`
 * of the larger, naming them, and a width unsure where it is too near its length (`FOOT_WIDTH_OF_LENGTH`, `HAND_WIDTH_OF_LENGTH`).
 */
export function readAcross(measurements: readonly Measurement[]): Record<string, AcrossReading> {
  const out: Record<string, AcrossReading> = {};
  for (const [name, facings] of Object.entries(SIZES)) {
    const read = combine(name, facings, measurements);
    if (read) out[name] = read;
  }
  for (const [widthName, lengthName, share] of WIDTH_OF_LENGTH) {
    const width = out[widthName];
    const length = out[lengthName];
    if (!width || !length || width.ofHead <= share * length.ofHead) continue;
    const why = `${(width.ofHead / length.ofHead).toFixed(2)} of the length wide (${width.views[0].view} against ${length.views[0].view}), more than ${share}: the width may span two touching parts or a turned one`;
    out[widthName] = { ...width, status: width.status === "given" ? "given" : "unsure", reason: width.reason ? `${width.reason}; ${why}` : why };
  }
  return out;
}
