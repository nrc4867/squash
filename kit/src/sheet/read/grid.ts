import type { Anatomy } from "../measure/anatomy.ts";
import type { Measurement } from "../measure/measurement.ts";
import { drawHLine, drawLine, drawText, drawVLine, textSize } from "../raster/draw.ts";
import { blankRaster, blit, upscale, type Raster } from "../raster/image.ts";
import type { Sheet } from "../views/sheet.ts";
import { viewCrop, viewCropBox } from "./overview.ts";
import { AXIS, HEAD_LINE, INK, PAPER, STATUS_COLORS } from "./style.ts";

/** A view smaller than this many rows is scaled up by a whole factor so its labels fit. */
const GRID_MIN_ROWS = 700;
const LABEL_SCALE = 2;
const LEFT_MARGIN = 110;
const RIGHT_MARGIN = 380;
/** Head lines every this many heads. */
const HEAD_STEP = 0.5;

/**
 * The view with height lines every half head from the crown down, labelled in heads and with their row on the sheet, and every
 * landmark's line, labelled with its name, height in body units and row, coloured by status: found, unsure, estimated, given; the points a landmark was read from are crossed.
 */
export function gridImage(sheet: Sheet, anatomy: Anatomy, measurement: Measurement): Raster {
  const box = viewCropBox(sheet, anatomy.view);
  const factor = Math.max(1, Math.ceil(GRID_MIN_ROWS / box.h));
  const view = upscale(viewCrop(sheet, anatomy.view), factor);
  const out = blankRaster(view.width + LEFT_MARGIN + RIGHT_MARGIN, view.height, PAPER);
  blit(out, view, LEFT_MARGIN, 0);
  const toY = (y: number) => Math.round((y - box.y + 0.5) * factor);
  const right = LEFT_MARGIN + view.width;
  drawVLine(out, LEFT_MARGIN + Math.round((measurement.axis - box.x + 0.5) * factor), 0, out.height - 1, AXIS, 6);
  const crown = anatomy.marks.crown.y;
  const lineHeight = textSize("0", LABEL_SCALE).height;
  for (let k = 0; ; k++) {
    const heads = k * HEAD_STEP;
    const y = Math.round(crown + heads * anatomy.headLength);
    if (y > anatomy.figure.ground + anatomy.headLength * 0.25) break;
    const py = toY(y);
    if (py >= out.height) break;
    drawHLine(out, py, LEFT_MARGIN, right, HEAD_LINE, 4);
    drawText(out, `${heads}H`, 4, py - lineHeight - 1, HEAD_LINE, LABEL_SCALE);
    drawText(out, `Y${y}`, 4, py + 2, HEAD_LINE, 1);
  }
  const placed: number[] = [];
  const entries = Object.entries(measurement.landmarks).sort((a, b) => a[1].y - b[1].y);
  for (const [name, mark] of entries) {
    const py = toY(mark.y);
    const color = STATUS_COLORS[mark.status];
    drawHLine(out, Math.min(out.height - 1, py), LEFT_MARGIN, right + 8, color);
    if (mark.x !== undefined) drawVLine(out, LEFT_MARGIN + Math.round((mark.x - box.x + 0.5) * factor), py - 6, py + 6, color);
    for (const point of mark.points ?? []) {
      const px = LEFT_MARGIN + Math.round((point.x - box.x + 0.5) * factor);
      const pointY = toY(point.y);
      drawLine(out, px - 6, pointY - 6, px + 6, pointY + 6, color);
      drawLine(out, px - 6, pointY + 6, px + 6, pointY - 6, color);
    }
    let labelY = Math.max(0, py - Math.round(lineHeight / 2));
    while (placed.some((p) => Math.abs(p - labelY) < lineHeight + 2)) labelY += lineHeight + 2;
    placed.push(labelY);
    drawText(out, `${name} ${mark.height.toFixed(3)} Y${mark.y}${mark.status === "found" || mark.status === "given" ? "" : ` ${mark.status}`}`, right + 12, labelY, color, LABEL_SCALE);
  }
  drawText(out, `${measurement.heads.toFixed(2)} HEADS`, 4, out.height - textSize("0", 1).height - 3, INK, 1);
  return out;
}
