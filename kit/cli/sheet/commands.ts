import { formatBox } from "../../src/sheet/core/box.ts";
import { pixelToHex } from "../../src/sheet/core/color.ts";
import { readAcross } from "../../src/sheet/measure/across.ts";
import { targetsOf } from "../../src/sheet/measure/measurement.ts";
import { checkImage } from "../../src/sheet/read/check.ts";
import { gridImage } from "../../src/sheet/read/grid.ts";
import { parseColors, traceHair, type HairView } from "../../src/sheet/read/hair.ts";
import { hairImage } from "../../src/sheet/read/hair-image.ts";
import { viewCrop, viewsOverview } from "../../src/sheet/read/overview.ts";
import { sheetPalette } from "../../src/sheet/read/palette.ts";
import { sheetProfiles } from "../../src/sheet/read/profiles/index.ts";
import { paletteImage } from "../../src/sheet/read/swatches.ts";
import { traceShape, type TracePart } from "../../src/sheet/read/trace.ts";
import { traceImage } from "../../src/sheet/read/trace-image.ts";
import { drawFace } from "../../src/sheet/read/zoom-face.ts";
import { drawReadings, regionReadings } from "../../src/sheet/read/zoom-readings.ts";
import { regionBoxes, zoomBox, type ZoomRegion } from "../../src/sheet/read/zoom.ts";
import { measuredFrom, sheetFrom, viewFileName } from "./context.ts";
import type { SheetOptions } from "./options.ts";
import { SheetOutput } from "./output.ts";
import { sideView } from "./side.ts";
import { acrossLines, readingLines, summary, traceLines } from "./summary.ts";

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
      console.log(`  ${v.index} ${v.name}: box=${formatBox(v.box)} ${v.pixelHeight}px ${v.guess.facing}? ${Math.round(v.guess.confidence * 100)}%${v.guess.reason ? ` (${v.guess.reason})` : ""}`);
    }
    for (const box of sheet.other) console.log(`  other: box=${formatBox(box)}`);
  },
  measure(image, options) {
    const sheet = sheetFrom(image, options);
    const out = new SheetOutput(options.outDir(image));
    const which = options.list("views");
    const views = which?.length === 1 && which[0] === "all" ? sheet.views.map((v) => v.index) : which;
    const measuredViews = views ? views.map((v) => measuredFrom(sheet, options.with("view", String(v)))) : [measuredFrom(sheet, options)];
    for (const measured of measuredViews) {
      const name = viewFileName(measured.anatomy.view);
      out.json(`measure-${name}.json`, measured.measurement);
      const targets = targetsOf(measured.measurement, options.flag("unsure"));
      if (targets.length > 0) out.json(`targets-${name}.json`, targets);
      console.log(summary(measured.measurement));
    }
    if (measuredViews.length > 1) {
      const across = readAcross(measuredViews.map((m) => m.measurement));
      out.json("measure-across.json", across);
      console.log("  across views:");
      for (const line of acrossLines(Object.values(across))) console.log(line);
    }
  },
  grid(image, options) {
    const sheet = sheetFrom(image, options);
    const measured = measuredFrom(sheet, options);
    const out = new SheetOutput(options.outDir(image));
    const name = viewFileName(measured.anatomy.view);
    out.png(`grid-${name}.png`, gridImage(sheet, measured.anatomy, measured.measurement));
    out.json(`measure-${name}.json`, measured.measurement);
  },
  check(image, options) {
    const sheet = sheetFrom(image, options);
    const measured = measuredFrom(sheet, options);
    const out = new SheetOutput(options.outDir(image));
    const name = viewFileName(measured.anatomy.view);
    out.png(`check-${name}.png`, checkImage(sheet, measured.anatomy, measured.measurement));
    out.json(`measure-${name}.json`, measured.measurement);
    console.log(summary(measured.measurement));
  },
  zoom(image, options) {
    const sheet = sheetFrom(image, options, false);
    const out = new SheetOutput(options.outDir(image));
    const factor = options.number("scale");
    const region = options.box();
    const named = (options.text("region") ?? "head") as ZoomRegion;
    const measured = measuredFrom(sheet, options, false);
    const boxes = region ? [{ name: "box", box: region }] : regionBoxes(measured.anatomy, named, sheet);
    const zooms = boxes.map(({ name, box }) => zoomBox(sheet, name, box, factor));
    const read = regionReadings(named, measured.measurement.proportions, measured.measurement.unread);
    if (!region) for (const zoom of zooms) drawReadings(zoom, read.readings);
    const face = measured.measurement.face;
    if (!region && named === "head" && face) for (const zoom of zooms) drawFace(zoom, face);
    for (const zoom of zooms) out.png(`zoom-${zoom.name}.png`, zoom.image);
    out.json(`zoom-${region ? "box" : named}.json`, { zooms: zooms.map(({ name, box, factor: f }) => ({ name, box, factor: f })), readings: region ? [] : read.readings, unread: region ? {} : Object.fromEntries(read.unread) });
    if (!region) for (const line of readingLines(read.readings, read.unread)) console.log(line);
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
    for (const note of profiles.warnings) console.log(`  warning: ${note}`);
  },
  trace(image, options) {
    const sheet = sheetFrom(image, options, false);
    const measured = measuredFrom(sheet, options, false);
    const part = (options.text("part") ?? "head") as TracePart;
    const as = options.text("as");
    if (as !== undefined && as !== "side" && as !== "top" && as !== "front") throw new Error(`as=${as}: side, top or front`);
    const facing = options.text("facing");
    const trace = traceShape(measured.anatomy, measured.measurement, {
      part,
      box: options.box(),
      tolerance: options.number("tolerance"),
      open: options.flag("open"),
      as,
      ankle: options.points("ankle")?.[0],
      wrist: options.points("wrist")?.[0],
      tip: options.points("tip")?.[0],
      facing: facing === "left" || facing === "right" ? facing : undefined,
      image: sheet.image,
    });
    const out = new SheetOutput(options.outDir(image));
    const name = trace.outline && trace.part !== "box" ? `${trace.part}-${trace.outline.pane}` : trace.part;
    out.json(`trace-${name}.json`, trace);
    out.png(`trace-${name}.png`, traceImage(sheet, measured.anatomy.view, trace));
    for (const line of traceLines(trace)) console.log(line);
  },
  hair(image, options) {
    const sheet = sheetFrom(image, options, false);
    const measured = measuredFrom(sheet, options, false);
    const m = measured.measurement;
    const as = (options.text("as") ?? m.view.guess.facing) as HairView;
    if (!HAIR_VIEWS.includes(as)) throw new Error(`as=${as}: ${HAIR_VIEWS.join(", ")}`);
    const chin = options.points("chin")?.[0] ?? [m.axis, m.landmarks.chin?.y ?? m.view.box.y + m.headPixels];
    const crown = options.points("crown")?.[0];
    const headHeight = options.number("headHeight");
    const scale = options.number("scale") ??
      (crown && headHeight ? headHeight / Math.max(1, chin[1] - crown[1]) : m.bodyPerPixel / (options.number("headScale") ?? 1));
    const view = m.view.box;
    const box = options.box() ?? { x: view.x, y: view.y, w: view.w, h: Math.min(view.h, Math.round(chin[1] - view.y + m.headPixels * 0.6)) };
    const colors = options.text("colors");
    const trace = traceHair(sheet.image, {
      view: as,
      box,
      colors: colors ? parseColors(colors) : undefined,
      tolerance: options.number("tolerance"),
      origin: chin,
      unitsPerPixel: scale,
      simplify: options.number("simplify"),
      close: options.number("close"),
    });
    const out = new SheetOutput(options.outDir(image));
    const name = `hair-${as}`;
    out.json(`${name}.json`, trace);
    out.png(`${name}.png`, hairImage(sheet.image, box, trace));
    console.log(`  ${as}: ${trace.points.length} points, ${trace.tips?.length ?? 0} tips, ${trace.valleys?.length ?? 0} valleys, ${scale.toPrecision(4)} head units a pixel, origin ${chin.join(",")}, colours ${trace.colors.join(",")}`);
  },
};

const HAIR_VIEWS: readonly string[] = ["front", "three-quarter", "side", "back"];
