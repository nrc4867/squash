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
