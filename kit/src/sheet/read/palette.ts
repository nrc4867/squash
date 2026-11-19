import type { Box } from "../core/box.ts";
import { labDistance, pixelToHex, toLab, type Lab } from "../core/color.ts";
import { pixelAt, type Pixel } from "../raster/image.ts";
import type { Sheet, SheetView } from "../views/sheet.ts";
import { blendedMask, inkMask } from "./ink.ts";

/** Colours asked for before near ones merge. */
const DEFAULT_COLORS = 8;
/** Clusters closer than this in L*a*b* are one colour. */
const MERGE_DISTANCE = 10;
/** A colour covering less than this share of the view folds into its nearest. */
const SMALLEST_SHARE = 0.004;
/** k-means works on at most this many sampled pixels, then labels them all. */
const SAMPLE_MOST = 40000;
const ROUNDS = 12;
/** A colour's box and heights run between these quantiles of its pixels, so stray pixels do not stretch them. */
const QUANTILE = 0.02;

export interface PaletteColor {
  hex: string;
  /** The share of the view's coloured pixels (ink and blended edges left out). */
  share: number;
  pixels: number;
  /** Where most of it sits on the sheet. */
  box: Box;
}

export interface SheetPalette {
  view: number;
  colors: PaletteColor[];
  /** The ink lines left out: their mean colour and share of the figure. */
  ink: { hex: string; share: number };
  /** Each coloured pixel's colour index, -1 for ink, blended or none; `labels[y * width + x]`. */
  labels: Int16Array;
}

export interface PaletteOptions {
  /** Colours to look for before near ones merge, default 8. */
  colors?: number;
}

/** A small deterministic generator, so the same sheet always gives the same palette. */
function random(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 2 ** 32;
  };
}

function nearest(lab: Lab, centers: Lab[]): number {
  let best = 0;
  let bestDistance = Infinity;
  centers.forEach((c, i) => {
    const d = labDistance(lab, c);
    if (d < bestDistance) {
      bestDistance = d;
      best = i;
    }
  });
  return best;
}

function kMeans(samples: Lab[], k: number): Lab[] {
  const next = random(k * 7919 + samples.length);
  const centers: Lab[] = [samples[Math.floor(next() * samples.length)]];
  while (centers.length < k) {
    const weights = samples.map((s) => Math.min(...centers.map((c) => labDistance(s, c))) ** 2);
    const total = weights.reduce((a, b) => a + b, 0);
    if (total === 0) break;
    let pick = next() * total;
    let i = 0;
    while (pick > weights[i] && i < weights.length - 1) pick -= weights[i++];
    centers.push(samples[i]);
  }
  for (let round = 0; round < ROUNDS; round++) {
    const sums = centers.map(() => [0, 0, 0, 0]);
    for (const s of samples) {
      const sum = sums[nearest(s, centers)];
      sum[0] += s[0];
      sum[1] += s[1];
      sum[2] += s[2];
      sum[3]++;
    }
    sums.forEach((sum, i) => {
      if (sum[3] > 0) centers[i] = [sum[0] / sum[3], sum[1] / sum[3], sum[2] / sum[3]];
    });
  }
  return centers;
}

/** Merges centres closer than the merge distance, the larger keeping its place. */
function mergeNear(centers: Lab[], counts: number[]): Lab[] {
  const order = centers.map((_, i) => i).sort((a, b) => counts[b] - counts[a]);
  const kept: Lab[] = [];
  for (const i of order) if (counts[i] > 0 && !kept.some((c) => labDistance(c, centers[i]) < MERGE_DISTANCE)) kept.push(centers[i]);
  return kept;
}

function quantile(sorted: number[], q: number): number {
  return sorted[Math.min(sorted.length - 1, Math.max(0, Math.floor(q * (sorted.length - 1))))];
}

/** The view's colours: ink lines and blended edges left out, the rest clustered and near clusters merged, largest first. */
export function sheetPalette(sheet: Sheet, view: SheetView, options: PaletteOptions = {}): SheetPalette {
  const { image } = sheet;
  const { box, mask } = view;
  const ink = inkMask(image, mask, box);
  const blended = blendedMask(mask, ink, box);
  const places: number[] = [];
  let inkCount = 0;
  const inkSum = [0, 0, 0];
  let figureCount = 0;
  for (let y = box.y; y < box.y + box.h; y++) {
    for (let x = box.x; x < box.x + box.w; x++) {
      const at = y * image.width + x;
      if (!mask.data[at]) continue;
      figureCount++;
      if (ink[at]) {
        inkCount++;
        pixelAt(image, x, y).forEach((c, i) => (inkSum[i] += c));
      } else if (!blended[at]) {
        places.push(at);
      }
    }
  }
  const labOf = (at: number) => toLab(pixelAt(image, at % image.width, Math.floor(at / image.width)));
  const stride = Math.max(1, Math.floor(places.length / SAMPLE_MOST));
  const samples = places.filter((_, i) => i % stride === 0).map(labOf);
  let centers = samples.length ? kMeans(samples, Math.min(options.colors ?? DEFAULT_COLORS, samples.length)) : [];
  const assign = () => {
    const counts = centers.map(() => 0);
    const index = places.map((at) => {
      const i = nearest(labOf(at), centers);
      counts[i]++;
      return i;
    });
    return { counts, index };
  };
  let labelled = assign();
  centers = mergeNear(centers, labelled.counts);
  labelled = assign();
  const small = (i: number) => labelled.counts[i] < SMALLEST_SHARE * places.length;
  centers = centers.filter((_, i) => !small(i));
  labelled = assign();
  const labels = new Int16Array(image.width * image.height).fill(-1);
  const members = centers.map(() => ({ sum: [0, 0, 0], xs: [] as number[], ys: [] as number[] }));
  places.forEach((at, k) => {
    const i = labelled.index[k];
    labels[at] = i;
    const m = members[i];
    const x = at % image.width;
    const y = Math.floor(at / image.width);
    pixelAt(image, x, y).forEach((c, j) => (m.sum[j] += c));
    m.xs.push(x);
    m.ys.push(y);
  });
  const colors = members.map((m, i) => {
    const n = Math.max(1, labelled.counts[i]);
    const xs = m.xs.sort((a, b) => a - b);
    const ys = m.ys.sort((a, b) => a - b);
    const x0 = quantile(xs, QUANTILE);
    const y0 = quantile(ys, QUANTILE);
    return {
      index: i,
      hex: pixelToHex(m.sum.map((s) => Math.round(s / n)) as Pixel),
      share: Math.round((labelled.counts[i] / Math.max(1, places.length)) * 1000) / 1000,
      pixels: labelled.counts[i],
      box: { x: x0, y: y0, w: quantile(xs, 1 - QUANTILE) - x0 + 1, h: quantile(ys, 1 - QUANTILE) - y0 + 1 },
    };
  });
  const order = colors.map((c) => c.index).sort((a, b) => colors[b].pixels - colors[a].pixels);
  const rank = new Int16Array(order.length);
  order.forEach((index, r) => (rank[index] = r));
  for (let at = 0; at < labels.length; at++) if (labels[at] >= 0) labels[at] = rank[labels[at]];
  return {
    view: view.index,
    colors: order.map((i) => ({ hex: colors[i].hex, share: colors[i].share, pixels: colors[i].pixels, box: colors[i].box })),
    ink: {
      hex: pixelToHex(inkSum.map((s) => Math.round(s / Math.max(1, inkCount))) as Pixel),
      share: Math.round((inkCount / Math.max(1, figureCount)) * 1000) / 1000,
    },
    labels,
  };
}
