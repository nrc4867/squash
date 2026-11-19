/** A pixel box: left, top, width and height; it holds x from `x` to `x + w - 1`. */
export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

export function boxRight(box: Box): number {
  return box.x + box.w;
}

export function boxBottom(box: Box): number {
  return box.y + box.h;
}

/** The smallest box holding both. */
export function unionBox(a: Box, b: Box): Box {
  const x = Math.min(a.x, b.x);
  const y = Math.min(a.y, b.y);
  return { x, y, w: Math.max(boxRight(a), boxRight(b)) - x, h: Math.max(boxBottom(a), boxBottom(b)) - y };
}

/** The distance in pixels between two boxes; 0 when they touch or overlap. */
export function boxGap(a: Box, b: Box): number {
  const dx = Math.max(0, Math.max(a.x, b.x) - Math.min(boxRight(a), boxRight(b)));
  const dy = Math.max(0, Math.max(a.y, b.y) - Math.min(boxBottom(a), boxBottom(b)));
  return Math.hypot(dx, dy);
}

/** How much of the shorter box's height the two share, 0 to 1. */
export function verticalOverlap(a: Box, b: Box): number {
  const shared = Math.min(boxBottom(a), boxBottom(b)) - Math.max(a.y, b.y);
  return Math.max(0, shared) / Math.max(1, Math.min(a.h, b.h));
}

/** The box grown by `margin` on every side, kept inside a `width` by `height` image. */
export function padBox(box: Box, margin: number, width: number, height: number): Box {
  const x = Math.max(0, box.x - margin);
  const y = Math.max(0, box.y - margin);
  return { x, y, w: Math.min(width, boxRight(box) + margin) - x, h: Math.min(height, boxBottom(box) + margin) - y };
}

/** The part of `box` inside a `width` by `height` image. */
export function clipBox(box: Box, width: number, height: number): Box {
  const x = Math.max(0, Math.min(width - 1, box.x));
  const y = Math.max(0, Math.min(height - 1, box.y));
  return { x, y, w: Math.max(1, Math.min(width, boxRight(box)) - x), h: Math.max(1, Math.min(height, boxBottom(box)) - y) };
}

/** Reads "x,y,w,h" in pixels. */
export function parseBox(text: string): Box {
  const parts = text.split(",").map(Number);
  if (parts.length !== 4 || parts.some((n) => !Number.isFinite(n)) || parts[2] <= 0 || parts[3] <= 0) {
    throw new Error(`box "${text}" is not x,y,w,h in pixels`);
  }
  const [x, y, w, h] = parts.map(Math.round);
  return { x, y, w, h };
}

export function formatBox(box: Box): string {
  return `${box.x},${box.y},${box.w},${box.h}`;
}
