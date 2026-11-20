import type { Point } from "../../geom/point.ts";
import type { Box } from "../core/box.ts";
import { components, maskOf } from "../core/mask.ts";
import type { Anatomy } from "../measure/anatomy.ts";
import { ankleRow, splitFeet, standingBelow } from "../measure/footSplit.ts";
import { across, principalAxis, unit, type ImageFrame, type OutlinePane } from "./partFrame.ts";
import type { PartPlacement, SideFacing } from "./trace-part.ts";

/** With no ankle given, a foot seen from above has its ankle this share of its length from the heel. */
const ANKLE_FROM_HEEL = 0.25;

export interface FootTraceOptions {
  side: "left" | "right";
  pane: OutlinePane;
  box?: Box;
  /** The ankle, and for a view from above the toe tip, in the image's pixels. */
  ankle?: [number, number];
  tip?: [number, number];
  /** Which image side a side view faces. */
  facing: () => SideFacing;
}

function largest(width: number, height: number, pixels: Int32Array): Int32Array {
  return components(maskOf(width, height, pixels)).pieces[0]?.pixels ?? new Int32Array();
}

/**
 * The foot's pixels: below the ankle row, standing on the ground, one foot of a pair (for Front, the feet split where they touch; for
 * Side, the standing piece on that side of the image); within `box` when given.
 */
function footPixels(anatomy: Anatomy, options: FootTraceOptions, row: number | undefined, notes: string[]): Int32Array {
  const { mask } = anatomy.figure;
  const { width, height } = mask;
  if (options.box) {
    const b = options.box;
    const inside: number[] = [];
    for (let y = Math.max(b.y, row ?? b.y); y < b.y + b.h; y++) for (let x = b.x; x < b.x + b.w; x++) if (mask.data[y * width + x]) inside.push(y * width + x);
    return largest(width, height, Int32Array.from(inside));
  }
  const top = row ?? ankleRow(anatomy);
  if (options.pane === "front") {
    const split = splitFeet(anatomy, top);
    const wanted = split.feet.find((f) => f.side === `image-${options.side}`);
    if (split.feet.length === 1) notes.push("the feet are not told apart in this view: both are traced as one");
    else if (split.how === "notch" || split.how === "legs") notes.push(`the feet touch: cut at column ${split.divider} (${split.how === "notch" ? "the notch between them" : "under the gap between the legs"})`);
    const foot = wanted ?? split.feet[0];
    if (!foot) throw new Error("no foot stands on the ground below the ankle in this view; trace it with box=x,y,w,h");
    return largest(width, height, foot.pixels);
  }
  const standing = standingBelow(anatomy, top).sort((a, b) => a.box.x - b.box.x);
  if (standing.length === 0) throw new Error("no foot stands on the ground below the ankle in this view; trace it with box=x,y,w,h");
  if (standing.length === 1) {
    notes.push("one foot stands below the ankle in this view (the other is behind it or joins it): it is traced");
    return standing[0].pixels;
  }
  return (options.side === "left" ? standing[0] : standing[standing.length - 1]).pixels;
}

/** The middle of the foot's highest row: where the leg meets it. */
function ankleOf(pixels: Int32Array, width: number): { x: number; y: number } {
  let top = Infinity;
  for (const at of pixels) top = Math.min(top, Math.floor(at / width));
  let l = Infinity;
  let r = -Infinity;
  for (const at of pixels) {
    if (Math.floor(at / width) !== top) continue;
    l = Math.min(l, at % width);
    r = Math.max(r, at % width);
  }
  return { x: (l + r + 1) / 2, y: top };
}

/**
 * A foot cut at the ankle and placed in its own frame (it rests forward, its socket at the ankle): Side x forward from the ankle and
 * y up, Top x forward and y toward the character's right, Front the cut section with x the character's left and y up.
 */
export function footPlacement(anatomy: Anatomy, options: FootTraceOptions): PartPlacement {
  const { width, height } = anatomy.figure.mask;
  const notes: string[] = [];
  const facing = anatomy.view.guess.facing;
  const row = options.pane === "top" ? options.ankle?.[1] : (options.ankle?.[1] ?? (options.box ? undefined : ankleRow(anatomy)));
  const pixels = footPixels(anatomy, options, row, notes);
  if (pixels.length === 0) throw new Error("nothing of the figure to trace there");
  const mask = maskOf(width, height, pixels);
  const found = ankleOf(pixels, width);
  const given = options.ankle ? { x: options.ankle[0] + 0.5, y: options.ankle[1] } : undefined;
  if (options.pane === "top") {
    const places = Array.from(pixels, (at): Point => [at % width, Math.floor(at / width)]);
    const { centre, axis } = principalAxis(places);
    const toward = options.tip ? { x: options.tip[0] + 0.5 - centre.x, y: options.tip[1] + 0.5 - centre.y } : { x: 0, y: -1 };
    const forward = axis.x * toward.x + axis.y * toward.y >= 0 ? axis : { x: -axis.x, y: -axis.y };
    const along = places.map(([x, y]) => (x + 0.5 - centre.x) * forward.x + (y + 0.5 - centre.y) * forward.y);
    const heel = Math.min(...along);
    const at = heel + ANKLE_FROM_HEEL * (Math.max(...along) - heel);
    const origin = given ? { x: given.x, y: given.y + 0.5 } : { x: centre.x + at * forward.x, y: centre.y + at * forward.y };
    const right = across(forward);
    const frame: ImageFrame = { origin, x: forward, y: { x: -right.x, y: -right.y } };
    notes.push(`seen from above with the toes toward ${options.tip ? "tip=" : "the image's top"}: the image-${options.side} foot is the character's ${options.side}`);
    return { mask, frame, characterRight: options.side === "right", notes };
  }
  const ankle = given ?? found;
  if (options.pane === "front") {
    const front = facing !== "back";
    const frame: ImageFrame = { origin: ankle, x: unit(front ? 1 : -1, 0), y: { x: 0, y: -1 } };
    const characterRight = facing === "front" ? options.side === "left" : facing === "back" ? options.side === "right" : undefined;
    return { mask, frame, characterRight, notes };
  }
  const xs = Array.from(pixels, (at) => at % width);
  const reach = { left: ankle.x - Math.min(...xs), right: Math.max(...xs) + 1 - ankle.x };
  const toes = facing === "side" || facing === "three-quarter" ? options.facing() : reach.right >= reach.left ? "right" : "left";
  if (facing === "front" || facing === "back") notes.push("a front or back view shows the foot end-on: its Side is foreshortened, trace it from a side view");
  notes.push(`the toes point image ${toes}`);
  return { mask, frame: { origin: ankle, x: unit(toes === "right" ? 1 : -1, 0), y: { x: 0, y: -1 } }, notes };
}
