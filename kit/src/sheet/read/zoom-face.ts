import type { Box } from "../core/box.ts";
import type { FaceFinding } from "../measure/face/types.ts";
import { drawLine, drawRect, drawText } from "../raster/draw.ts";
import type { Pixel } from "../raster/image.ts";
import { AXIS, HEAD_LINE } from "./style.ts";
import type { Zoom } from "./zoom.ts";

const EYE_COLOR: Pixel = [20, 150, 60];
const NOSE_COLOR: Pixel = [230, 120, 0];
const MOUTH_COLOR: Pixel = [210, 30, 60];
const MUZZLE_COLOR: Pixel = [0, 160, 170];
const LABEL_SCALE = 2;

function scaled(zoom: Zoom, box: Box): Box {
  return { x: Math.round((box.x - zoom.box.x) * zoom.factor), y: Math.round((box.y - zoom.box.y) * zoom.factor), w: Math.round(box.w * zoom.factor), h: Math.round(box.h * zoom.factor) };
}

function framed(zoom: Zoom, box: Box, color: Pixel, label: string): void {
  const b = scaled(zoom, box);
  drawRect(zoom.image, b.x, b.y, b.w, b.h, color, 2);
  drawText(zoom.image, label, b.x + 2, Math.max(0, b.y - 8 * LABEL_SCALE - 2), color, LABEL_SCALE, [255, 255, 255]);
}

/** Draws the face check over a head zoom: the skin area's outline, the middle line, each eye's box, the muzzle, the nose and the mouth. */
export function drawFace(zoom: Zoom, face: FaceFinding): void {
  const at = (x: number, y: number) => [(x - zoom.box.x) * zoom.factor, (y - zoom.box.y) * zoom.factor] as const;
  let previous: { l: readonly [number, number]; r: readonly [number, number] } | undefined;
  for (const row of face.region.rows) {
    const l = at(row.l, row.y);
    const r = at(row.r, row.y);
    if (previous) {
      drawLine(zoom.image, previous.l[0], previous.l[1], l[0], l[1], HEAD_LINE);
      drawLine(zoom.image, previous.r[0], previous.r[1], r[0], r[1], HEAD_LINE);
    }
    previous = { l, r };
  }
  const top = at(face.axis, face.region.box.y);
  const bottom = at(face.axis, face.region.box.y + face.region.box.h);
  drawLine(zoom.image, top[0], top[1], bottom[0], bottom[1], AXIS);
  face.eyes.forEach((e) => framed(zoom, e.box, EYE_COLOR, e.side === "image-left" ? "eye L" : "eye R"));
  if (face.muzzle) framed(zoom, face.muzzle, MUZZLE_COLOR, "muzzle");
  if (face.nose) framed(zoom, face.nose, NOSE_COLOR, "nose");
  if (face.mouth) framed(zoom, face.mouth, MOUTH_COLOR, "mouth");
}
