import { length, sub, type V3 } from "../../geom/vec3.ts";
import type { ViewName } from "../../reader/types.ts";
import type { BuiltBody, BuiltPart } from "./body.ts";

/** A part shows its length in a view when it lies at least this share of its length across the view. */
const SHOWN_LENGTH = 0.45;

/** A limb is drawn from the side when at least this share of it lies above or below its parent. */
const SIDE_SHOWN_SHARE = 0.5;

/** A place as a view shows it, the view's depth left out: across, then up (or forward, from above). */
export function across(view: ViewName, p: V3): [number, number] {
  if (view === "front") return [p[2], p[1]];
  if (view === "side") return [p[0], p[1]];
  return [p[2], p[0]];
}

/** Whether a view shows a part along its length rather than end on, as the front view sees a level trunk. */
export function showsLength(built: BuiltPart, view: ViewName): boolean {
  const tip = built.joints[built.joints.length - 1];
  const root = built.joints[0];
  const [ax, ay] = across(view, root);
  const [bx, by] = across(view, tip);
  return Math.hypot(bx - ax, by - ay) >= SHOWN_LENGTH * length(sub(tip, root));
}

/** Whether the front view sees a limb behind another limb on the same parent further forward: hind legs behind front legs. */
function behindAnother(built: BuiltPart, body: BuiltBody): boolean {
  if (built.part.kind !== "limb") return false;
  const reach = length(sub(built.joints[built.joints.length - 1], built.joints[0])) * SHOWN_LENGTH;
  return [...body.parts.values()].some(
    (other) => other !== built && other.part.kind === "limb" && other.part.on === built.part.on && other.joints[0][0] > built.joints[0][0] + reach,
  );
}

/** Whether the side view shows a limb: where most of it lies above or below its parent. */
function shownFromSide(built: BuiltPart, body: BuiltBody): boolean {
  if (built.part.kind === "trunk" || !built.part.on) return true;
  const parent = body.parts.get(built.part.on)!;
  const low = Math.min(...parent.joints.map((j) => j[1]));
  const high = Math.max(...parent.joints.map((j) => j[1]));
  const samples = built.sections.filter((s) => s.center[1] < low || s.center[1] > high).length;
  return samples / built.sections.length >= SIDE_SHOWN_SHARE;
}

/** The parts each view's outline is read for, at rest, as the figure fit reads them. */
export function readParts(body: BuiltBody, view: ViewName): Set<string> {
  const out = new Set<string>();
  for (const built of body.parts.values()) {
    const shown =
      view === "front"
        ? showsLength(built, view) && !behindAnother(built, body)
        : view === "side"
          ? shownFromSide(built, body)
          : built.part.kind === "trunk" && showsLength(built, view);
    if (shown) out.add(built.part.id);
  }
  return out;
}
