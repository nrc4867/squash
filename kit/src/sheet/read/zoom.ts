import { clipBox, type Box } from "../core/box.ts";
import { clamp } from "../core/solve.ts";
import type { Anatomy } from "../measure/anatomy.ts";
import { runsAt } from "../measure/figure.ts";
import { cropRaster, upscale, type Raster } from "../raster/image.ts";
import type { Sheet } from "../views/sheet.ts";

/** Zooms aim for about this many pixels on the longer side, at 2 to 4 times. */
const ZOOM_TARGET = 600;
const ZOOM_MIN = 2;
const ZOOM_MAX = 4;
/** Margins round a region, in head heights. */
const MARGIN_HEADS = 0.08;
/** A hand's region runs this many heads above its tip and below, and this many either side. */
const HAND_ABOVE = 0.55;
const HAND_BELOW = 0.12;
const HAND_ACROSS = 0.4;
/** The feet run from the ankle, or this many heads above the ground when no ankle is known. */
const FEET_HEADS = 0.4;

export type ZoomRegion = "head" | "hands" | "feet";

export interface Zoom {
  name: string;
  /** The region on the sheet, in its pixels. */
  box: Box;
  factor: number;
  image: Raster;
}

function rowsBox(anatomy: Anatomy, from: number, to: number): Box {
  let l = Infinity;
  let r = -Infinity;
  for (let y = from; y < to; y++) {
    for (const run of runsAt(anatomy.figure, y)) {
      l = Math.min(l, run.l);
      r = Math.max(r, run.r);
    }
  }
  return { x: l, y: from, w: r - l + 1, h: to - from };
}

function padded(box: Box, margin: number, sheet: Sheet): Box {
  return clipBox({ x: box.x - margin, y: box.y - margin, w: box.w + 2 * margin, h: box.h + 2 * margin }, sheet.image.width, sheet.image.height);
}

/** The boxes on the sheet a named region covers: the head, one box a hand, or the feet. */
export function regionBoxes(anatomy: Anatomy, region: ZoomRegion, sheet: Sheet): Array<{ name: string; box: Box }> {
  const head = anatomy.headLength;
  const margin = Math.round(MARGIN_HEADS * head);
  if (region === "head") {
    const chin = anatomy.marks.chin.y;
    return [{ name: "head", box: padded(rowsBox(anatomy, anatomy.figure.top, chin + 1), margin, sheet) }];
  }
  if (region === "feet") {
    const from = anatomy.marks.ankle?.y ?? Math.round(anatomy.figure.ground - FEET_HEADS * head);
    return [{ name: "feet", box: padded(rowsBox(anatomy, from, anatomy.figure.ground), margin, sheet) }];
  }
  const hands = anatomy.pieces.filter((p) => p.kind === "arm");
  if (hands.length === 0) throw new Error("no hand stands apart from the body in this view; zoom on it with box=x,y,w,h");
  return hands.map((p) => ({
    name: p.side < 0 ? "hand-image-left" : "hand-image-right",
    box: clipBox(
      {
        x: Math.round(p.tip.x - HAND_ACROSS * head),
        y: Math.round(p.tip.y - HAND_ABOVE * head),
        w: Math.round(2 * HAND_ACROSS * head),
        h: Math.round((HAND_ABOVE + HAND_BELOW) * head),
      },
      sheet.image.width,
      sheet.image.height,
    ),
  }));
}

/** A box of the sheet scaled up 2 to 4 times with crisp edges; `factor` fixes the scale. */
export function zoomBox(sheet: Sheet, name: string, box: Box, factor?: number): Zoom {
  const clipped = clipBox(box, sheet.image.width, sheet.image.height);
  const f = factor ?? clamp(Math.round(ZOOM_TARGET / Math.max(clipped.w, clipped.h)), ZOOM_MIN, ZOOM_MAX);
  return { name, box: clipped, factor: f, image: upscale(cropRaster(sheet.image, clipped.x, clipped.y, clipped.w, clipped.h), f) };
}
