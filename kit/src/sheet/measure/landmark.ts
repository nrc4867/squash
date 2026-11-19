/**
 * How a landmark was placed: read from the silhouette, read but doubtful (the reason says why), placed by proportion, or given by the
 * author.
 */
export type LandmarkStatus = "found" | "unsure" | "estimated" | "given";

/** Every status, most trusted first. */
export const LANDMARK_STATUSES: readonly LandmarkStatus[] = ["found", "unsure", "estimated", "given"];

/** A landmark as a pixel place on the image before it is put in units. */
export interface PixelMark {
  /** The row, in the image's pixels. */
  y: number;
  /** The column, where one place means something (a hand tip, an ear tip). */
  x?: number;
  status: LandmarkStatus;
  /** Why it is unsure or estimated: what the silhouette did not show, or which check it failed. */
  reason?: string;
  /** The places on the outline it was read from, where it has them: the shoulder's or armpit's point on each side. */
  points?: Array<{ x: number; y: number }>;
}

/** A landmark in every unit the tools speak. */
export interface Landmark extends PixelMark {
  /** Height above the ground in body units: ready for `bodyPlan`. */
  height: number;
  /** Height above the ground in head heights. */
  heads: number;
  /** Depth below the top of the figure in head heights. */
  headsDown: number;
}

/** Every landmark name the measure looks for. */
export const LANDMARK_NAMES = [
  "top",
  "earTip",
  "crown",
  "chin",
  "neck",
  "shoulder",
  "armpit",
  "waist",
  "hip",
  "crotch",
  "knee",
  "ankle",
  "ground",
  "handTip",
  "tailTop",
  "tailBottom",
] as const;

/** Reads `name:x,y;name:x,y` (pixels of the image measured) into marks given by the author. */
export function parseLandmarks(text: string): Record<string, PixelMark> {
  const out: Record<string, PixelMark> = {};
  for (const entry of text.split(";").map((e) => e.trim()).filter(Boolean)) {
    const match = entry.match(/^([A-Za-z][\w-]*):(-?[\d.]+),(-?[\d.]+)$/);
    if (!match) throw new Error(`landmark "${entry}" is not name:x,y`);
    out[match[1]] = { x: Number(match[2]), y: Number(match[3]), status: "given" };
  }
  return out;
}
