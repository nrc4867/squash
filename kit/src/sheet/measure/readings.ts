import { roundTo } from "../../geom/point.ts";
import type { LandmarkStatus } from "./landmark.ts";

/** A line the reading was taken along, in the sheet's pixels: a hand's length from wrist to tip, a foot's width across. */
export interface ReadingLine {
  from: { x: number; y: number };
  to: { x: number; y: number };
}

/**
 * A size or a place read off a view, in every unit the tools speak. A size (`handLength`, `eyeWidth`) is a length; a place (`eyeLine`,
 * `nose`, `mouth`) is a depth down the head from the crown.
 */
export interface Reading {
  /** The name a recipe's target for it uses. */
  name: string;
  kind: "size" | "place";
  /** A size's length in body units; a place's height above the ground. */
  value: number;
  /** A size's length, or a place's depth from the crown, as a share of the head's height (crown to chin). */
  ofHead: number;
  /** A size's length, or a place's height above the ground, as a share of the figure's height (ground to crown). */
  ofHeight: number;
  status: LandmarkStatus;
  /** Why it is unsure or estimated. */
  reason?: string;
  /** Which of a pair it is read from, or `both`, where it has sides. */
  side?: "image-left" | "image-right" | "both";
  /** Where it was read, in the sheet's pixels, for the images. */
  lines: ReadingLine[];
}

/** Everything read of the hands, feet and face of one view: the readings, and what could not be read and why. */
export interface Proportions {
  readings: Record<string, Reading>;
  /** Names that could not be read, each with the reason. */
  missing: Record<string, string>;
}

/** The units of a measurement: what a pixel is in body units and how many pixels a head and the figure are. */
export interface ReadingScale {
  bodyPerPixel: number;
  headPixels: number;
  /** The figure's height in pixels, ground to crown. */
  heightPixels: number;
  crown: number;
  ground: number;
}

const PLACES = 4;

/** A size of `pixels` along `lines`, in every unit. */
export function sizeReading(name: string, pixels: number, scale: ReadingScale, status: LandmarkStatus, lines: ReadingLine[], reason?: string, side?: Reading["side"]): Reading {
  return {
    name,
    kind: "size",
    value: roundTo(pixels * scale.bodyPerPixel, PLACES),
    ofHead: roundTo(pixels / scale.headPixels, 3),
    ofHeight: roundTo(pixels / scale.heightPixels, 4),
    status,
    ...(reason ? { reason } : {}),
    ...(side ? { side } : {}),
    lines,
  };
}

/** A place on row `y`, in every unit: its depth down from the crown as a share of the head, its height above the ground. */
export function placeReading(name: string, y: number, scale: ReadingScale, status: LandmarkStatus, lines: ReadingLine[], reason?: string): Reading {
  return {
    name,
    kind: "place",
    value: roundTo((scale.ground - y) * scale.bodyPerPixel, PLACES),
    ofHead: roundTo((y - scale.crown) / scale.headPixels, 3),
    ofHeight: roundTo((scale.ground - y) / scale.heightPixels, 4),
    status,
    ...(reason ? { reason } : {}),
    lines,
  };
}

/** Joins two sides' reasons, leaving out repeats. */
export function joinReasons(...reasons: Array<string | undefined>): string | undefined {
  const kept = [...new Set(reasons.filter((r): r is string => !!r))];
  return kept.length ? kept.join("; ") : undefined;
}

/** The weaker of two statuses: found, then unsure, then estimated. */
export function weakest(a: LandmarkStatus, b: LandmarkStatus): LandmarkStatus {
  const order: LandmarkStatus[] = ["found", "given", "unsure", "estimated"];
  return order.indexOf(a) >= order.indexOf(b) ? a : b;
}
