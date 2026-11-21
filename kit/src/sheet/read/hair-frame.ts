// Where a hair outline's frame lies on a sheet's view: the head's chin, its crown and its eyes, as `sheet measure` reads them, so a hair
// outline is traced with no calibration given. See docs/hld/parts.md, Hair locks.

import type { Measurement } from "../measure/measurement.ts";

export interface HairFrame {
  /** The chin under the face's middle, the outline's origin, in the sheet's pixels. */
  chin: [number, number];
  /** The row the skull tops out on: where the measure reads the crown. */
  crown: number;
  /** The top row of the eyes, the hair's colours read above it; absent with no eyes read. */
  eyeTop?: number;
  /** How high the eyes' middle stands over the chin, in head lengths (chin to crown 1); absent with no eyes read. */
  eyes?: number;
}

/** The frame a view's measurement gives a hair outline. */
export function hairFrame(m: Measurement): HairFrame {
  const chinY = m.landmarks.chin?.y ?? m.view.box.y + m.headPixels;
  const crown = m.landmarks.crown?.y ?? chinY - m.headPixels;
  const chin: [number, number] = [m.face?.axis ?? m.axis, chinY];
  const eyes = m.face?.eyes ?? [];
  if (eyes.length === 0) return { chin, crown };
  const open = eyes.filter((e) => !e.coveredTop);
  const read = open.length > 0 ? open : eyes;
  // An eye whose top the fringe covers is read up from its bottom, as tall as it is wide where its box is cut shorter.
  const middle = read.reduce((s, e) => s + e.box.y + e.box.h - Math.max(e.box.h, e.box.w) / 2, 0) / read.length;
  const length = Math.max(1, chinY - crown);
  return { chin, crown, eyeTop: Math.min(...eyes.map((e) => e.box.y)), eyes: (chinY - middle) / length };
}
