import { drawLine, fillRect } from "../raster/draw.ts";
import { upscale, type Raster } from "../raster/image.ts";
import type { Sheet, SheetView } from "../views/sheet.ts";
import { viewCrop, viewCropBox } from "./overview.ts";
import type { Trace } from "./trace.ts";

const LINE = [230, 40, 120] as const;
const POINT = [20, 20, 28] as const;
const SOCKET = [20, 150, 230] as const;
/** The socket's cross reaches this many pixels each way. */
const SOCKET_ARM = 6;
/** The view is scaled up to at least this many rows so the points show. */
const MIN_ROWS = 700;

/** The view with a traced outline drawn over it, its points marked and a foot's ankle or a hand's wrist crossed in blue. */
export function traceImage(sheet: Sheet, view: SheetView, trace: Trace): Raster {
  const box = viewCropBox(sheet, view);
  const factor = Math.max(1, Math.ceil(MIN_ROWS / box.h));
  const out = upscale(viewCrop(sheet, view), factor);
  const at = ([x, y]: [number, number]): [number, number] => [(x - box.x + 0.5) * factor, (y - box.y + 0.5) * factor];
  const points = trace.pixels.map(at);
  const segments = trace.closed ? points.length : points.length - 1;
  for (let i = 0; i < segments; i++) {
    const [x0, y0] = points[i];
    const [x1, y1] = points[(i + 1) % points.length];
    drawLine(out, x0, y0, x1, y1, [...LINE]);
  }
  for (const [x, y] of points) fillRect(out, Math.round(x) - 2, Math.round(y) - 2, 5, 5, [...POINT]);
  if (trace.socket) {
    const sx = (trace.socket[0] - box.x) * factor;
    const sy = (trace.socket[1] - box.y) * factor;
    drawLine(out, sx - SOCKET_ARM, sy, sx + SOCKET_ARM, sy, [...SOCKET]);
    drawLine(out, sx, sy - SOCKET_ARM, sx, sy + SOCKET_ARM, [...SOCKET]);
  }
  return out;
}
