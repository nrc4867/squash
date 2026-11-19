import type { Box } from "../../core/box.ts";

/** One eye as its outline, in the sheet's pixels: lid line or lash and the white together, not only the iris. */
export interface FaceEye {
  side: "image-left" | "image-right";
  box: Box;
  width: number;
  height: number;
  /** The fringe or hair lies over the eye's top, so its height is cut short. */
  coveredTop: boolean;
}

/** Where the face reader found things on one view, in the sheet's pixels, for the face check image. */
export interface FaceFinding {
  /** The skin area inside the head below the hair: its box and, row by row, its left and right edges. */
  region: { box: Box; rows: Array<{ y: number; l: number; r: number }> };
  /** The column the face's middle line runs down. */
  axis: number;
  skin: string;
  hair?: string;
  eyes: FaceEye[];
  /** A patch on the axis below the eyes that is not the skin's colour and holds the nose and mouth (a muzzle). */
  muzzle?: Box;
  nose?: Box;
  mouth?: Box;
}
