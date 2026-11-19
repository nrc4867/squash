import { channelDistance, hexToPixel } from "./core/color.ts";
import { blankMask, type Mask } from "./core/mask.ts";
import { clamp, leastSquares, median } from "./core/solve.ts";
import { pixelAt, type Pixel, type Raster } from "./raster/image.ts";

/** The pixels round the image's edge that sample the background. */
const BORDER_BAND = 3;
/** Colours a border pixel may stray from the dominant border colour and still sample the background. */
const SAMPLE_TOLERANCE = 40;
/** The least and most a pixel may stray from the background model and still be background, 0 to 255 a channel. */
const MIN_TOLERANCE = 14;
const MAX_TOLERANCE = 60;
/** How many robust deviations of the border's noise the tolerance allows, and its floor above that. */
const NOISE_SPREAD = 5;
const NOISE_FLOOR = 6;
/** Median absolute deviation to standard deviation. */
const MAD_SCALE = 1.4826;
/** Quantising step for the border colour histogram. */
const HISTOGRAM_SHIFT = 4;
/** Fits of the border model, each on the border pixels near the one before: a gradient's far end joins on the second. */
const FIT_ROUNDS = 3;

/** The background of a sheet: a smooth colour model fitted to its border, and what stands on it. */
export interface Background {
  /** The dominant border colour, or the one given. */
  ground: Pixel;
  /** How far a pixel may stray from the model, largest channel difference, and still be background. */
  tolerance: number;
  /** The background's colour at a pixel: flat, or a gentle gradient fitted to the border. */
  colorAt(x: number, y: number): Pixel;
  /** 1 where something stands on the background: everything not reached by a flood fill of background from the edges. */
  figure: Mask;
}

export interface BackgroundOptions {
  /** The background colour, "#rrggbb": a flat background of that colour instead of one read from the border. */
  ground?: string;
  /** A fixed tolerance, 0 to 255 a channel, instead of one read from the border's noise. */
  tolerance?: number;
  /** Pixels inside enclosed background, such as the gap a curled tail closes against a leg: background floods from them too. */
  holes?: ReadonlyArray<[number, number]>;
}

function borderPixels(image: Raster): Array<[number, number]> {
  const out: Array<[number, number]> = [];
  const band = Math.min(BORDER_BAND, Math.floor(Math.min(image.width, image.height) / 4) || 1);
  for (let y = 0; y < image.height; y++) {
    for (let x = 0; x < image.width; x++) {
      const onBorder = x < band || y < band || x >= image.width - band || y >= image.height - band;
      if (onBorder) out.push([x, y]);
      else if (x === band) x = image.width - band - 1;
    }
  }
  return out;
}

function dominantColor(image: Raster, places: ReadonlyArray<[number, number]>): Pixel {
  const counts = new Map<number, { n: number; sum: [number, number, number] }>();
  for (const [x, y] of places) {
    const p = pixelAt(image, x, y);
    const key = ((p[0] >> HISTOGRAM_SHIFT) << 16) | ((p[1] >> HISTOGRAM_SHIFT) << 8) | (p[2] >> HISTOGRAM_SHIFT);
    const bin = counts.get(key) ?? { n: 0, sum: [0, 0, 0] };
    bin.n++;
    bin.sum[0] += p[0];
    bin.sum[1] += p[1];
    bin.sum[2] += p[2];
    counts.set(key, bin);
  }
  let best = { n: 0, sum: [255, 255, 255] as [number, number, number] };
  for (const bin of counts.values()) if (bin.n > best.n) best = bin;
  return best.sum.map((s) => Math.round(s / Math.max(1, best.n))) as Pixel;
}

function features(x: number, y: number, width: number, height: number): number[] {
  const u = x / Math.max(1, width - 1) - 0.5;
  const v = y / Math.max(1, height - 1) - 0.5;
  return [1, u, v, u * v, u * u, v * v];
}

/** A per-channel quadratic in x and y fitted to the samples: a flat colour or a gentle gradient. */
function fitModel(image: Raster, samples: ReadonlyArray<[number, number]>): (x: number, y: number) => Pixel {
  const rows = samples.map(([x, y]) => features(x, y, image.width, image.height));
  const weights = [0, 1, 2].map((c) => leastSquares(rows, samples.map(([x, y]) => pixelAt(image, x, y)[c])));
  return (x, y) => {
    const f = features(x, y, image.width, image.height);
    return weights.map((w) => clamp(Math.round(w.reduce((sum, wi, i) => sum + wi * f[i], 0)), 0, 255)) as Pixel;
  };
}

/** Floods background in from every edge pixel near the model; what the flood never reaches is figure. */
function floodFigure(image: Raster, colorAt: (x: number, y: number) => Pixel, tolerance: number, holes: ReadonlyArray<[number, number]>): Mask {
  const { width, height } = image;
  const near = new Uint8Array(width * height);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) near[y * width + x] = channelDistance(pixelAt(image, x, y), colorAt(x, y)) <= tolerance ? 1 : 0;
  }
  const reached = new Uint8Array(width * height);
  const stack: number[] = [];
  const seed = (at: number) => {
    if (near[at] && !reached[at]) {
      reached[at] = 1;
      stack.push(at);
    }
  };
  for (let x = 0; x < width; x++) {
    seed(x);
    seed((height - 1) * width + x);
  }
  for (let y = 0; y < height; y++) {
    seed(y * width);
    seed(y * width + width - 1);
  }
  for (const [x, y] of holes) if (x >= 0 && y >= 0 && x < width && y < height) seed(Math.round(y) * width + Math.round(x));
  while (stack.length > 0) {
    const at = stack.pop()!;
    const x = at % width;
    if (x > 0) seed(at - 1);
    if (x < width - 1) seed(at + 1);
    if (at >= width) seed(at - width);
    if (at < width * (height - 1)) seed(at + width);
  }
  const figure = blankMask(width, height);
  for (let i = 0; i < reached.length; i++) figure.data[i] = reached[i] ? 0 : 1;
  return figure;
}

/** Keys a sheet's background: by the dominant border colour and a gradient fitted to the border, or by `ground=`. */
export function keyBackground(image: Raster, options: BackgroundOptions = {}): Background {
  const border = borderPixels(image);
  let ground: Pixel;
  let colorAt: (x: number, y: number) => Pixel;
  let samples: Array<[number, number]>;
  if (options.ground) {
    ground = hexToPixel(options.ground);
    colorAt = () => ground;
    samples = border.filter(([x, y]) => channelDistance(pixelAt(image, x, y), ground) <= SAMPLE_TOLERANCE);
  } else {
    ground = dominantColor(image, border);
    const flat = ground;
    colorAt = () => flat;
    samples = border;
    for (let round = 0; round < FIT_ROUNDS; round++) {
      const model = colorAt;
      samples = border.filter(([x, y]) => channelDistance(pixelAt(image, x, y), model(x, y)) <= SAMPLE_TOLERANCE);
      if (samples.length < features(0, 0, 1, 1).length * 4) break;
      colorAt = fitModel(image, samples);
    }
  }
  const residuals = samples.map(([x, y]) => channelDistance(pixelAt(image, x, y), colorAt(x, y)));
  const noise = median(residuals) * MAD_SCALE;
  const tolerance = options.tolerance ?? clamp(Math.round(NOISE_SPREAD * noise + NOISE_FLOOR), MIN_TOLERANCE, MAX_TOLERANCE);
  return { ground, tolerance, colorAt, figure: floodFigure(image, colorAt, tolerance, options.holes ?? []) };
}

/**
 * The figure without background-coloured pixels reachable from outside it: background a cast shadow closed off, such as the gap
 * between the feet, opens again once the shadow is split away.
 */
export function reopenBackground(image: Raster, background: Background, figure: Mask): Mask {
  const { width, height } = figure;
  const out = blankMask(width, height);
  out.data.set(figure.data);
  const near = (at: number) => channelDistance(pixelAt(image, at % width, Math.floor(at / width)), background.colorAt(at % width, Math.floor(at / width))) <= background.tolerance;
  const stack: number[] = [];
  const open = (at: number) => {
    if (out.data[at] && near(at)) {
      out.data[at] = 0;
      stack.push(at);
    }
  };
  for (let at = 0; at < out.data.length; at++) {
    if (!out.data[at] || !near(at)) continue;
    const x = at % width;
    const outside = (n: number) => !figure.data[n];
    if ((x > 0 && outside(at - 1)) || (x < width - 1 && outside(at + 1)) || (at >= width && outside(at - width)) || (at < width * (height - 1) && outside(at + width))) open(at);
  }
  while (stack.length > 0) {
    const at = stack.pop()!;
    const x = at % width;
    if (x > 0) open(at - 1);
    if (x < width - 1) open(at + 1);
    if (at >= width) open(at - width);
    if (at < width * (height - 1)) open(at + width);
  }
  return out;
}
