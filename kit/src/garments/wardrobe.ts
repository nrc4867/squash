import type { GarmentSlot, Outfit, RecipeGarment, RecipeWardrobe } from "../schema/recipe.ts";

/** Layers counted out from the skin. */
export const LAYER = { under: 1, waistband: 2, over: 3 } as const;

/** Wardrobe slots from ids, each named by its id with a capital: `slots("top", "bottom")`. */
export function slots(...ids: string[]): GarmentSlot[] {
  return ids.map((id) => ({ id, name: id.charAt(0).toUpperCase() + id.slice(1) }));
}

/** A garment; its name is its id with a capital when left out. */
export function garment(spec: Omit<RecipeGarment, "name"> & { name?: string }): RecipeGarment {
  return { name: spec.id.charAt(0).toUpperCase() + spec.id.slice(1).replaceAll("-", " "), ...spec };
}

/** An outfit: which garment each slot holds. */
export function outfit(id: string, picks: Record<string, string>, name?: string): Outfit {
  return { id, name: name ?? id.charAt(0).toUpperCase() + id.slice(1), picks };
}

/** A wardrobe whose slots are read from its garments, shown in its first outfit unless told otherwise. */
export function wardrobe(garments: RecipeGarment[], outfits: Outfit[], shown?: string): RecipeWardrobe {
  const ids = [...new Set(garments.map((g) => g.slot).filter((slot): slot is string => typeof slot === "string"))];
  const result: RecipeWardrobe = { slots: slots(...ids), garments, outfits };
  const first = shown ?? outfits[0]?.id;
  if (first) result.shown = first;
  return result;
}
