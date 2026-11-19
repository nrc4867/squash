import { existsSync } from "node:fs";
import { measureView } from "../../src/sheet/api.ts";
import type { MeasuredView } from "../../src/sheet/read/profiles/index.ts";
import type { Sheet } from "../../src/sheet/views/sheet.ts";
import { sheetFrom } from "./context.ts";
import type { SheetOptions } from "./options.ts";

/**
 * The side view side= names: a view of this sheet by index, name or facing, or another image's first view; the view guessed as a
 * side when side= is left out, and none when there is no such view.
 */
export function sideView(sheet: Sheet, image: string, options: SheetOptions): MeasuredView | undefined {
  const which = options.text("side");
  const measure = { height: options.number("height"), heightTo: options.text("heightTo") === "top" ? ("top" as const) : ("crown" as const) };
  if (which && existsSync(which) && which !== image) {
    return measureView(sheetFrom(which, options.with("box", "").with("names", "")), undefined, measure);
  }
  if (which) return measureView(sheet, which, measure);
  return sheet.views.some((v) => v.guess.facing === "side") ? measureView(sheet, "side", measure) : undefined;
}
