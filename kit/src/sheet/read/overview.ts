import { padBox, type Box } from "../core/box.ts";
import { drawRect, drawText } from "../raster/draw.ts";
import { cropRaster, setPixel, type Raster } from "../raster/image.ts";
import type { Sheet, SheetView } from "../views/sheet.ts";
import { labelScale, MUTED, PAPER, VIEW_COLORS } from "./style.ts";

/** The background kept round a view in its own image, as a share of its larger side. */
export const VIEW_MARGIN = 0.06;

function viewLabel(view: SheetView): string {
  const facing = view.guess.facing === "three-quarter" ? "3/4" : view.guess.facing;
  return `${view.index} ${view.name} ${facing}? ${Math.round(view.guess.confidence * 100)}%`;
}

/** The whole sheet with every view's box drawn and numbered, its shadow dashed, and clutter boxed in grey. */
export function viewsOverview(sheet: Sheet): Raster {
  const image = cropRaster(sheet.image, 0, 0, sheet.image.width, sheet.image.height);
  const scale = labelScale(Math.max(image.width, image.height));
  const thick = Math.max(2, scale);
  for (const box of sheet.other) {
    drawRect(image, box.x, box.y, box.w, box.h, MUTED, 1, 4);
    drawText(image, "other", box.x, Math.max(0, box.y - 9 * scale), MUTED, scale, PAPER);
  }
  sheet.views.forEach((view, i) => {
    const color = VIEW_COLORS[i % VIEW_COLORS.length];
    drawRect(image, view.box.x, view.box.y, view.box.w, view.box.h, color, thick);
    if (view.shadow) drawRect(image, view.shadow.x, view.shadow.y, view.shadow.w, view.shadow.h, color, 1, 4);
    drawText(image, viewLabel(view), view.box.x + thick + scale, view.box.y + thick + scale, color, scale, PAPER);
  });
  return image;
}

/** The box a view's own image covers: the view and a margin of background. */
export function viewCropBox(sheet: Sheet, view: SheetView): Box {
  return padBox(view.box, Math.round(Math.max(view.box.w, view.box.h) * VIEW_MARGIN), sheet.image.width, sheet.image.height);
}

/** A view on its own: its box and margin, with every other figure and clutter painted over in the background's colour. */
export function viewCrop(sheet: Sheet, view: SheetView): Raster {
  const box = viewCropBox(sheet, view);
  const out = cropRaster(sheet.image, box.x, box.y, box.w, box.h);
  const { figure } = sheet.background;
  const inShadow = (x: number, y: number) => view.shadow !== undefined && x >= view.shadow.x && y >= view.shadow.y && x < view.shadow.x + view.shadow.w && y < view.shadow.y + view.shadow.h;
  for (let y = box.y; y < box.y + box.h; y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      const at = y * figure.width + x;
      if (figure.data[at] && !view.mask.data[at] && !inShadow(x, y)) setPixel(out, x - box.x, y - box.y, sheet.background.colorAt(x, y));
    }
  }
  return out;
}
