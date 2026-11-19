import { roundTo } from "../geom/point.ts";
import { heightAbove, type ProbeReport } from "../probe/parse.ts";

/** Where a planned height is found in a built character: a part's joint or socket, or the top or bottom of its box. */
export type ProbeMark =
  | { part: string; joint: string }
  | { part: string; socket: string }
  | { part: string; box: "top" | "bottom" };

export interface HeightCheck {
  name: string;
  planned: number;
  built: number;
  /** built - planned, in body units. */
  off: number;
}

function builtY(report: ProbeReport, mark: ProbeMark): number {
  const part = report.parts[mark.part];
  if (!part) throw new Error(`the probe read no part "${mark.part}"; probe part=all`);
  if ("joint" in mark) {
    const joint = part.joints[mark.joint];
    if (!joint) throw new Error(`${mark.part} has no joint "${mark.joint}"; it has ${Object.keys(part.joints).join(", ")}`);
    return joint.at[1];
  }
  if ("socket" in mark) {
    const socket = part.sockets[mark.socket];
    if (!socket) throw new Error(`${mark.part} has no socket "${mark.socket}"; it has ${Object.keys(part.sockets).join(", ")}`);
    return socket.at[1];
  }
  return mark.box === "top" ? part.box.max[1] : part.box.min[1];
}

/** Planned heights against where the build put them, from a probe of every part. */
export function checkHeights<K extends string>(
  report: ProbeReport,
  heights: Readonly<Record<K, number>>,
  marks: Partial<Record<K, ProbeMark>>,
): HeightCheck[] {
  return (Object.entries(marks) as [K, ProbeMark][]).map(([name, mark]) => {
    const built = roundTo(heightAbove(report, builtY(report, mark)), 3);
    return { name, planned: heights[name], built, off: roundTo(built - heights[name], 3) };
  });
}

/** The checks as an aligned table, one line each. */
export function formatHeightChecks(checks: readonly HeightCheck[]): string {
  const width = Math.max(4, ...checks.map((check) => check.name.length));
  return checks
    .map((check) => `${check.name.padEnd(width)}  planned ${check.planned.toFixed(3)}  built ${check.built.toFixed(3)}  off ${check.off >= 0 ? "+" : ""}${check.off.toFixed(3)}`)
    .join("\n");
}

/** What a character file exports as `heights` for `tools/recipe.sh kit heights`: its planned heights and where to find each. */
export interface HeightMarks<K extends string> {
  heights: Readonly<Record<K, number>>;
  marks: Partial<Record<K, ProbeMark>>;
}

/** Pairs a plan's heights with where the probe finds each: `export const heights = heightMarks(plan, { knee: {...} })`. */
export function heightMarks<K extends string>(
  plan: { readonly heights: Readonly<Record<K, number>> },
  marks: Partial<Record<K, ProbeMark>>,
): HeightMarks<K> {
  return { heights: plan.heights, marks };
}
