// Cuts on lock hair: hair cut to the skin (a shell with a hairline and a fade), tips cut to a plane, braids and buns. See
// docs/hld/parts.md, Hair locks, and docs/reference/hair.md (Shell, Groups, Presets).

import type { LockTrim, RecipeHair, RecipeHairShell, RecipeLockGroup } from "../schema/recipe.ts";
import { lockHair, type LockHairSpec } from "./locks.ts";

/** How high up the sides and back a shell fades into the skin. */
export type FadeLevel = NonNullable<RecipeHairShell["fade"]>;

/**
 * Hair cut to the skin: a thin shell over the scalp inside a hairline (`hairline: { front, temples, sideburns, ears, nape }`, degrees up the
 * head from level), in a stubble tone (`tone`, else the skin carried `shade` toward the hair), fading into the skin up the sides and back
 * (`fade`: none, low, mid or high; `fadeTo`, the tone left at the hairline). Give it as a hair entry's `shell`.
 */
export function hairShell(spec: RecipeHairShell = {}): RecipeHairShell {
  return { ...spec };
}

/** A plane lock tips are cut to: `level` a share of the head's reach above the skull's middle (negative toward the jaw), `tilt` degrees, front up. */
export function trim(level: number, tilt = 0, blunt = true): LockTrim {
  return { level, ...(tilt !== 0 ? { tilt } : {}), ...(blunt ? {} : { blunt: false }) };
}

/** A buzz cut: the whole scalp cut to the skin inside its hairline, in a stubble tone, no locks. `shell` changes the shell. */
export const buzzCut = (spec: LockHairSpec): RecipeHair => lockHair("buzzCut", spec);

export interface FadeSpec extends LockHairSpec {
  /** How high the fade reaches: low (just above the ears and nape), mid (to the temples) or high (the default, to the corners of the top). */
  level?: FadeLevel;
}

/** A skin fade: a short cut whose tone fades into the skin up the sides and back, `level` how high; the top stays its stubble tone. */
export const fade = (spec: FadeSpec): RecipeHair => {
  const { level, ...rest } = spec;
  return lockHair("fade", { ...rest, shell: { ...(level ? { fade: level } : {}), ...(rest.shell ?? {}) } });
};

export interface FlatTopSpec extends LockHairSpec {
  /** How high the flat top is cut, a share of the head's reach above the skull's middle: 1.32 by default, about a third of a reach over the crown. */
  height?: number;
}

/** A flat top: a stiff brush standing straight up from the top, cut level over the head, over sides faded to the skin. */
export const flatTop = (spec: FlatTopSpec): RecipeHair => {
  const { height, ...rest } = spec;
  const cut: RecipeLockGroup[] = height === undefined ? [] : ["top", "crown", "fringe"].map((id) => ({ id, trim: trim(height) }));
  return lockHair("flatTop", { ...rest, groups: [...cut, ...(rest.groups ?? [])] });
};

export interface UndercutSpec extends LockHairSpec {
  /** The sides and back under the top: shaved (the default, a stubble tone to the hairline), bare (the skin), or faded low, mid or high. */
  sides?: "shaved" | "bare" | FadeLevel;
}

/** An undercut: long locks over the top, swept to the side from a side parting, over sides and back cut to the skin. */
export const undercut = (spec: UndercutSpec): RecipeHair => {
  const { sides = "shaved", ...rest } = spec;
  const shell: RecipeHairShell = sides === "shaved" ? {} : sides === "bare" ? { shade: 0.15 } : { fade: sides };
  return lockHair("undercut", { ...rest, shell: { ...shell, ...(rest.shell ?? {}) } });
};

export interface BluntBobSpec extends LockHairSpec {
  /** Where the hem is cut, a share of the head's reach above the skull's middle: -0.62 by default, about the jaw. */
  hem?: number;
  /** Degrees the hem tilts, front up: positive is longer at the back. */
  tilt?: number;
  /** Where the bangs are cut: 0.42 by default, above the brows; null leaves the fringe uncut. */
  bangs?: number | null;
}

/** A blunt bob: wide ribbon locks cut square at one length round the jaw, under bangs cut straight across. */
export const bluntBob = (spec: BluntBobSpec): RecipeHair => {
  const { hem, tilt, bangs, ...rest } = spec;
  const hemCut: RecipeLockGroup[] = hem === undefined && tilt === undefined ? [] : ["back", "left", "right"].map((id) => ({ id, trim: trim(hem ?? -0.62, tilt ?? 6) }));
  const bangCut: RecipeLockGroup[] = bangs === undefined ? [] : bangs === null ? [{ id: "fringe", trim: trim(-3) }] : [{ id: "fringe", trim: trim(bangs) }];
  return lockHair("bluntBob", { ...rest, groups: [...hemCut, ...bangCut, ...(rest.groups ?? [])] });
};

export interface BraidSpec extends LockHairSpec {
  /** Two braids, one behind each ear, from a centre parting, in place of one down the back. */
  twin?: boolean;
}

/** A braid: the hair drawn back over the inner mass into one braid of three plaited strands down the back, tied with a band (`band`). */
export const braid = (spec: BraidSpec): RecipeHair => {
  const { twin, ...rest } = spec;
  return lockHair(twin ? "twinBraids" : "braid", rest);
};

export interface BunSpec extends LockHairSpec {
  /** Two buns, high on either side of the top, in place of one. */
  space?: boolean;
  /** A low bun at the nape in place of a high one on the back of the crown. */
  low?: boolean;
}

/** A bun: the hair drawn over the inner mass to a tie and coiled round it into a dome, high on the back of the crown; `low` at the nape. */
export const bun = (spec: BunSpec): RecipeHair => {
  const { space, low, ...rest } = spec;
  if (space) return lockHair("spaceBuns", rest);
  const lowered: RecipeLockGroup[] = low ? [{ id: "bun", kind: "back", spread: 0, height: -0.7, rise: 45 }] : [];
  return lockHair("bun", { ...rest, groups: [...lowered, ...(rest.groups ?? [])] });
};
