import { roundTo } from "../geom/point.ts";

/**
 * A part laid along a stretch of the body: positions in body units (heights, or distances along a limb) turned into the
 * part's outline x, which runs from its socket end, and its u, 0 at the socket end to 1 at the far end.
 */
export interface Span {
  /** The part's drawn length: its outlines' x runs 0 to this. */
  readonly length: number;
  /** Outline x at a position. */
  x(position: number): number;
  /** u at a position. */
  u(position: number): number;
  /** The position at a u. */
  at(u: number): number;
}

function span(length: number, x: (position: number) => number, at: (u: number) => number): Span {
  if (!(length > 0)) throw new Error(`a span needs a positive length, not ${length}`);
  return { length, x, u: (position) => roundTo(x(position) / length), at };
}

/** A part resting up from `bottom` to `top`, its socket end at the bottom: a torso, a neck, a head. */
export function uprightSpan(bottom: number, top: number): Span {
  return span(top - bottom, (h) => h - bottom, (u) => bottom + u * (top - bottom));
}

/** A part hanging down from `top` to `bottom`, its socket end at the top: a leg, a hanging arm. */
export function hangingSpan(top: number, bottom: number): Span {
  return span(top - bottom, (h) => top - h, (u) => top - u * (top - bottom));
}

/** A part measured along itself from its socket end, whatever way it points: an arm in A-pose, a tail. */
export function lengthSpan(length: number): Span {
  return span(length, (d) => d, (u) => u * length);
}
