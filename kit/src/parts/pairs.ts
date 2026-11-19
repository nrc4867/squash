import type { BonePose } from "../schema/recipe.ts";

/** The suffixes a pair's sides take when its entry names none. */
export const LEFT_SUFFIX = "-l";
export const RIGHT_SUFFIX = "-r";

export type Sides = {
  left: string;
  right: string;
};

/** A pair's side ids from its base name: `sides("arm")` is arm-l and arm-r. */
export function sides(base: string): Sides {
  return { left: base + LEFT_SUFFIX, right: base + RIGHT_SUFFIX };
}

/** A part's `pair` field naming its sides from a base name: `{ ...pair("arm") }` on the arms' entry. */
export function pair(base: string): { pair: Sides } {
  return { pair: sides(base) };
}

/** The same bone pose on both sides of a pair, which mirror each other by themselves. */
export function bothSides(base: string, bone: Omit<BonePose, "part">): BonePose[] {
  const { left, right } = sides(base);
  return [
    { part: left, ...bone },
    { part: right, ...bone },
  ];
}
