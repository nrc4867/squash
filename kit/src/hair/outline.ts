// A hair outline for lock hair to be fitted to: one closed curve a view round the region the hair covers, in the head's drawn units.
// `kit sheet hair` traces one from a sheet; see docs/hld/parts.md, Hair locks.

import { readFileSync } from "node:fs";
import type { RecipeHairOutline, RecipeOutlineView } from "../schema/recipe.ts";

/**
 * A hair entry's `outline`: `views`, the front among them, and `fit` (true by default), whether the build fits the outer locks' tips to
 * the outline's tips, sizes the inner mass to it and keeps every other lock inside it.
 */
export function hairOutline(views: RecipeOutlineView[], options: { fit?: boolean } = {}): RecipeHairOutline {
  if (!views.some((v) => v.view === "front")) throw new Error("hairOutline: the front view is needed");
  return { views, ...(options.fit === false ? { fit: false } : {}) };
}

/** One view of an outline as `kit sheet hair` wrote it (`hair-front.json`), keeping only what a recipe takes. */
export function readHairOutline(path: string): RecipeOutlineView {
  const read = JSON.parse(readFileSync(path, "utf8")) as RecipeOutlineView;
  const { view, points, tips, valleys } = read;
  if (!view || !Array.isArray(points)) throw new Error(`${path}: not a hair outline (kit sheet hair writes one)`);
  return { view, points, ...(tips ? { tips } : {}), ...(valleys ? { valleys } : {}) };
}
