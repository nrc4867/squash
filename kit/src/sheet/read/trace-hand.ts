import type { Box } from "../core/box.ts";
import { components, maskOf } from "../core/mask.ts";
import type { Anatomy } from "../measure/anatomy.ts";
import { handsOf, type OneHand } from "../measure/hands.ts";
import { farthestFrom, joinPoint } from "../measure/pieces.ts";
import type { Raster } from "../raster/image.ts";
import { across, unit, type ImageFrame, type OutlinePane } from "./partFrame.ts";
import type { PartPlacement, SideFacing } from "./trace-part.ts";

export interface HandTraceOptions {
  side: "left" | "right";
  pane: OutlinePane;
  box?: Box;
  /** The wrist and the fingertip, in the image's pixels: the hand's axis. */
  wrist?: [number, number];
  tip?: [number, number];
  image?: Raster;
  /** Which image side a side view faces. */
  facing: () => SideFacing;
}

type Place = { x: number; y: number };

/** The hand read off the arm on the image's `side`, or why there is none. */
function readHand(anatomy: Anatomy, side: "left" | "right", image?: Raster): OneHand {
  const { hands } = handsOf(anatomy, image);
  const read = hands.filter((h): h is OneHand => "length" in h && h.side === `image-${side}`);
  const hand = read.sort((a, b) => b.piece.area - a.piece.area)[0];
  if (!hand) throw new Error(`no hand is read on the image's ${side} in this view; trace it with box=x,y,w,h wrist=x,y tip=x,y`);
  return hand;
}

/**
 * A hand cut at the wrist and placed in its own frame (it rests down, palm toward the body's middle, its socket at the wrist): x from
 * the wrist toward the fingertip; Side's y forward, Top's y toward the character's right.
 */
export function handPlacement(anatomy: Anatomy, options: HandTraceOptions): PartPlacement {
  if (options.pane === "front") throw new Error("a hand is traced as=side or as=top");
  const { width, height } = anatomy.figure.mask;
  const notes: string[] = [];
  let pixels: Int32Array;
  let wrist: Place;
  let tip: Place;
  const place = (p: [number, number] | undefined): Place | undefined => (p ? { x: p[0] + 0.5, y: p[1] + 0.5 } : undefined);
  if (options.box) {
    const b = options.box;
    const inside: number[] = [];
    for (let y = b.y; y < b.y + b.h; y++) for (let x = b.x; x < b.x + b.w; x++) if (anatomy.figure.mask.data[y * width + x]) inside.push(y * width + x);
    pixels = components(maskOf(width, height, Int32Array.from(inside))).pieces[0]?.pixels ?? new Int32Array();
    if (pixels.length === 0) throw new Error("nothing of the figure to trace in that box");
    const join = joinPoint(pixels, width, anatomy.core);
    wrist = place(options.wrist) ?? { x: join.x + 0.5, y: join.y + 0.5 };
    const far = farthestFrom(pixels, width, { x: Math.floor(wrist.x), y: Math.floor(wrist.y) });
    tip = place(options.tip) ?? { x: far.x + 0.5, y: far.y + 0.5 };
  } else {
    const hand = readHand(anatomy, options.side, options.image);
    pixels = hand.pixels ?? hand.piece.pixels;
    wrist = place(options.wrist) ?? { x: hand.wrist.x + 0.5, y: hand.wrist.y + 0.5 };
    tip = place(options.tip) ?? { x: hand.tip.x + 0.5, y: hand.tip.y + 0.5 };
    notes.push(hand.pixels ? "the hand is told from its sleeve by the skin's colour" : "the wrist is the arm's narrowest row above the hand");
  }
  const axis = unit(tip.x - wrist.x, tip.y - wrist.y);
  const beyond = pixels.filter((at) => ((at % width) + 0.5 - wrist.x) * axis.x + (Math.floor(at / width) + 0.5 - wrist.y) * axis.y >= 0);
  const cut = components(maskOf(width, height, beyond)).pieces[0]?.pixels;
  if (!cut || cut.length === 0) throw new Error("nothing of the hand lies past the wrist toward the tip");
  const facing = anatomy.view.guess.facing;
  let toward: Place | undefined;
  let characterRight: boolean | undefined;
  if (options.pane === "side") {
    if (facing === "side" || facing === "three-quarter") toward = { x: options.facing() === "right" ? 1 : -1, y: 0 };
    else notes.push("y is a quarter turn left of the wrist-to-tip axis in the image: negate y if the thumb belongs on the other side");
  } else {
    if (facing === "front") toward = { x: -1, y: 0 };
    else if (facing === "back") toward = { x: 1, y: 0 };
    else notes.push("Top is the hand edge-on from the front or back: this view is not one, so y is a quarter turn left of the axis");
    characterRight = facing === "front" ? options.side === "left" : facing === "back" ? options.side === "right" : undefined;
  }
  const frame: ImageFrame = { origin: wrist, x: axis, y: across(axis, toward) };
  return { mask: maskOf(width, height, cut), frame, characterRight, notes };
}
