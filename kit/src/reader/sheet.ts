import { readRaster } from "../sheet/raster/io.ts";
import type { Raster } from "../sheet/raster/image.ts";
import { readReadings, viewImagePath, type Readings } from "./readings.ts";
import { readSilhouette, type ViewSilhouette } from "./silhouette.ts";
import { VIEW_NAMES, type ViewName } from "./types.ts";

/** A sheet as read: its readings file and each view it gives, as a silhouette. */
export interface ReadSheet {
  path: string;
  readings: Readings;
  views: Map<ViewName, ViewSilhouette>;
}

/** Reads the views a readings file names; views that share an image read it once. */
export function readSheetViews(path: string, readings: Readings = readReadings(path)): ReadSheet {
  const images = new Map<string, Raster>();
  const views = new Map<ViewName, ViewSilhouette>();
  for (const name of VIEW_NAMES) {
    const given = readings.views[name];
    if (!given) continue;
    const file = viewImagePath(path, given);
    const image = images.get(file) ?? readRaster(file);
    images.set(file, image);
    views.set(name, readSilhouette(name, image, given));
  }
  return { path, readings, views };
}
