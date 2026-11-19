import type { Anatomy } from "../../measure/anatomy.ts";
import type { Measurement } from "../../measure/measurement.ts";

export type Confidence = "high" | "medium" | "low";

/** A measured view: what `readAnatomy` and `measurementOf` give. */
export interface MeasuredView {
  anatomy: Anatomy;
  measurement: Measurement;
}

/** Radii for `tube(...)`, in body units at stations along the part. */
export interface PartProfile {
  rest: "up" | "down";
  /** The part's drawn length in body units: from its socket end to its far end. */
  length: number;
  /** Each station's distance from the socket end, body units: `tube`'s `at`. */
  at: number[];
  /** Each station's height above the ground, for parts that stand or hang upright. */
  heights?: number[];
  profiles: { left?: number[]; right?: number[]; outer?: number[]; inner?: number[]; front?: number[]; back?: number[] };
  confidence: Confidence;
  notes: string[];
}

export interface SheetProfiles {
  torso?: PartProfile;
  /** Legs by the character's side (`leg-l`, `leg-r`) when the front view's facing says which is which, else by image side. */
  legs: Record<string, PartProfile>;
  arms: Record<string, PartProfile>;
  skipped: string[];
  /** Why every radius here is only as sure as the views they were read from: a three-quarter view read as a front or a side. */
  warnings: string[];
}

export interface ProfileOptions {
  /** Stations a part, default 7. */
  stations?: number;
  /** Which way the side view faces on the image, when its feet do not tell. */
  sideFacing?: "left" | "right";
}

