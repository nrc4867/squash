import { padBox, type Box } from "../core/box.ts";
import { drawLine, fillRect } from "../raster/draw.ts";
import { cropRaster, upscale, type Raster } from "../raster/image.ts";
import type { HairTrace } from "./hair.ts";

const LINE: [number, number, number] = [230, 40, 120];
const TIP: [number, number, number] = [220, 30, 30];
const VALLEY: [number, number, number] = [30, 90, 230];
const ORIGIN: [number, number, number] = [20, 150, 60];
/** The region is scaled up to at least this many rows so the points show. */
const MIN_ROWS = 600;
const ARM = 6;

/** The hair's region with its traced outline over it, each tip marked red, each valley blue, and the head's origin crossed in green. */
export function hairImage(image: Raster, box: Box, trace: HairTrace): Raster {
  const region = padBox(box, Math.round(box.h * 0.08), image.width, image.height);
  const factor = Math.max(1, Math.ceil(MIN_ROWS / region.h));
  const out = upscale(cropRaster(image, region.x, region.y, region.w, region.h), factor);
  const at = ([x, y]: [number, number]): [number, number] => [(x - region.x + 0.5) * factor, (y - region.y + 0.5) * factor];
  const points = trace.pixels.map(at);
  for (let i = 0; i < points.length; i++) {
    const [x0, y0] = points[i];
    const [x1, y1] = points[(i + 1) % points.length];
    drawLine(out, x0, y0, x1, y1, LINE);
  }
  const mark = (i: number, color: [number, number, number]) => {
    const [x, y] = points[i];
    fillRect(out, Math.round(x) - 4, Math.round(y) - 4, 9, 9, color);
  };
  for (const i of trace.valleys ?? []) mark(i, VALLEY);
  for (const i of trace.tips ?? []) mark(i, TIP);
  const [ox, oy] = at(trace.origin);
  drawLine(out, ox - ARM, oy, ox + ARM, oy, ORIGIN);
  drawLine(out, ox, oy - ARM, ox, oy + ARM, ORIGIN);
  return out;
}
