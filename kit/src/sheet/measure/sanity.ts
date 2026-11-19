import type { PixelMark } from "./landmark.ts";

/** One proportion a set of landmarks breaks. */
export interface LandmarkIssue {
  /** The landmarks it concerns. */
  landmarks: string[];
  message: string;
}

/** Landmarks from the crown down, in the order their rows must run. */
const DOWN_THE_BODY = ["crown", "chin", "neck", "shoulder", "armpit", "waist", "hip", "crotch", "knee", "ankle", "ground"] as const;
/** The shoulder lies at most this many heads below the chin. */
const SHOULDER_BELOW_CHIN = 1;
/** The armpit lies this share of the way from the shoulder to the crotch, within these bounds, and within these heads of the shoulder. */
const ARMPIT_SPAN: [number, number] = [0.08, 0.5];
const ARMPIT_HEADS: [number, number] = [0.1, 0.6];
/** The waist lies this share of the way from the armpit to the crotch, within these bounds. */
const WAIST_SPAN: [number, number] = [0, 0.9];
/** The crotch's height is this share of the figure's, crown to ground, within these bounds. */
const CROTCH_HEIGHT: [number, number] = [0.2, 0.62];
/** The knee lies this share of the way from the crotch to the ankle, within these bounds. */
const KNEE_SPAN: [number, number] = [0.3, 0.7];
/** The ankle stands this share of the figure's height above the ground, within these bounds. */
const ANKLE_HEIGHT: [number, number] = [0.01, 0.2];
/** The head, crown to chin, is this share of the figure's height, within these bounds. */
const HEAD_SHARE: [number, number] = [0.1, 0.6];

const outside = (value: number, [low, high]: [number, number]): boolean => value < low || value > high;
const percent = (share: number): string => `${Math.round(share * 100)}%`;

/**
 * Checks every landmark against the others and against the proportions of a standing figure. Landmarks a failed check names and that
 * were read from the silhouette are returned as unsure with the check as the reason; estimated and given ones stay as they are.
 */
export function checkLandmarks(marks: Record<string, PixelMark>, headLength: number): { marks: Record<string, PixelMark>; issues: LandmarkIssue[] } {
  const issues: LandmarkIssue[] = [];
  const y = (name: string): number | undefined => marks[name]?.y;
  const flag = (landmarks: string[], message: string) => issues.push({ landmarks, message });

  const present = DOWN_THE_BODY.filter((name) => marks[name] !== undefined);
  for (let i = 1; i < present.length; i++) {
    const [above, below] = [present[i - 1], present[i]];
    if (marks[below].y < marks[above].y || (marks[below].y === marks[above].y && below !== "neck")) {
      flag([above, below], `${below} (y ${marks[below].y}) is not below ${above} (y ${marks[above].y})`);
    }
  }
  const crown = y("crown")!;
  const ground = y("ground")!;
  const height = ground - crown;
  const [chin, shoulder, armpit, waist, crotch, knee, ankle] = ["chin", "shoulder", "armpit", "waist", "crotch", "knee", "ankle"].map(y);
  if (chin !== undefined && outside((chin - crown) / height, HEAD_SHARE)) flag(["crown", "chin"], `the head is ${percent((chin - crown) / height)} of the height`);
  if (chin !== undefined && shoulder !== undefined && shoulder - chin > SHOULDER_BELOW_CHIN * headLength) {
    flag(["chin", "shoulder"], `the shoulder is ${((shoulder - chin) / headLength).toFixed(2)} heads below the chin`);
  }
  if (shoulder !== undefined && armpit !== undefined) {
    const heads = (armpit - shoulder) / headLength;
    if (outside(heads, ARMPIT_HEADS)) flag(["shoulder", "armpit"], `the armpit is ${heads.toFixed(2)} heads below the shoulder, expected ${ARMPIT_HEADS[0]} to ${ARMPIT_HEADS[1]}`);
    if (crotch !== undefined && crotch > shoulder && outside((armpit - shoulder) / (crotch - shoulder), ARMPIT_SPAN)) {
      flag(["shoulder", "armpit"], `the armpit is ${percent((armpit - shoulder) / (crotch - shoulder))} of the way from shoulder to crotch, expected ${percent(ARMPIT_SPAN[0])} to ${percent(ARMPIT_SPAN[1])}`);
    }
  }
  if (armpit !== undefined && waist !== undefined && crotch !== undefined && crotch > armpit && outside((waist - armpit) / (crotch - armpit), WAIST_SPAN)) {
    flag(["armpit", "waist"], `the waist is ${percent((waist - armpit) / (crotch - armpit))} of the way from armpit to crotch, expected ${percent(WAIST_SPAN[0])} to ${percent(WAIST_SPAN[1])}`);
  }
  if (crotch !== undefined && outside((ground - crotch) / height, CROTCH_HEIGHT)) {
    flag(["crotch"], `the crotch is at ${percent((ground - crotch) / height)} of the height, expected ${percent(CROTCH_HEIGHT[0])} to ${percent(CROTCH_HEIGHT[1])}`);
  }
  if (crotch !== undefined && knee !== undefined && ankle !== undefined && ankle > crotch && outside((knee - crotch) / (ankle - crotch), KNEE_SPAN)) {
    flag(["knee"], `the knee is ${percent((knee - crotch) / (ankle - crotch))} of the way from crotch to ankle, expected ${percent(KNEE_SPAN[0])} to ${percent(KNEE_SPAN[1])}`);
  }
  if (ankle !== undefined && outside((ground - ankle) / height, ANKLE_HEIGHT)) {
    flag(["ankle"], `the ankle is ${percent((ground - ankle) / height)} of the height above the ground, expected ${percent(ANKLE_HEIGHT[0])} to ${percent(ANKLE_HEIGHT[1])}`);
  }

  const out = { ...marks };
  for (const issue of issues) {
    for (const name of issue.landmarks) {
      const mark = out[name];
      if (!mark || mark.status === "given") continue;
      const reason = mark.reason && mark.reason !== issue.message ? `${mark.reason}; ${issue.message}` : issue.message;
      out[name] = { ...mark, status: mark.status === "found" ? "unsure" : mark.status, reason };
    }
  }
  return { marks: out, issues };
}
