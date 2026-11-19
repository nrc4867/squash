import { measureView, openSheet } from "../../src/sheet/api.ts";
import type { MeasuredView } from "../../src/sheet/read/profiles/index.ts";
import type { Sheet } from "../../src/sheet/views/sheet.ts";
import type { SheetOptions } from "./options.ts";
import { fileName } from "./output.ts";

/**
 * Reads the sheet as the options say: ground=, groundTolerance=, holes=, pale=false, names=, boxes= for the views, and box= for one view unless the
 * command takes box= as a region of its own.
 */
export function sheetFrom(image: string, options: SheetOptions, boxIsView = true): Sheet {
  return openSheet(image, {
    ground: options.text("ground"),
    tolerance: options.number("groundTolerance"),
    names: options.list("names"),
    box: boxIsView ? options.box() : undefined,
    boxes: options.boxes("boxes"),
    holes: options.points("holes"),
    keepPale: options.text("pale") === "false" ? false : undefined,
  });
}

/** Measures the view view= names, or box= when it is the view, at height= (to heightTo=crown or top) with landmarks=. */
export function measuredFrom(sheet: Sheet, options: SheetOptions, boxIsView = true): MeasuredView {
  const which = boxIsView && options.box() ? 1 : options.text("view");
  const heightTo = options.text("heightTo") === "top" ? "top" : "crown";
  return measureView(sheet, which, { height: options.number("height"), heightTo, landmarks: options.text("landmarks") });
}

/** A view's name for its files. */
export function viewFileName(view: { name: string }): string {
  return fileName(view.name);
}
