import { boxGap, unionBox, verticalOverlap, type Box } from "../core/box.ts";
import { edgePixels, pixelDistance, type Component } from "../core/mask.ts";

/** A piece nearer a figure than this share of the figure's height belongs to it: a detached tail, ear or floating flower. */
const ATTACH_REACH = 0.08;
/** A figure-sized piece nearer than this share of a figure's height, and at most this share of its area, is a detached part of it. */
const CLOSE_REACH = 0.015;
const CLOSE_MOST = 0.2;
/** A figure is at least this share of the tallest piece's height and of the largest piece's area. */
const FIGURE_HEIGHT = 0.25;
const FIGURE_AREA = 0.03;
/** Small pieces in a cluster of at least this many are swatches, text or dots, and join a figure only where they all but touch it (CLOSE_REACH). */
const CLUTTER_COUNT = 3;
/** Pieces of clutter closer than this share of the sheet's larger side are one item, as letters make a word. */
const CLUTTER_REACH = 0.02;
/** A shadow piece's top lies within this share of its figure's height of the figure's bottom. */
const SHADOW_REACH = 0.15;

export interface FigureGroup {
  /** The pieces of one figure, its largest first. */
  pieces: Component[];
  /** Separate shade pieces under it: its cast shadow. */
  shadows: Component[];
  box: Box;
}

export interface Grouping {
  figures: FigureGroup[];
  /** Labels, text, swatches, arrows and specks: boxes of clutter that are no figure. */
  other: Box[];
}

function nearestFigure(
  piece: Component,
  figures: FigureGroup[],
  edges: Map<number, Int32Array>,
  width: number,
  share: number,
): { figure: FigureGroup; distance: number } | undefined {
  let best: { figure: FigureGroup; distance: number } | undefined;
  for (const figure of figures) {
    const reach = Math.max(2, share * figure.box.h);
    if (boxGap(piece.box, figure.box) > reach) continue;
    let distance = Infinity;
    for (const member of figure.pieces) distance = Math.min(distance, pixelDistance(edges.get(piece.label)!, edges.get(member.label)!, width, 1));
    if (distance <= reach && (!best || distance < best.distance)) best = { figure, distance };
  }
  return best;
}

function shadowOwner(shadow: Component, figures: FigureGroup[]): FigureGroup | undefined {
  return figures.find((figure) => {
    const bottom = figure.box.y + figure.box.h;
    const sharesColumns = Math.min(shadow.box.x + shadow.box.w, figure.box.x + figure.box.w) > Math.max(shadow.box.x, figure.box.x);
    return sharesColumns && Math.abs(shadow.box.y - bottom) <= SHADOW_REACH * figure.box.h + 1;
  });
}

function clusterBoxes(boxes: Box[], reach: number): Box[] {
  const items = [...boxes];
  let merged = true;
  while (merged) {
    merged = false;
    for (let i = 0; i < items.length && !merged; i++) {
      for (let j = i + 1; j < items.length; j++) {
        if (boxGap(items[i], items[j]) > reach) continue;
        items[i] = unionBox(items[i], items[j]);
        items.splice(j, 1);
        merged = true;
        break;
      }
    }
  }
  return items;
}

/** Small pieces lying in a cluster of at least this many: swatches, letters or dots, never part of a figure. */
function clusteredSmall(small: Component[], reach: number): Set<number> {
  const groups: Array<{ box: Box; members: Component[] }> = small.map((p) => ({ box: p.box, members: [p] }));
  let merged = true;
  while (merged) {
    merged = false;
    for (let i = 0; i < groups.length && !merged; i++) {
      for (let j = i + 1; j < groups.length; j++) {
        if (boxGap(groups[i].box, groups[j].box) > reach) continue;
        groups[i] = { box: unionBox(groups[i].box, groups[j].box), members: [...groups[i].members, ...groups[j].members] };
        groups.splice(j, 1);
        merged = true;
        break;
      }
    }
  }
  return new Set(groups.filter((g) => g.members.length >= CLUTTER_COUNT).flatMap((g) => g.members.map((m) => m.label)));
}

/**
 * Groups a sheet's pieces into figures. Pieces tall and large enough start a figure of their own unless they nearly touch a much
 * larger one; smaller pieces close to a figure join it, unless they sit in a cluster of small pieces (swatches, text). Pieces that are
 * mostly shade join the figure above them as its shadow. What is left is clutter, clustered into boxes.
 */
export function groupPieces(pieces: Component[], shade: (piece: Component) => boolean, labels: Int32Array, width: number, height: number): Grouping {
  const solid = pieces.filter((piece) => !shade(piece));
  const shadows = pieces.filter((piece) => shade(piece));
  const tallest = Math.max(1, ...solid.map((p) => p.box.h));
  const largest = Math.max(1, ...solid.map((p) => p.area));
  const figureSized = (p: Component) => p.box.h >= FIGURE_HEIGHT * tallest && p.area >= FIGURE_AREA * largest;
  const reach = CLUTTER_REACH * Math.max(width, height);
  const swarm = clusteredSmall(solid.filter((p) => !figureSized(p)), reach);
  const edges = new Map(solid.map((piece) => [piece.label, edgePixels(piece, width, labels)]));
  const figures: FigureGroup[] = [];
  const clutter: Box[] = [];
  const join = (figure: FigureGroup, piece: Component) => {
    figure.pieces.push(piece);
    figure.box = unionBox(figure.box, piece.box);
  };
  for (const piece of solid) {
    if (figureSized(piece)) {
      const near = nearestFigure(piece, figures, edges, width, CLOSE_REACH);
      if (near && piece.area <= CLOSE_MOST * near.figure.pieces[0].area) join(near.figure, piece);
      else figures.push({ pieces: [piece], shadows: [], box: piece.box });
      continue;
    }
    const near = nearestFigure(piece, figures, edges, width, swarm.has(piece.label) ? CLOSE_REACH : ATTACH_REACH);
    if (near) join(near.figure, piece);
    else clutter.push(piece.box);
  }
  for (const shadow of shadows) {
    const owner = shadowOwner(shadow, figures);
    if (owner) owner.shadows.push(shadow);
    else clutter.push(shadow.box);
  }
  return { figures, other: clusterBoxes(clutter, reach) };
}

/** Figures in reading order: rows from the top, a row being figures that share most of their height, left to right within a row. */
export function readingOrder<T extends { box: Box }>(items: T[]): T[] {
  const rows: T[][] = [];
  for (const item of [...items].sort((a, b) => a.box.y - b.box.y)) {
    const row = rows.find((r) => r.some((other) => verticalOverlap(other.box, item.box) >= 0.5));
    if (row) row.push(item);
    else rows.push([item]);
  }
  rows.sort((a, b) => Math.min(...a.map((i) => i.box.y + i.box.h / 2)) - Math.min(...b.map((i) => i.box.y + i.box.h / 2)));
  return rows.flatMap((row) => row.sort((a, b) => a.box.x - b.box.x));
}
