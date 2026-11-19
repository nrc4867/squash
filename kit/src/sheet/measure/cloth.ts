import { channelDistance } from "../core/color.ts";
import type { Mask } from "../core/mask.ts";
import { pixelAt, type Pixel, type Raster } from "../raster/image.ts";
import type { Figure } from "./figure.ts";
import { figureHeight } from "./figure.ts";
import type { PixelMark } from "./landmark.ts";

/** A row's colour is read over this many rows each side of it. */
const WINDOW = 4;
/** The colour the rows above and below an edge differ by, at least, in the largest channel. */
const EDGE_STEP = 45;
/** Edges closer than this share of the figure's height are one. */
const EDGE_APART = 0.008;
/** A landmark within this share of the figure's height of an edge sits on it. */
const ON_EDGE = 0.02;
/** The landmarks the body's own shape sets, which clothes can hide behind an edge. */
const HIDDEN_BY_CLOTHES = ["waist", "hip", "ankle", "knee"] as const;

/** A row where the colour across the body changes: a hem, a waistband, a cuff, a sock's top. */
export interface ClothEdge {
  y: number;
  /** How far the colour changes, the largest channel, 0 to 255. */
  step: number;
}

function rowColor(image: Raster, core: Mask, figure: Figure, y: number): Pixel | undefined {
  const values: number[][] = [[], [], []];
  for (let x = figure.box.x; x < figure.box.x + figure.box.w; x++) {
    if (!core.data[y * core.width + x]) continue;
    const p = pixelAt(image, x, y);
    for (let c = 0; c < 3; c++) values[c].push(p[c]);
  }
  if (values[0].length === 0) return undefined;
  return values.map((v) => v.sort((a, b) => a - b)[v.length >> 1]) as Pixel;
}

/** The rows from `from` to the ground where the body core's median colour jumps. */
export function clothEdges(image: Raster, core: Mask, figure: Figure, from: number): ClothEdge[] {
  const colors = new Map<number, Pixel>();
  for (let y = from; y < figure.ground; y++) {
    const c = rowColor(image, core, figure, y);
    if (c) colors.set(y, c);
  }
  const average = (rows: number[]): Pixel | undefined => {
    const found = rows.map((y) => colors.get(y)).filter((c): c is Pixel => c !== undefined);
    if (found.length < Math.ceil(rows.length / 2)) return undefined;
    return [0, 1, 2].map((c) => found.reduce((s, p) => s + p[c], 0) / found.length) as Pixel;
  };
  const steps: ClothEdge[] = [];
  for (let y = from + WINDOW; y < figure.ground - WINDOW; y++) {
    const above = average(Array.from({ length: WINDOW }, (_, i) => y - 1 - i));
    const below = average(Array.from({ length: WINDOW }, (_, i) => y + i));
    if (!above || !below) continue;
    const step = channelDistance(above.map(Math.round) as Pixel, below.map(Math.round) as Pixel);
    if (step >= EDGE_STEP) steps.push({ y, step });
  }
  const apart = Math.max(2, Math.round(EDGE_APART * figureHeight(figure)));
  const edges: ClothEdge[] = [];
  for (const s of steps) {
    const last = edges[edges.length - 1];
    if (last && s.y - last.y <= apart) {
      if (s.step > last.step) edges[edges.length - 1] = s;
    } else {
      edges.push(s);
    }
  }
  return edges;
}

/**
 * Landmarks read from the silhouette that sit on a clothing edge become unsure: the body's own waist, hip, knee or ankle may lie under
 * the hem, waistband or cuff the silhouette turned at. Estimated and given marks stay as they are.
 */
export function markOnEdges(marks: Record<string, PixelMark>, edges: readonly ClothEdge[], figure: Figure): Record<string, PixelMark> {
  const reach = Math.max(2, Math.round(ON_EDGE * figureHeight(figure)));
  const out = { ...marks };
  for (const name of HIDDEN_BY_CLOTHES) {
    const mark = out[name];
    if (!mark || mark.status !== "found") continue;
    const edge = edges.find((e) => Math.abs(e.y - mark.y) <= reach);
    if (!edge) continue;
    out[name] = { ...mark, status: "unsure", reason: `on a clothing edge: the colour across the body changes at y ${edge.y} (a hem, waistband, cuff or sock), so the body's ${name} may lie under it` };
  }
  return out;
}
