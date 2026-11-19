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
  return { torso: typeof torso === "string" ? undefined : torso, legs: typeof legs === "string" ? {} : legs, arms, skipped };
}
