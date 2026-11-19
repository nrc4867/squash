import { armProfile } from "./arm.ts";
import { legProfiles } from "./legs.ts";
import { leftOnImageRight, sideFacing } from "./shared.ts";
import { torsoProfile } from "./torso.ts";
import type { MeasuredView, PartProfile, ProfileOptions, SheetProfiles } from "./types.ts";

export type * from "./types.ts";
export { sideFacing } from "./shared.ts";

const DEFAULT_STATIONS = 7;

/** Kit-ready radii for the torso, each leg and each loose arm, from a front view and, when given, a side view. */
export function sheetProfiles(front: MeasuredView, side?: MeasuredView, options: ProfileOptions = {}): SheetProfiles {
  const count = options.stations ?? DEFAULT_STATIONS;
  const facing = options.sideFacing ?? (side ? sideFacing(side) : "right");
  const skipped: string[] = [];
  const torso = torsoProfile(front, side, count, facing);
  if (typeof torso === "string") skipped.push(torso);
  const legs = legProfiles(front, side, count, facing);
  if (typeof legs === "string") skipped.push(legs);
  const arms: Record<string, PartProfile> = {};
  const flip = leftOnImageRight(front);
  for (const piece of front.anatomy.pieces.filter((p) => p.kind === "arm")) {
    const characterSide = flip ? (piece.side < 0 ? "r" : "l") : piece.side < 0 ? "l" : "r";
    arms[`arm-${characterSide}`] = armProfile(front, piece, count);
  }
  if (Object.keys(arms).length === 0) skipped.push("arms: no arm stands apart from the body in the front view");
  if (!side) skipped.push("depths: no side view given");
  const result = { torso: typeof torso === "string" ? undefined : torso, legs: typeof legs === "string" ? {} : legs, arms, skipped, warnings: [] as string[] };
  const turned = (view: MeasuredView, role: string, what: string) => {
    const guess = view.anatomy.view.guess;
    if (guess.facing === "three-quarter") result.warnings.push(`the ${role} view (${view.anatomy.view.name}) is guessed three-quarter (${Math.round(guess.confidence * 100)}%): ${what} are not true ${what === "widths" ? "widths" : "depths"}, so every radius read from it is low confidence`);
  };
  turned(front, "front", "widths");
  if (side) {
    turned(side, "side", "depths");
    if (side.anatomy.view.guess.facing !== "side" && side.anatomy.view.guess.facing !== "three-quarter") result.warnings.push(`the side view (${side.anatomy.view.name}) is guessed ${side.anatomy.view.guess.facing}, not side: its front and back radii are not depths`);
  }
  if (result.warnings.length > 0) {
    const all = [result.torso, ...Object.values(result.legs), ...Object.values(result.arms)].filter((p): p is PartProfile => p !== undefined);
    for (const part of all) {
      part.confidence = "low";
      part.notes.push(...result.warnings);
    }
  }
  return result;
}
