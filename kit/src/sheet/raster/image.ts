/** An RGBA image, 8 bits a channel, rows from the top. */
export interface Raster {
  width: number;
  height: number;
  data: Uint8Array;
}

/** A pixel's red, green and blue, 0 to 255. */
export type Pixel = [number, number, number];

export function blankRaster(width: number, height: number, fill: Pixel = [255, 255, 255]): Raster {
  const data = new Uint8Array(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    data[i * 4] = fill[0];
    data[i * 4 + 1] = fill[1];
    data[i * 4 + 2] = fill[2];
    data[i * 4 + 3] = 255;
  }
  return { width, height, data };
}

export function pixelAt(image: Raster, x: number, y: number): Pixel {
  const i = (y * image.width + x) * 4;
  return [image.data[i], image.data[i + 1], image.data[i + 2]];
}

export function setPixel(image: Raster, x: number, y: number, color: Pixel): void {
  if (x < 0 || y < 0 || x >= image.width || y >= image.height) return;
  const i = (y * image.width + x) * 4;
  image.data[i] = color[0];
  image.data[i + 1] = color[1];
  image.data[i + 2] = color[2];
  image.data[i + 3] = 255;
}

/** Blends `color` over the pixel by `alpha` (0 to 1). */
export function blendPixel(image: Raster, x: number, y: number, color: Pixel, alpha: number): void {
  if (x < 0 || y < 0 || x >= image.width || y >= image.height) return;
  const i = (y * image.width + x) * 4;
  for (let c = 0; c < 3; c++) image.data[i + c] = Math.round(image.data[i + c] * (1 - alpha) + color[c] * alpha);
  image.data[i + 3] = 255;
}

/** The part of `image` inside the box; parts of the box off the image take `fill`. */
export function cropRaster(image: Raster, x: number, y: number, w: number, h: number, fill: Pixel = [255, 255, 255]): Raster {
  const out = blankRaster(w, h, fill);
  for (let row = 0; row < h; row++) {
    const sy = y + row;
    if (sy < 0 || sy >= image.height) continue;
    for (let col = 0; col < w; col++) {
      const sx = x + col;
      if (sx < 0 || sx >= image.width) continue;
      const s = (sy * image.width + sx) * 4;
      const d = (row * w + col) * 4;
      out.data.set(image.data.subarray(s, s + 4), d);
    }
  }
  return out;
}

/** Every pixel made a `factor` by `factor` block: crisp edges, no smoothing. */
export function upscale(image: Raster, factor: number): Raster {
  const f = Math.max(1, Math.round(factor));
  const out = blankRaster(image.width * f, image.height * f);
  for (let y = 0; y < out.height; y++) {
    for (let x = 0; x < out.width; x++) {
      const s = (Math.floor(y / f) * image.width + Math.floor(x / f)) * 4;
      out.data.set(image.data.subarray(s, s + 4), (y * out.width + x) * 4);
    }
  }
  return out;
}

/** Copies `source` onto `target` with its top left at (x, y). */
export function blit(target: Raster, source: Raster, x: number, y: number): void {
  for (let row = 0; row < source.height; row++) {
    for (let col = 0; col < source.width; col++) {
      const tx = x + col;
      const ty = y + row;
      if (tx < 0 || ty < 0 || tx >= target.width || ty >= target.height) continue;
      const s = (row * source.width + col) * 4;
      target.data.set(source.data.subarray(s, s + 4), (ty * target.width + tx) * 4);
    }
  }
}
