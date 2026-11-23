import { readFileSync } from "node:fs";
import type { Point } from "../geom/point.ts";
import type { BonePose, RecipeFigure, RecipeFigureStroke } from "../schema/recipe.ts";

/**
 * A figure outline as drawn or traced: open strokes for the front view (the character's left half, x from the body's middle toward its
 * left) and the side view (x forward), y up from the ground, and optionally the top view (the left half from above, y forward from the
 * root part's origin), each running with the figure on its right; head, hands and feet left out.
 */
export interface FigureOutline {
  format: "squash-figure-outline/1";
  front: FigureOutlineStroke[];
  side: FigureOutlineStroke[];
  /** Strokes seen from above, which shape the parts lying level: a four-legged trunk, a tail. */
  top?: FigureOutlineStroke[];
}

/** One stroke: a smooth curve through `points`, sharp at `corners`; `mirrored` runs an end on the axis on into its mirror image. */
export interface FigureOutlineStroke {
  id: string;
  points: Point[];
  corners?: number[];
  mirrored?: boolean;
}

export interface FigureOptions {
  /** How high above the ground the root part's origin sits, in body units. */
  rootHeight: number;
  /** The pose the outline is drawn in; the recipe's display pose when left out, [] when drawn at rest. */
  pose?: BonePose[];
  /** Correction rounds on the built body's silhouette; 3 when left out. */
  rounds?: number;
}

function stroke(s: FigureOutlineStroke): RecipeFigureStroke {
  return {
    id: s.id,
    through: s.points.map((p) => [...p]),
    ...(s.corners?.length ? { corners: [...s.corners] } : {}),
    ...(s.mirrored ? { mirrored: true } : {}),
  };
}

/** A recipe's `figure` from an outline: the parts `figurePart` makes take their widths, joins and roots from it. */
export function figure(outline: FigureOutline, options: FigureOptions): RecipeFigure {
  return {
    rootHeight: options.rootHeight,
    front: outline.front.map(stroke),
    side: outline.side.map(stroke),
    ...(outline.top?.length ? { top: outline.top.map(stroke) } : {}),
    ...(options.pose ? { pose: options.pose } : {}),
    ...(options.rounds !== undefined ? { rounds: options.rounds } : {}),
  };
}

/** Reads a figure outline file, as `tools/recipe.sh figure` writes one. */
export function readFigureOutline(path: string): FigureOutline {
  const outline = JSON.parse(readFileSync(path, "utf8")) as FigureOutline;
  if (outline.format !== "squash-figure-outline/1") throw new Error(`${path}: not a figure outline (format squash-figure-outline/1)`);
  return outline;
}
