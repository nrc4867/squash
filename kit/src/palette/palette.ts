import type { RecipeSlot } from "../schema/recipe.ts";
import type { Color } from "./color.ts";

/**
 * A palette from slot names to colours, in the order given: `palette({ skin: "#e8c4a8", ink: [0.1, 0.08, 0.09] })`.
 * The returned `slots` go in the recipe's `palette`; `slot` names a slot and fails on one that is not there. `opacity` makes the slots it
 * names see-through, 0 to 1: `palette({ lens: "#203040" }, { opacity: { lens: 0.35 } })`. `shadow` gives a slot its own shadow tone (a warm shift
 * of the colour when left out): `palette({ skin: "#d8b48e" }, { shadow: { skin: "#b08a74" } })`.
 */
export function palette<const P extends Record<string, Color>>(
  colors: P,
  options: { opacity?: Partial<Record<keyof P & string, number>>; shadow?: Partial<Record<keyof P & string, Color>> } = {},
): { slots: RecipeSlot[]; slot(name: keyof P & string): string } {
  const opacity: Partial<Record<string, number>> = options.opacity ?? {};
  const shadow: Partial<Record<string, Color>> = options.shadow ?? {};
  return {
    slots: Object.entries(colors).map(([slot, color]) => ({ slot, color, ...(shadow[slot] === undefined ? {} : { shadow: shadow[slot] }), ...(opacity[slot] === undefined ? {} : { opacity: opacity[slot] }) })),
    slot(name) {
      if (!(name in colors)) throw new Error(`the palette has no slot "${name}"`);
      return name;
    },
  };
}
