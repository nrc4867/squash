import type { Raster } from "../raster/image.ts";
import type { Anatomy } from "./anatomy.ts";
import { readFace } from "./face.ts";
import { readFeet } from "./feet.ts";
import { readHands } from "./hands.ts";
import type { Proportions, ReadingScale } from "./readings.ts";

/** The hands', feet's and face's sizes and places of one view, as shares of the head and the figure; what could not be read is listed with why. */
export function readProportions(anatomy: Anatomy, image: Raster | undefined, scale: ReadingScale): Proportions {
  const parts = [readHands(anatomy, scale, image), readFeet(anatomy, scale)];
  if (image) parts.push(readFace(image, anatomy, scale));
  const out: Proportions = { readings: {}, missing: {} };
  for (const p of parts) {
    if (p.face) out.face = p.face;
    Object.assign(out.readings, p.readings);
    Object.assign(out.missing, p.missing);
  }
  for (const name of Object.keys(out.readings)) delete out.missing[name];
  return out;
}
