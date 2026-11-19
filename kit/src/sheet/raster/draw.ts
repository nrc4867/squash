import { GLYPH_HEIGHT, GLYPH_WIDTH, glyph } from "./font.ts";
import { blendPixel, setPixel, type Pixel, type Raster } from "./image.ts";

export function drawHLine(image: Raster, y: number, x0: number, x1: number, color: Pixel, dash = 0): void {
  for (let x = Math.max(0, x0); x <= Math.min(image.width - 1, x1); x++) {
    if (dash > 0 && Math.floor((x - x0) / dash) % 2 === 1) continue;
    setPixel(image, x, y, color);
  }
}

export function drawVLine(image: Raster, x: number, y0: number, y1: number, color: Pixel, dash = 0): void {
  for (let y = Math.max(0, y0); y <= Math.min(image.height - 1, y1); y++) {
    if (dash > 0 && Math.floor((y - y0) / dash) % 2 === 1) continue;
    setPixel(image, x, y, color);
  }
}

/** A rectangle's outline `thickness` pixels wide, inside the box. */
export function drawRect(image: Raster, x: number, y: number, w: number, h: number, color: Pixel, thickness = 1, dash = 0): void {
  for (let t = 0; t < thickness; t++) {
    drawHLine(image, y + t, x, x + w - 1, color, dash);
    drawHLine(image, y + h - 1 - t, x, x + w - 1, color, dash);
    drawVLine(image, x + t, y, y + h - 1, color, dash);
    drawVLine(image, x + w - 1 - t, y, y + h - 1, color, dash);
  }
}

export function fillRect(image: Raster, x: number, y: number, w: number, h: number, color: Pixel, alpha = 1): void {
  for (let row = y; row < y + h; row++) for (let col = x; col < x + w; col++) blendPixel(image, col, row, color, alpha);
}

/** A straight line from (x0, y0) to (x1, y1). */
export function drawLine(image: Raster, x0: number, y0: number, x1: number, y1: number, color: Pixel): void {
  const steps = Math.max(1, Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
  for (let i = 0; i <= steps; i++) {
    setPixel(image, Math.round(x0 + ((x1 - x0) * i) / steps), Math.round(y0 + ((y1 - y0) * i) / steps), color);
  }
}

/** The pixel size of `text` drawn at `scale`. */
export function textSize(text: string, scale: number): { width: number; height: number } {
  return { width: text.length * (GLYPH_WIDTH + 1) * scale, height: GLYPH_HEIGHT * scale };
}

/** Text with its top left at (x, y), each font pixel `scale` pixels square, on an optional backing box. */
export function drawText(image: Raster, text: string, x: number, y: number, color: Pixel, scale = 2, backing?: Pixel): void {
  if (backing) {
    const size = textSize(text, scale);
    fillRect(image, x - scale, y - scale, size.width + scale, size.height + 2 * scale, backing, 0.85);
  }
  [...text].forEach((char, i) => {
    const left = x + i * (GLYPH_WIDTH + 1) * scale;
    glyph(char).forEach((row, gy) =>
      row.forEach((on, gx) => {
        if (!on) return;
        for (let sy = 0; sy < scale; sy++) for (let sx = 0; sx < scale; sx++) setPixel(image, left + gx * scale + sx, y + gy * scale + sy, color);
      }),
    );
  });
}
