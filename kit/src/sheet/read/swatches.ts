import { hexToPixel } from "../core/color.ts";
import { drawRect, drawText, fillRect } from "../raster/draw.ts";
import { blankRaster, pixelAt, setPixel, type Pixel, type Raster } from "../raster/image.ts";
import type { Sheet, SheetView } from "../views/sheet.ts";
import type { SheetPalette } from "./palette.ts";
import { INK, MUTED, PAPER } from "./style.ts";

/** Each tile's map of the view is about this many pixels tall. */
const TILE_ROWS = 300;
const HEADER = 40;
const SWATCH = 28;
const COLUMNS = 4;
const GAP = 8;
/** Pixels of other colours are drawn this light. */
const FADED: Pixel = [236, 236, 238];
const FADED_INK: Pixel = [200, 200, 205];

/** One tile a colour: its swatch, hex and share over a map of the view with its pixels in colour and the rest faded. */
export function paletteImage(sheet: Sheet, view: SheetView, palette: SheetPalette): Raster {
  const { box } = view;
  const step = Math.max(1, Math.ceil(box.h / TILE_ROWS));
  const mapW = Math.ceil(box.w / step);
  const mapH = Math.ceil(box.h / step);
  const tileW = Math.max(mapW, 220);
  const tiles = palette.colors.length;
  const columns = Math.min(COLUMNS, Math.max(1, tiles));
  const rows = Math.ceil(tiles / columns);
  const out = blankRaster(columns * (tileW + GAP) + GAP, rows * (mapH + HEADER + GAP) + GAP, PAPER);
  palette.colors.forEach((color, i) => {
    const left = GAP + (i % columns) * (tileW + GAP);
    const top = GAP + Math.floor(i / columns) * (mapH + HEADER + GAP);
    fillRect(out, left, top + 4, SWATCH, SWATCH, hexToPixel(color.hex));
    drawRect(out, left, top + 4, SWATCH, SWATCH, INK);
    drawText(out, `${i + 1} ${color.hex}`, left + SWATCH + 6, top + 4, INK, 2);
    drawText(out, `${(color.share * 100).toFixed(1)}%`, left + SWATCH + 6, top + 22, MUTED, 2);
    for (let my = 0; my < mapH; my++) {
      for (let mx = 0; mx < mapW; mx++) {
        const x = box.x + mx * step;
        const y = box.y + my * step;
        const at = y * sheet.image.width + x;
        if (!view.mask.data[at]) continue;
        const label = palette.labels[at];
        setPixel(out, left + mx, top + HEADER + my, label === i ? pixelAt(sheet.image, x, y) : label < 0 ? FADED_INK : FADED);
      }
    }
    drawRect(out, left, top + HEADER, mapW, mapH, MUTED, 1, 3);
  });
  return out;
}
