import type { Box } from "./box.ts";

/** One value a pixel, 1 where something is, rows from the top. */
export interface Mask {
  width: number;
  height: number;
  data: Uint8Array;
}

export function blankMask(width: number, height: number): Mask {
  return { width, height, data: new Uint8Array(width * height) };
}

export function maskAt(mask: Mask, x: number, y: number): boolean {
  return x >= 0 && y >= 0 && x < mask.width && y < mask.height && mask.data[y * mask.width + x] === 1;
}

export function maskCount(mask: Mask): number {
  let n = 0;
  for (const v of mask.data) n += v;
  return n;
}

/** The box round what the mask holds, or undefined when it holds nothing. */
export function maskBox(mask: Mask): Box | undefined {
  let x0 = mask.width;
  let y0 = mask.height;
  let x1 = -1;
  let y1 = -1;
  for (let y = 0; y < mask.height; y++) {
    for (let x = 0; x < mask.width; x++) {
      if (!mask.data[y * mask.width + x]) continue;
      if (x < x0) x0 = x;
      if (x > x1) x1 = x;
      if (y < y0) y0 = y;
      if (y > y1) y1 = y;
    }
  }
  return x1 < 0 ? undefined : { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 };
}

/** The mask's pixels inside the box, as a mask the box's size. */
export function cropMask(mask: Mask, box: Box): Mask {
  const out = blankMask(box.w, box.h);
  for (let y = 0; y < box.h; y++) for (let x = 0; x < box.w; x++) out.data[y * box.w + x] = maskAt(mask, box.x + x, box.y + y) ? 1 : 0;
  return out;
}

/** A mask holding the given pixel indices (y * width + x). */
export function maskOf(width: number, height: number, ...pixelLists: Int32Array[]): Mask {
  const mask = blankMask(width, height);
  for (const list of pixelLists) for (const at of list) mask.data[at] = 1;
  return mask;
}

/** A connected piece of a mask, 8-connected. */
export interface Component {
  label: number;
  area: number;
  box: Box;
  /** The indices (y * width + x) of its pixels. */
  pixels: Int32Array;
}

/** The mask's 8-connected pieces, largest first, and a label image (0 for nothing, else the piece's label). */
export function components(mask: Mask): { pieces: Component[]; labels: Int32Array } {
  const { width, height, data } = mask;
  const labels = new Int32Array(width * height);
  const pieces: Component[] = [];
  const stack = new Int32Array(width * height);
  for (let start = 0; start < data.length; start++) {
    if (!data[start] || labels[start]) continue;
    const label = pieces.length + 1;
    let top = 0;
    stack[top++] = start;
    labels[start] = label;
    const found: number[] = [];
    let x0 = width;
    let y0 = height;
    let x1 = -1;
    let y1 = -1;
    while (top > 0) {
      const at = stack[--top];
      found.push(at);
      const x = at % width;
      const y = (at - x) / width;
      if (x < x0) x0 = x;
      if (x > x1) x1 = x;
      if (y < y0) y0 = y;
      if (y > y1) y1 = y;
      for (let dy = -1; dy <= 1; dy++) {
        const ny = y + dy;
        if (ny < 0 || ny >= height) continue;
        for (let dx = -1; dx <= 1; dx++) {
          const nx = x + dx;
          if (nx < 0 || nx >= width) continue;
          const n = ny * width + nx;
          if (data[n] && !labels[n]) {
            labels[n] = label;
            stack[top++] = n;
          }
        }
      }
    }
    pieces.push({ label, area: found.length, box: { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 }, pixels: Int32Array.from(found) });
  }
  pieces.sort((a, b) => b.area - a.area);
  return { pieces, labels };
}

/** The pixels of a piece next to something outside it: its edge. */
export function edgePixels(piece: Component, width: number, labels: Int32Array): Int32Array {
  const height = labels.length / width;
  const edge: number[] = [];
  for (const at of piece.pixels) {
    const x = at % width;
    const y = (at - x) / width;
    const onBorder = x === 0 || y === 0 || x === width - 1 || y === height - 1;
    if (
      onBorder ||
      labels[at - 1] !== piece.label ||
      labels[at + 1] !== piece.label ||
      labels[at - width] !== piece.label ||
      labels[at + width] !== piece.label
    ) {
      edge.push(at);
    }
  }
  return Int32Array.from(edge);
}

/** The least distance in pixels between two sets of pixel indices, stopping early once at or below `enough`. */
export function pixelDistance(a: Int32Array, b: Int32Array, width: number, enough = 0): number {
  let best = Infinity;
  const enoughSquared = enough * enough;
  for (const p of a) {
    const px = p % width;
    const py = (p - px) / width;
    for (const q of b) {
      const qx = q % width;
      const dx = px - qx;
      const dy = py - (q - qx) / width;
      const d = dx * dx + dy * dy;
      if (d < best) {
        best = d;
        if (best <= enoughSquared) return Math.sqrt(best);
      }
    }
  }
  return Math.sqrt(best);
}

/** The mask after a square of side 2r+1 slides along one axis: kept where all (erode) or any (dilate) of the window is set. */
function slide(mask: Mask, r: number, horizontal: boolean, all: boolean): Mask {
  const { width, height } = mask;
  const out = blankMask(width, height);
  const lines = horizontal ? height : width;
  const length = horizontal ? width : height;
  const at = (line: number, i: number) => (horizontal ? line * width + i : i * width + line);
  for (let line = 0; line < lines; line++) {
    let count = 0;
    for (let i = -r; i < length + r; i++) {
      const enter = i + r;
      const leave = i - r - 1;
      if (enter < length && enter >= 0 && mask.data[at(line, enter)]) count++;
      if (leave >= 0 && leave < length && mask.data[at(line, leave)]) count--;
      if (i < 0 || i >= length) continue;
      const window = Math.min(length - 1, i + r) - Math.max(0, i - r) + 1;
      out.data[at(line, i)] = (all ? count === window && (i - r >= 0 && i + r < length) : count > 0) ? 1 : 0;
    }
  }
  return out;
}

/** Shrinks a mask by `r` pixels: only pixels whose whole square of side 2r+1 is set stay. */
export function erode(mask: Mask, r: number): Mask {
  return r <= 0 ? mask : slide(slide(mask, r, true, true), r, false, true);
}

/** Grows a mask by `r` pixels. */
export function dilate(mask: Mask, r: number): Mask {
  return r <= 0 ? mask : slide(slide(mask, r, true, false), r, false, false);
}

/**
 * Pieces of a mask once lines thinner than 2r+1 pixels are cut: each piece labelled on the mask opened by `r`, then every pixel the
 * opening dropped given the label of the piece that reaches it first through the mask. Hatching and ground strokes no longer join two
 * figures; the strokes themselves stay with a figure they touch.
 */
export function openComponents(mask: Mask, r: number): { pieces: Component[]; labels: Int32Array } {
  if (r <= 0) return components(mask);
  const opened = dilate(erode(mask, r), r);
  for (let i = 0; i < opened.data.length; i++) opened.data[i] &= mask.data[i];
  const { labels } = components(opened);
  const { width, height } = mask;
  let frontier: number[] = [];
  for (let at = 0; at < labels.length; at++) if (labels[at]) frontier.push(at);
  while (frontier.length > 0) {
    const next: number[] = [];
    for (const at of frontier) {
      const x = at % width;
      for (const n of [x > 0 ? at - 1 : -1, x < width - 1 ? at + 1 : -1, at - width, at + width]) {
        if (n < 0 || n >= width * height || !mask.data[n] || labels[n]) continue;
        labels[n] = labels[at];
        next.push(n);
      }
    }
    frontier = next;
  }
  const members = new Map<number, number[]>();
  const loose = blankMask(width, height);
  for (let at = 0; at < labels.length; at++) {
    if (!mask.data[at]) continue;
    if (!labels[at]) {
      loose.data[at] = 1;
      continue;
    }
    const list = members.get(labels[at]) ?? [];
    list.push(at);
    members.set(labels[at], list);
  }
  const pieces = [...members.entries()].map(([label, list]) => componentOf(label, list, width));
  let nextLabel = Math.max(0, ...pieces.map((p) => p.label)) + 1;
  for (const piece of components(loose).pieces) {
    const label = nextLabel++;
    for (const at of piece.pixels) labels[at] = label;
    pieces.push({ ...piece, label });
  }
  pieces.sort((a, b) => b.area - a.area);
  return { pieces, labels };
}

function componentOf(label: number, list: number[], width: number): Component {
  let x0 = Infinity;
  let y0 = Infinity;
  let x1 = -1;
  let y1 = -1;
  for (const at of list) {
    const x = at % width;
    const y = (at - x) / width;
    if (x < x0) x0 = x;
    if (x > x1) x1 = x;
    if (y < y0) y0 = y;
    if (y > y1) y1 = y;
  }
  return { label, area: list.length, box: { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 }, pixels: Int32Array.from(list) };
}
