import type { RecipeTarget } from "../schema/recipe.ts";

/** Every proportion a build measures and a target may name. */
export const TARGET_NAMES = ["handLength", "handWidth", "footLength", "footWidth", "headHeight", "headWidth", "eyeLine", "eyeWidth", "eyeSpacing"] as const;
export type TargetName = (typeof TARGET_NAMES)[number];

/** A target's value: a share of the head or of the figure's height as text (`"0.4 head"`, `"0.06 height"`), or a ready target's parts. */
export type TargetValue = string | { of: "head" | "height"; value: number; tolerance?: number };

const SHARE = /^\s*(\d*\.?\d+)\s*(head|heads|height)\s*$/i;

function parse(name: string, spec: TargetValue): { of: "head" | "height"; value: number; tolerance?: number } {
  if (typeof spec !== "string") return spec;
  const m = spec.match(SHARE);
  if (!m) throw new Error(`target ${name}: "${spec}" is not a share like "0.4 head" or "0.06 height"`);
  return { of: m[2].toLowerCase() === "height" ? "height" : "head", value: Number(m[1]) };
}

/**
 * Proportions the built character is checked against: `targets: targets({ handLength: "0.4 head", footLength: "0.12 height" })` in the recipe.
 * `measure.txt` flags each one the build is off by more than `tolerance` (a share of a size, 0.15 when left out; a share of the head for a
 * place such as `eyeLine`, 0.05). A target of one name may carry its own tolerance: `{ of: "head", value: 0.4, tolerance: 0.1 }`.
 */
export function targets(spec: Partial<Record<TargetName, TargetValue>>, options: { tolerance?: number } = {}): RecipeTarget[] {
  return Object.entries(spec).map(([name, value]) => {
    if (!(TARGET_NAMES as readonly string[]).includes(name)) throw new Error(`"${name}" is not a measured proportion; names: ${TARGET_NAMES.join(", ")}`);
    const t = parse(name, value as TargetValue);
    const tolerance = t.tolerance ?? options.tolerance;
    return { name, of: t.of, value: t.value, ...(tolerance !== undefined ? { tolerance } : {}) };
  });
}
