/** The views a sheet reader reads. Back and bottom mirror front and top, so they add no shape. */
export type ViewName = "front" | "side" | "top";

export const VIEW_NAMES: readonly ViewName[] = ["front", "side", "top"];

/** A place in a view's own pixels: x right, y down. */
export type Px = [number, number];

/**
 * Where a reading came from: read off the view, held where the user dragged it, the proposer's default because no view shows it, or
 * inferred under a layer that hides the shape (clothing, fur), loosened by that layer.
 */
export type Source = "read" | "held" | "default" | "inferred";

/** A named point on a view: a landmark. */
export interface Mark {
  id: string;
  view: ViewName;
  at: Px;
  source: Source;
}

/** One measurement as read: a span from `from` to `to` on a view, its value in the proposer's unit, and where it came from. */
export interface Measure {
  id: string;
  view: ViewName;
  from: Px;
  to: Px;
  value: number;
  source: Source;
  /** The hiding layer it was inferred under. */
  layer?: string;
}

/** Everything a proposal read, to be shown where it was read. */
export interface Evidence {
  marks: Mark[];
  measures: Measure[];
}

/** A box on a view, [x, y, w, h] in its pixels. */
export type PxBox = [number, number, number, number];

/** Something drawn over the shape that hides it, on one view: clothing, fur, a hair tuft, a tail. Its looseness is how far it stands off the shape, in the proposer's unit (a head, for a creature). */
export interface HidingLayer {
  id: string;
  kind: string;
  looseness: number;
  view: ViewName;
  box: PxBox;
}

/** Where a view's pixels lie over the subject's space: the pixel of its origin on the ground, and pixels to one of its units. */
export interface ViewPlace {
  origin: Px;
  scale: number;
}
