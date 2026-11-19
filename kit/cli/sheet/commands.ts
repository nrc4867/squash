import { formatBox } from "../../src/sheet/core/box.ts";
import { pixelToHex } from "../../src/sheet/core/color.ts";
import { gridImage } from "../../src/sheet/read/grid.ts";
import { viewCrop, viewsOverview } from "../../src/sheet/read/overview.ts";
import { sheetPalette } from "../../src/sheet/read/palette.ts";
import { sheetProfiles } from "../../src/sheet/read/profiles/index.ts";
import { paletteImage } from "../../src/sheet/read/swatches.ts";
import { traceShape, type TracePart } from "../../src/sheet/read/trace.ts";
import { traceImage } from "../../src/sheet/read/trace-image.ts";
import { regionBoxes, zoomBox, type ZoomRegion } from "../../src/sheet/read/zoom.ts";
import { measuredFrom, sheetFrom, viewFileName } from "./context.ts";
import type { SheetOptions } from "./options.ts";
import { SheetOutput } from "./output.ts";
import { sideView } from "./side.ts";
import { summary } from "./summary.ts";

export type SheetCommand = (image: string, options: SheetOptions) => void;

export const sheetCommands: Record<string, SheetCommand> = {
  views(image, options) {
    const sheet = sheetFrom(image, options);
    const out = new SheetOutput(options.outDir(image));
    out.png("views.png", viewsOverview(sheet));
    for (const view of sheet.views) out.png(`${viewFileName(view)}.png`, viewCrop(sheet, view));
    out.json("views.json", {
      image,
      ground: pixelToHex(sheet.background.ground),
      tolerance: sheet.background.tolerance,
      views: sheet.views.map((v) => ({ index: v.index, name: v.name, box: v.box, pixelHeight: v.pixelHeight, shadow: v.shadow, guess: v.guess })),
      other: sheet.other,
    });
    for (const v of sheet.views) {
      console.log(`  ${v.index} ${v.name}: box=${formatBox(v.box)} ${v.pixelHeight}px ${v.guess.facing}? ${Math.round(v.guess.confidence * 100)}%`);
    }
    for (const box of sheet.other) console.log(`  other: box=${formatBox(box)}`);
  },
  measure(image, options) {
    const sheet = sheetFrom(image, options);
    const measured = measuredFrom(sheet, options);
    const out = new SheetOutput(options.outDir(image));
    out.json(`measure-${viewFileName(measured.anatomy.view)}.json`, measured.measurement);
    console.log(summary(measured.measurement));
  },
  grid(image, options) {
    const sheet = sheetFrom(image, options);
    const measured = measuredFrom(sheet, options);
    const out = new SheetOutput(options.outDir(image));
    const name = viewFileName(measured.anatomy.view);
    out.png(`grid-${name}.png`, gridImage(sheet, measured.anatomy, measured.measurement));
    out.json(`measure-${name}.json`, measured.measurement);
  },
  zoom(image, options) {
    const sheet = sheetFrom(image, options, false);
    const out = new SheetOutput(options.outDir(image));
    const factor = options.number("scale");
    const region = options.box();
    const named = (options.text("region") ?? "head") as ZoomRegion;
    const boxes = region ? [{ name: "box", box: region }] : regionBoxes(measuredFrom(sheet, options, false).anatomy, named, sheet);
    const zooms = boxes.map(({ name, box }) => zoomBox(sheet, name, box, factor));
    for (const zoom of zooms) out.png(`zoom-${zoom.name}.png`, zoom.image);
    out.json(`zoom-${region ? "box" : named}.json`, zooms.map(({ name, box, factor: f }) => ({ name, box, factor: f })));
  },
  palette(image, options) {
    const sheet = sheetFrom(image, options);
    const { anatomy } = measuredFrom(sheet, options);
    const palette = sheetPalette(sheet, anatomy.view, { colors: options.number("colors") });
    const out = new SheetOutput(options.outDir(image));
    const name = viewFileName(anatomy.view);
    out.png(`palette-${name}.png`, paletteImage(sheet, anatomy.view, palette));
    out.json(`palette-${name}.json`, palette);
    palette.colors.forEach((c, i) => console.log(`  ${i + 1} ${c.hex} ${(c.share * 100).toFixed(1)}% box=${formatBox(c.box)}`));
    console.log(`  ink ${palette.ink.hex} ${(palette.ink.share * 100).toFixed(1)}% of the figure`);
  },
  profiles(image, options) {
    const sheet = sheetFrom(image, options);
    const frontWhich = options.text("front") ?? (sheet.views.some((v) => v.guess.facing === "front") ? "front" : undefined);
    const front = measuredFrom(sheet, frontWhich ? options.with("view", frontWhich) : options);
    const side = sideView(sheet, image, options);
    const facing = options.text("facing");
    const profiles = sheetProfiles(front, side, {
      stations: options.number("stations"),
      sideFacing: facing === "left" || facing === "right" ? facing : undefined,
    });
    const out = new SheetOutput(options.outDir(image));
    out.json("profiles.json", { front: front.anatomy.view.name, side: side?.anatomy.view.name, ...profiles });
    for (const note of profiles.skipped) console.log(`  skipped ${note}`);
  },
  trace(image, options) {
    const sheet = sheetFrom(image, options, false);
    const measured = measuredFrom(sheet, options, false);
    const part = (options.text("part") ?? "head") as TracePart;
    const trace = traceShape(measured.anatomy, measured.measurement, {
      part,
      box: options.box(),
      tolerance: options.number("tolerance"),
      open: options.flag("open"),
    });
    const out = new SheetOutput(options.outDir(image));
    out.json(`trace-${trace.part}.json`, trace);
    out.png(`trace-${trace.part}.png`, traceImage(sheet, measured.anatomy.view, trace));
  },
};
