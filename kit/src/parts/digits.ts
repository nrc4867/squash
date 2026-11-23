import type { BonePose } from "../schema/recipe.ts";

/** The patch of a hand's thumb, outside the numbered fingers. */
export const THUMB = "thumb";

/** How a hand's or foot's digits are posed: each joint's degrees from the root, the same on every numbered digit unless `each` says. */
export type DigitCurl = {
  /** How many numbered digits the part builds: a hand's `digits` less its thumb, a foot's or paw's `toes`. */
  count: number;
  /** The row's name: fingers on a hand, toes on a foot or paw. */
  row?: string;
  /** Degrees at each joint from the root, fingers curling with negative degrees and toes with positive; a hand's fingers have three joints, toes two. */
  curl: number[];
  /** Degrees added at every joint of digit i, i from 0: a hand's outer fingers curling a little more than its first. */
  each?: (i: number) => number;
  /** A hand's thumb's degrees at its three joints, curling across the palm with positive degrees; left out, the thumb stays as built. */
  thumb?: number[];
};

/**
 * The bone poses that curl one part's digits: every joint `<row>:<i>.<k>` of its `count` numbered digits, and `<row>:thumb.<k>` when
 * `thumb` is given. `digitCurl("hand-l", { count: 3, curl: [-8, -12, -8], thumb: [8, 6, 4] })` relaxes a three-finger hand.
 */
export function digitCurl(part: string, curl: DigitCurl): BonePose[] {
  const row = curl.row ?? "fingers";
  const numbered = Array.from({ length: curl.count }, (_, i) =>
    curl.curl.map((degrees, k) => ({ part, joint: `${row}:${i}.${k + 1}`, degrees: degrees + (curl.each?.(i) ?? 0) })),
  ).flat();
  const thumb = (curl.thumb ?? []).map((degrees, k) => ({ part, joint: `${row}:${THUMB}.${k + 1}`, degrees }));
  return [...numbered, ...thumb];
}

/** The digit patch a socket, region or mark names to lie on one digit: `digitPatch(2)` is fingers:2, `digitPatch(0, "toes")` toes:0. */
export function digitPatch(digit: number | typeof THUMB, row = "fingers"): string {
  return `${row}:${digit}`;
}

/** The shortest a stretch makes a span, as the rig holds it: a whole retract slides a claw this far short of its anchor. */
const MIN_STRETCH = 0.1;

/** Which hard claws a retract slides: those on a part's `count` numbered digits and on its `named` ones. */
export type ClawRetract = {
  /** How many numbered digits the part builds: a hand's `digits` less its thumb, a foot's or paw's `toes`. */
  count: number;
  /** The named digits the part builds, which carry claws too: a hand's `thumb`, a bird foot's `back`. */
  named?: string[];
  /** How far the claws are in, from 0, out as built, to 1, hidden inside their digits. */
  amount: number;
};

/**
 * The bone poses that retract one part's hard claws (`clawParts` on a hand, foot, paw or bird foot): the root joint
 * `claws:<i>.2` of every claw, stretched toward its hidden anchor, not keeping volume, so the claw slides back whole into its digit,
 * the slide growing with the amount's square as the rig's Retract does.
 * `clawRetract("paw-l", { count: 3, amount: 1 })` sheathes a three-toed paw's claws; the app's Pose stage writes the same as Retract.
 */
export function clawRetract(part: string, retract: ClawRetract): BonePose[] {
  const amount = Math.min(Math.max(retract.amount, 0), 1);
  const stretch = 1 - (1 - MIN_STRETCH) * amount * amount;
  const digits: string[] = [...Array.from({ length: retract.count }, (_, i) => String(i)), ...(retract.named ?? [])];
  return digits.map((digit) => ({ part, joint: `claws:${digit}.2`, stretch, keepVolume: false }));
}
