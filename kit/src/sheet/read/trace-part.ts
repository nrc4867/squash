import type { Mask } from "../core/mask.ts";
import type { ImageFrame } from "./partFrame.ts";

/** The image side a side view faces. */
export type SideFacing = "left" | "right";

/** A part cut from the view and the frame its recipe outline is drawn in. */
export interface PartPlacement {
  mask: Mask;
  frame: ImageFrame;
  /** Whether the traced part is the character's right, where the view tells; a pair's recipe part is its left. */
  characterRight?: boolean;
  /** What the trace took to place the part, for the printout. */
  notes: string[];
}
