import { blendPixel, type Pixel, type Raster } from "../sheet/raster/image.ts";
import { drawRect } from "../sheet/raster/draw.ts";
import type { Mask } from "../sheet/core/mask.ts";
import type { Evidence, HidingLayer, Px, Source, ViewName } from "./types.ts";

/** The colour each source of a reading is drawn in: read blue, held green, a plan default amber, inferred under a layer magenta. */
export const SOURCE_COLORS: Record<Source, Pixel> = {
  read: [40, 110, 220],
  held: [30, 160, 80],
  default: [235, 150, 10],
  inferred: [200, 40, 170],
};

/** How much of the sheet shows under the evidence. */
const SHEET_SHOW = 0.45;
const LINE_WIDTH = 2;
const DASH = 6;
const MARK_RADIUS = 4;
const LAYER_COLOR: Pixel = [200, 40, 170];

/** A line `width` pixels wide, dashed for a reading the views do not show. */
function thickLine(image: Raster, [x0, y0]: Px, [x1, y1]: Px, color: Pixel, dashed: boolean): void {
  const length = Math.hypot(x1 - x0, y1 - y0);
  const steps = Math.max(1, Math.ceil(length * 2));
  for (let i = 0; i <= steps; i++) {
    const along = (i / steps) * length;
    if (dashed && Math.floor(along / DASH) % 2 === 1) continue;
    const x = x0 + ((x1 - x0) * i) / steps;
    const y = y0 + ((y1 - y0) * i) / steps;
    for (let dy = -LINE_WIDTH / 2; dy <= LINE_WIDTH / 2; dy++) for (let dx = -LINE_WIDTH / 2; dx <= LINE_WIDTH / 2; dx++) blendPixel(image, Math.round(x + dx), Math.round(y + dy), color, 1);
  }
}

function dot(image: Raster, [x, y]: Px, color: Pixel): void {
  for (let dy = -MARK_RADIUS; dy <= MARK_RADIUS; dy++) {
    for (let dx = -MARK_RADIUS; dx <= MARK_RADIUS; dx++) {
      const d = Math.hypot(dx, dy);
      if (d <= MARK_RADIUS) blendPixel(image, Math.round(x + dx), Math.round(y + dy), d > MARK_RADIUS - 1.5 ? [255, 255, 255] : color, 1);
    }
  }
}

/** A view's silhouette against a proposed shape: both grey, the view alone pale, the shape alone red. */
export function shapeImage(view: Mask, shape: Mask): Raster {
  const out: Raster = { width: view.width, height: view.height, data: new Uint8Array(view.width * view.height * 4) };
  for (let i = 0; i < view.data.length; i++) {
    const s = view.data[i];
    const b = shape.data[i];
    out.data.set([...(s && b ? SHAPE_BOTH : s ? SHAPE_VIEW : b ? SHAPE_ONLY : SHAPE_NONE), 255], i * 4);
  }
  return out;
}

const SHAPE_BOTH: Pixel = [90, 90, 90];
const SHAPE_VIEW: Pixel = [205, 205, 205];
const SHAPE_ONLY: Pixel = [220, 60, 60];
const SHAPE_NONE: Pixel = [255, 255, 255];

/**
 * A view with its evidence drawn where it was read: the sheet faded, each measurement a line in its source's colour (a plan default
 * dashed), each landmark a dot, and the hiding layers' boxes.
 */
export function evidenceImage(view: ViewName, image: Raster, evidence: Evidence, layers: readonly HidingLayer[] = []): Raster {
  const out: Raster = { width: image.width, height: image.height, data: Uint8Array.from(image.data) };
  for (let i = 0; i < out.width * out.height; i++) {
    for (let c = 0; c < 3; c++) out.data[i * 4 + c] = Math.round(255 - (255 - out.data[i * 4 + c]) * SHEET_SHOW);
  }
  for (const layer of layers) if (layer.view === view) drawRect(out, layer.box[0], layer.box[1], layer.box[2], layer.box[3], LAYER_COLOR, 1, DASH);
  for (const m of evidence.measures) {
    if (m.view !== view) continue;
    thickLine(out, m.from, m.to, SOURCE_COLORS[m.source], m.source === "default");
  }
  for (const mark of evidence.marks) if (mark.view === view) dot(out, mark.at, SOURCE_COLORS[mark.source]);
  return out;
}
