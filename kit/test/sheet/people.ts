// Synthetic front-view people for the landmark tests: a head, neck, torso, two arms and two legs, drawn from ellipses, rectangles and capsules.
import { setPixel, type Pixel, type Raster } from "squash-kit/sheet";
import { ellipse, FUR, rect, sheet } from "./figures.ts";

export type ArmPose = "a-pose" | "against" | "raised";

export interface PersonOptions {
  arms: ArmPose;
  /** A short sleeve in another colour over each arm's top, wider than the arm. */
  sleeves?: boolean;
  /** A shirt that flares from the chest to cover the hips. */
  looseShirt?: boolean;
  /** The head's radius in pixels. */
  headRadius?: number;
}

export const CENTER = 200;
const TOP = 30;
const NECK_HALF = 12;
const TORSO_HALF = 50;
const TORSO_LENGTH = 150;
const ARM_LENGTH = 150;
const ARM_RADIUS = 8;
const SLEEVE_RADIUS = 12;
const SLEEVE_LENGTH = 40;
const SLEEVE: Pixel = [230, 220, 200];
const SHIRT: Pixel = [200, 90, 70];

/** Where a person built from the options has its landmarks, in sheet rows. */
export interface PersonTruth {
  top: number;
  chin: number;
  shoulder: number;
  crotch: number;
  ground: number;
  head: number;
}

function capsule(image: Raster, x0: number, y0: number, x1: number, y1: number, radius: number, color: Pixel): void {
  const dx = x1 - x0;
  const dy = y1 - y0;
  const length2 = dx * dx + dy * dy;
  for (let y = Math.floor(Math.min(y0, y1) - radius); y <= Math.ceil(Math.max(y0, y1) + radius); y++) {
    for (let x = Math.floor(Math.min(x0, x1) - radius); x <= Math.ceil(Math.max(x0, x1) + radius); x++) {
      const t = Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / length2));
      if (Math.hypot(x - (x0 + t * dx), y - (y0 + t * dy)) <= radius) setPixel(image, x, y, color);
    }
  }
}

const ANGLES: Record<ArmPose, number> = { "a-pose": 28, against: 0, raised: 135 };

/** A standing person on a blank sheet, and where its landmarks are. */
export function person(options: PersonOptions): { image: Raster; truth: PersonTruth } {
  const image = sheet(400, 560);
  const hr = options.headRadius ?? 45;
  const headBottom = TOP + 2 * hr;
  const shoulder = headBottom + 22;
  const crotch = shoulder + TORSO_LENGTH;
  const ground = crotch + 190;
  const torsoColor = options.looseShirt ? SHIRT : FUR;
  ellipse(image, CENTER, TOP + hr, hr * 0.95, hr, FUR);
  rect(image, CENTER - NECK_HALF, headBottom - 8, CENTER + NECK_HALF, shoulder + 2, FUR);
  for (let y = shoulder - 14; y < shoulder; y++) {
    const half = NECK_HALF + ((y - (shoulder - 14)) / 14) * (TORSO_HALF - NECK_HALF);
    rect(image, CENTER - half, y, CENTER + half, y + 1, torsoColor);
  }
  rect(image, CENTER - TORSO_HALF, shoulder, CENTER + TORSO_HALF, crotch, torsoColor);
  if (options.looseShirt) {
    for (let y = shoulder + 60; y < crotch + 20; y++) {
      const flare = ((y - shoulder - 60) / (TORSO_LENGTH - 40)) * 18;
      rect(image, CENTER - TORSO_HALF - flare, y, CENTER + TORSO_HALF + flare, y + 1, SHIRT);
    }
  }
  const angle = (ANGLES[options.arms] * Math.PI) / 180;
  for (const side of [-1, 1]) {
    const joint = { x: CENTER + side * (TORSO_HALF - ARM_RADIUS + (options.arms === "against" ? 0 : 6)), y: shoulder + 10 };
    const tip = { x: joint.x + side * ARM_LENGTH * Math.sin(angle), y: joint.y + ARM_LENGTH * Math.cos(angle) };
    capsule(image, joint.x, joint.y, tip.x, tip.y, ARM_RADIUS, FUR);
    if (options.sleeves) {
      const reach = SLEEVE_LENGTH / ARM_LENGTH;
      capsule(image, joint.x, joint.y, joint.x + (tip.x - joint.x) * reach, joint.y + (tip.y - joint.y) * reach, SLEEVE_RADIUS, SLEEVE);
    }
  }
  rect(image, CENTER - 40, crotch, CENTER - 3, ground, FUR);
  rect(image, CENTER + 3, crotch, CENTER + 40, ground, FUR);
  return { image, truth: { top: TOP, chin: headBottom, shoulder, crotch, ground, head: 2 * hr } };
}
