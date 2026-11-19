import type { Anatomy } from "../measure/anatomy.ts";
import type { Measurement } from "../measure/measurement.ts";
import { drawHLine, drawLine, drawRect, drawText } from "../raster/draw.ts";
import { GLYPH_WIDTH } from "../raster/font.ts";
import { blankRaster, blit, cropRaster, upscale, type Raster } from "../raster/image.ts";
import type { Sheet } from "../views/sheet.ts";
import { viewCrop, viewCropBox } from "./overview.ts";
import { AXIS, INK, PAPER, STATUS_COLORS } from "./style.ts";

/** The landmarks a check tile is drawn for, top to bottom. */
const CHECKED = ["earTip", "crown", "chin", "neck", "shoulder", "armpit", "waist", "hip", "crotch", "handTip", "knee", "ankle", "tailTop", "tailBottom"];
/** A tile shows this many heads above and below its landmark. */
const TILE_HEADS = 0.6;
/** Tiles are scaled up by a whole factor until they are at least this wide. */
const TILE_MIN_WIDTH = 360;
const COLUMNS = 3;
const GAP = 8;
const HEADER = 14;
const NOTE_LINES = 4;
const LINE = 9;
const BORDER = 2;

/** `text` broken into lines of at most `columns` characters at word breaks. */
function wrap(text: string, columns: number): string[] {
  const lines: string[] = [];
  let line = "";
  for (const word of text.split(" ")) {
    if (line && line.length + 1 + word.length > columns) {
      lines.push(line);
      line = word;
    } else {
      line = line ? `${line} ${word}` : word;
    }
  }
  if (line) lines.push(line);
  return lines;
}

/**
 * One close-up a landmark, side by side: the view's rows a little above and below the landmark, its line drawn across (in its status
 * colour: green found, magenta unsure, orange estimated, blue given), the outline points it was read from crossed, and under each the
 * landmark's name, status and reason. A builder confirms each landmark in one look.
 */
export function checkImage(sheet: Sheet, anatomy: Anatomy, measurement: Measurement): Raster {
  const box = viewCropBox(sheet, anatomy.view);
  const base = viewCrop(sheet, anatomy.view);
  const factor = Math.max(1, Math.ceil(TILE_MIN_WIDTH / base.width));
  const names = CHECKED.filter((name) => measurement.landmarks[name] !== undefined);
  const half = Math.max(8, Math.round(TILE_HEADS * anatomy.headLength));
  const tileW = base.width * factor;
  const tileH = HEADER + 2 * half * factor + NOTE_LINES * LINE + 2;
  const rows = Math.ceil(names.length / COLUMNS);
  const out = blankRaster(COLUMNS * tileW + (COLUMNS + 1) * GAP, rows * (tileH + GAP) + GAP, PAPER);
  const columns = Math.max(10, Math.floor(tileW / (GLYPH_WIDTH + 1)));
  names.forEach((name, i) => {
    const mark = measurement.landmarks[name];
    const color = STATUS_COLORS[mark.status];
    const left = GAP + (i % COLUMNS) * (tileW + GAP);
    const top = GAP + Math.floor(i / COLUMNS) * (tileH + GAP);
    const from = mark.y - box.y - half;
    const view = upscale(cropRaster(base, 0, from, base.width, 2 * half, [255, 255, 255]), factor);
    blit(out, view, left, top + HEADER);
    const toX = (x: number) => left + Math.round((x - box.x + 0.5) * factor);
    const toY = (y: number) => top + HEADER + Math.round((y - box.y - from + 0.5) * factor);
    drawHLine(out, top + HEADER + half * factor, left, left + tileW - 1, color);
    drawHLine(out, top + HEADER + half * factor + 1, left, left + tileW - 1, color);
    const axis = toX(anatomy.figure.axis);
    drawLine(out, axis, top + HEADER, axis, top + HEADER + 2 * half * factor - 1, AXIS);
    for (const point of [...(mark.points ?? []), ...(mark.x !== undefined ? [{ x: mark.x, y: mark.y }] : [])]) {
      drawLine(out, toX(point.x) - 6, toY(point.y) - 6, toX(point.x) + 6, toY(point.y) + 6, color);
      drawLine(out, toX(point.x) - 6, toY(point.y) + 6, toX(point.x) + 6, toY(point.y) - 6, color);
    }
    drawRect(out, left, top, tileW, tileH, color, mark.status === "found" ? 1 : BORDER);
    drawText(out, `${name} ${mark.status} Y${mark.y} ${mark.height.toFixed(3)}`, left + 3, top + 3, color, 1);
    const note = mark.reason ?? (mark.status === "found" ? "" : mark.status);
    wrap(note, columns).slice(0, NOTE_LINES).forEach((line, k) => drawText(out, line, left + 3, top + HEADER + 2 * half * factor + 2 + k * LINE, INK, 1));
  });
  return out;
}
