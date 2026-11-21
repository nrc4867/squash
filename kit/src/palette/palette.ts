import type { RecipeMaterial, RecipeSlot } from "../schema/recipe.ts";
import type { Color } from "./color.ts";

/** A slot's toon material: a preset's name, or a preset with the settings it changes. */
export type Material = NonNullable<RecipeSlot["material"]>;

/** A preset's name. */
export type MaterialPreset = NonNullable<RecipeMaterial["preset"]>;

/**
 * A palette from slot names to colours, in the order given: `palette({ skin: "#e8c4a8", ink: [0.1, 0.08, 0.09] })`.
 * The returned `slots` go in the recipe's `palette`; `slot` names a slot and fails on one that is not there. `material` gives slots a toon
 * material, a preset's name or a preset with settings changed: `palette({ buckle: "#c8c8d0", lamp: "#ffd860" }, { material: { buckle: "metal",
 * lamp: { preset: "glow", glow: { strength: 0.8, halo: 12 } } } })`. `opacity` makes the slots it names see-through, 0 to 1, and `shadow` gives a
 * slot its own shadow tone (a shift of the colour when left out); both are settings of the slot's material, on top of any it is given:
 * `palette({ lens: "#203040" }, { opacity: { lens: 0.35 } })`, `palette({ skin: "#d8b48e" }, { shadow: { skin: "#b08a74" } })`.
 */
export function palette<const P extends Record<string, Color>>(
  colors: P,
  options: {
    material?: Partial<Record<keyof P & string, Material>>;
    opacity?: Partial<Record<keyof P & string, number>>;
    shadow?: Partial<Record<keyof P & string, Color>>;
  } = {},
): { slots: RecipeSlot[]; slot(name: keyof P & string): string } {
  const materials: Partial<Record<string, Material>> = options.material ?? {};
  const opacity: Partial<Record<string, number>> = options.opacity ?? {};
  const shadow: Partial<Record<string, Color>> = options.shadow ?? {};
  return {
    slots: Object.entries(colors).map(([slot, color]) => {
      const material = materialOf(materials[slot], opacity[slot], shadow[slot]);
      return { slot, color, ...(material === undefined ? {} : { material }) };
    }),
    slot(name) {
      if (!(name in colors)) throw new Error(`the palette has no slot "${name}"`);
      return name;
    },
  };
}

/** `given` with a see-through `opacity` and a `shadow` tone set on it, or `given` alone, or undefined when nothing is set. */
function materialOf(given: Material | undefined, opacity: number | undefined, shadow: Color | undefined): Material | undefined {
  if (opacity === undefined && shadow === undefined) return given;
  const base: RecipeMaterial = typeof given === "string" ? { preset: given } : { ...(given ?? {}) };
  return { ...base, ...(opacity === undefined ? {} : { seeThrough: opacity }), ...(shadow === undefined ? {} : { shadow }) };
}

/** A preset with settings changed: `material("metal", { rim: { width: 0.2 } })`. */
export function material(preset: MaterialPreset, settings: Omit<RecipeMaterial, "preset"> = {}): Material {
  return Object.keys(settings).length === 0 ? preset : { preset, ...settings };
}
