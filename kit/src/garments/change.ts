import type { Hide, RecipeShape, Redraw, Swap, Through } from "../schema/recipe.ts";

/** The part comes out through a hole: an armhole or neckline with `cuff` false, a collar or sleeve cuff with it true. */
export function through(cuff = true, options: Omit<Through, "type" | "cuff"> = {}): Through {
  return { type: "through", cuff, ...options };
}

/** The part is not built while the garment is worn. */
export function hide(): Hide {
  return { type: "hide" };
}

/** The part keeps its template and takes this shape over its own. */
export function redraw(shape: RecipeShape): Redraw {
  return { type: "redraw", shape };
}

/** The part takes another template with this shape while the garment is worn, as a closed shoe swaps a foot for a blob. */
export function swap(template: string, shape?: RecipeShape): Swap {
  return shape ? { type: "swap", template, shape } : { type: "swap", template };
}
