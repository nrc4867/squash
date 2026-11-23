import { readFileSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, resolve } from "node:path";
import type { HidingLayer, Px, PxBox, ViewName } from "./types.ts";

export const READINGS_FORMAT = "squash-sheet-readings/1";

/** One view of a sheet: an image (or a box of one), its flat background, and which way is forward on it. */
export interface ReadingsView {
  /** The image, relative to the readings file or absolute. */
  image: string;
  box?: PxBox;
  /** The background as `#rrggbb`; the image's corner when left out. */
  ground?: string;
  /** Side view: which way the subject faces; top view: which way its front points. Right and up when left out. */
  forward?: "left" | "right" | "up" | "down";
}

/** A mark the user placed by hand, in its view's pixels (the box's, when the view is a box). */
export interface HeldMark {
  id: string;
  view: ViewName;
  at: Px;
}

/**
 * A sheet's readings file: which views it has, which proposer and plan read them, the marks held by hand and the layers that hide
 * the shape. The proposal is made again from it; the edit page saves it.
 */
export interface Readings {
  format: typeof READINGS_FORMAT;
  proposer: string;
  /** The proposer's choice of what it proposes: a creature's body plan by name or path. */
  plan: string;
  views: Partial<Record<ViewName, ReadingsView>>;
  /** Body units a pixel of the views spans, when known; else `height` sets the scale. */
  unitsPerPixel?: number;
  /** The subject's height (or length, for a plan measured lying) in body units, when `unitsPerPixel` is not given. */
  height?: number;
  held?: HeldMark[];
  layers?: HidingLayer[];
  /** A proposer's own settings. */
  options?: Record<string, unknown>;
}

/** Reads a readings file. */
export function readReadings(path: string): Readings {
  const value = JSON.parse(readFileSync(path, "utf8")) as Readings;
  if (value.format !== READINGS_FORMAT) throw new Error(`${path}: not a readings file (format ${READINGS_FORMAT})`);
  if (Object.keys(value.views ?? {}).length === 0) throw new Error(`${path}: names no view`);
  return value;
}

export function writeReadings(path: string, readings: Readings): void {
  writeFileSync(path, JSON.stringify(readings, null, 2) + "\n");
}

/** A view's image path as the file names it, made absolute against the readings file. */
export function viewImagePath(readingsPath: string, view: ReadingsView): string {
  return isAbsolute(view.image) ? view.image : resolve(dirname(readingsPath), view.image);
}

/** Whether a value read from a request is a readings file. */
export function isReadings(value: unknown): value is Readings {
  const v = value as Readings;
  return typeof v === "object" && v !== null && v.format === READINGS_FORMAT && typeof v.views === "object";
}
