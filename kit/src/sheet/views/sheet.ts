import { keyBackground, reopenBackground, type Background, type BackgroundOptions } from "../background.ts";
import { clipBox, unionBox, type Box } from "../core/box.ts";
import { blankMask, components, maskBox, maskOf, openComponents, type Component, type Mask } from "../core/mask.ts";
import { readRaster } from "../raster/io.ts";
import type { Raster } from "../raster/image.ts";
import { shadeShare, splitShadow } from "../shade.ts";
import { groupPieces, readingOrder } from "./group.ts";
import { guessFacings, type FacingGuess } from "./orientation.ts";

/** Pieces smaller than this share of the sheet's pixels, or than a few pixels, are noise. */
const SPECK_SHARE = 1e-5;
const SPECK_PIXELS = 4;
/** Strokes thinner than about twice this share of the sheet's larger side (hatching, ground lines) do not join two figures. */
const STROKE_SHARE = 0.0015;
/** A piece at least this share shade is a shadow, not a figure. */
const SHADOW_PIECE = 0.8;
/** Crumbs a shadow leaves at its edge, smaller than this share of the figure's largest piece, are dropped. */
const CRUMB_SHARE = 0.001;

/** One view of the figure on a sheet. */
export interface SheetView {
  /** 1 for the first in reading order. */
  index: number;
  /** The author's name for it, or `view-<index>`. */
  name: string;
  /** The box round the figure, its shadow left out. */
  box: Box;
  /** The figure's height in pixels: its top to its lowest point. */
  pixelHeight: number;
  /** The figure's pixels, the sheet's size. */
  mask: Mask;
  /** Its cast shadow's box, when it has one. */
  shadow?: Box;
  guess: FacingGuess;
}

export interface Sheet {
  image: Raster;
  background: Background;
  views: SheetView[];
  /** Clutter that is no figure: labels, text, swatches, arrows. */
  other: Box[];
}

export interface SheetOptions extends BackgroundOptions {
  /** Names for the views in reading order. */
  names?: readonly string[];
  /** Boxes on the sheet to take as the views instead of finding them, in this order. */
  boxes?: readonly Box[];
}

function viewOf(image: Raster, background: Background, mask: Mask, box: Box): { mask: Mask; box: Box; shadow?: Box } {
  const split = splitShadow(image, background, mask, box);
  const { pieces } = components(reopenBackground(image, background, split.figure));
  const crumb = (pieces[0]?.area ?? 0) * CRUMB_SHARE;
  const figure = maskOf(mask.width, mask.height, ...pieces.filter((p) => p.area > crumb).map((p) => p.pixels));
  const kept = maskBox(figure) ?? box;
  const shadow = maskBox(split.shadow);
  return { mask: figure, box: kept, shadow };
}

function figureMasks(image: Raster, background: Background, options: SheetOptions): { found: Array<{ mask: Mask; box: Box; shadow?: Box }>; other: Box[] } {
  const { width, height } = image;
  if (options.boxes) {
    const found = options.boxes.map((given) => {
      const box = clipBox(given, width, height);
      const mask = blankMask(width, height);
      for (let y = box.y; y < box.y + box.h; y++) for (let x = box.x; x < box.x + box.w; x++) mask.data[y * width + x] = background.figure.data[y * width + x];
      return viewOf(image, background, mask, maskBox(mask) ?? box);
    });
    return { found, other: [] };
  }
  const { pieces, labels } = openComponents(background.figure, Math.max(1, Math.round(STROKE_SHARE * Math.max(width, height))));
  const speck = Math.max(SPECK_PIXELS, SPECK_SHARE * width * height);
  const kept = pieces.filter((p) => p.area >= speck);
  const isShadow = (piece: Component) => shadeShare(image, background, piece.pixels) >= SHADOW_PIECE;
  const grouping = groupPieces(kept, isShadow, labels, width, height);
  const found = readingOrder(grouping.figures).map((figure) => {
    const mask = maskOf(width, height, ...figure.pieces.map((p) => p.pixels));
    const view = viewOf(image, background, mask, figure.box);
    const shadowPieces = figure.shadows.map((s) => maskBox(maskOf(width, height, s.pixels))!);
    const shadow = [view.shadow, ...shadowPieces]
      .filter((b): b is Box => b !== undefined)
      .reduce<Box | undefined>((all, b) => (all ? unionBox(all, b) : b), undefined);
    return { ...view, shadow };
  });
  return { found, other: grouping.other };
}

/** Reads a sheet: keys its background, finds each figure as its own view with its pieces and shadow, and the clutter beside them. */
export function readSheet(source: string | Raster, options: SheetOptions = {}): Sheet {
  const image = typeof source === "string" ? readRaster(source) : source;
  const background = keyBackground(image, options);
  const { found, other } = figureMasks(image, background, options);
  const guesses = guessFacings(image, found);
  const views = found.map((view, i) => ({
    index: i + 1,
    name: options.names?.[i] ?? `view-${i + 1}`,
    box: view.box,
    pixelHeight: view.box.h,
    mask: view.mask,
    shadow: view.shadow,
    guess: guesses[i],
  }));
  return { image, background, views, other };
}

/** A view by its index (from 1), its name, or its guessed facing; the first view when none is asked. */
export function pickView(sheet: Sheet, which?: string | number): SheetView {
  if (sheet.views.length === 0) throw new Error("the sheet shows no figure on its background; give ground=#rrggbb or box=x,y,w,h");
  if (which === undefined) return sheet.views[0];
  const text = String(which);
  const byIndex = /^\d+$/.test(text) ? sheet.views[Number(text) - 1] : undefined;
  const found = byIndex ?? sheet.views.find((v) => v.name === text) ?? sheet.views.find((v) => v.guess.facing === text);
  if (!found) throw new Error(`no view "${text}"; the sheet has ${sheet.views.map((v) => `${v.index} ${v.name} (${v.guess.facing}?)`).join(", ")}`);
  return found;
}
