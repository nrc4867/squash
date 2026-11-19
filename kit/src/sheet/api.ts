import type { Box } from "./core/box.ts";
import { readAnatomy } from "./measure/anatomy.ts";
import { parseLandmarks, type PixelMark } from "./measure/landmark.ts";
import { measurementOf, type HeightTo, type Measurement } from "./measure/measurement.ts";
import type { MeasuredView } from "./read/profiles/index.ts";
import type { Raster } from "./raster/image.ts";
import { pickView, readSheet, type Sheet, type SheetOptions } from "./views/sheet.ts";

export interface MeasureOptions {
  /** The figure's height in body units, from the ground to its crown (or to its top with `heightTo: "top"`); 1 when left out. */
  height?: number;
  heightTo?: HeightTo;
  /** Landmarks the author reads off the image, in its pixels: `"crotch:251,556;knee:240,660"` or a record of marks. */
  landmarks?: string | Record<string, PixelMark>;
}

export interface MeasureSheetOptions extends SheetOptions, MeasureOptions {
  /** The view by index (from 1), name, or guessed facing; the first when left out. */
  view?: string | number;
  /** A box of the sheet to measure as the one view, instead of the views found. */
  box?: Box;
}

/** Measures one view of a sheet already read: its anatomy and its measurement in body units. */
export function measureView(sheet: Sheet, which?: string | number, options: MeasureOptions = {}): MeasuredView {
  const view = pickView(sheet, which);
  const given = typeof options.landmarks === "string" ? parseLandmarks(options.landmarks) : (options.landmarks ?? {});
  const anatomy = readAnatomy(view, given);
  return { anatomy, measurement: measurementOf(anatomy, options.height ?? 1, options.heightTo) };
}

/** Reads a sheet, or one box of it, from a file or raster. */
export function openSheet(source: string | Raster, options: SheetOptions & { box?: Box } = {}): Sheet {
  return readSheet(source, options.box ? { ...options, boxes: [options.box] } : options);
}

/**
 * Measures one view of a sheet image: `measureSheet("C:/refs/fox.png", { view: "front", height: 1.6 }).plan` holds every landmark's
 * height above the ground in body units, ready for `bodyPlan`.
 */
export function measureSheet(source: string | Raster, options: MeasureSheetOptions = {}): Measurement {
  const sheet = openSheet(source, options);
  return measureView(sheet, options.box ? 1 : options.view, options).measurement;
}
