// Reading a synthetic sheet: palette, profiles, zooms, traces, the images drawn for agents, and the files they go to.
import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import jpeg from "jpeg-js";
import {
  gridImage,
  measureView,
  readRaster,
  readSheet,
  regionBoxes,
  sheetPalette,
  sheetProfiles,
  traceShape,
  viewCrop,
  viewsOverview,
  writePng,
  zoomBox,
} from "squash-kit/sheet";
import { kitDir } from "../src/run/paths.ts";
import { ellipse, figure, FUR, INK, rect, sheet } from "./sheet/figures.ts";

const SHIRT: [number, number, number] = [220, 70, 60];
const BLACK_FUR: [number, number, number] = [35, 35, 38];

/** A figure in a red shirt with black boots, ink lines across it. */
function dressedSheet() {
  const image = sheet(260, 360);
  figure(image, 130, 20, 300);
  rect(image, 100, 110, 160, 180, SHIRT);
  rect(image, 97, 290, 160, 320, BLACK_FUR);
  for (const y of [130, 150, 200]) rect(image, 102, y, 158, y + 2, INK);
  return image;
}

test("the palette finds the body, shirt and boot colours by share, and leaves the thin ink lines out", () => {
  const image = dressedSheet();
  const read = readSheet(image);
  const palette = sheetPalette(read, read.views[0]);
  const hexes = palette.colors.map((c) => c.hex);
  assert.equal(hexes[0], "#5a96d2");
  assert.ok(hexes.includes("#dc463c"), hexes.join(" "));
  assert.ok(hexes.includes("#232326"), "a large dark area is a colour, not ink");
  assert.ok(!hexes.includes("#1e1e28"));
  assert.ok(palette.ink.share > 0);
  const shirt = palette.colors.find((c) => c.hex === "#dc463c")!;
  assert.ok(shirt.box.y >= 108 && shirt.box.y + shirt.box.h <= 182);
  assert.ok(Math.abs(palette.colors.reduce((s, c) => s + c.share, 0) - 1) < 0.01);
});

test("profiles give the torso's left and right and each leg's radii at stations, ready for tube", () => {
  const image = sheet(260, 360);
  figure(image, 130, 20, 300);
  const read = readSheet(image);
  const front = measureView(read, 1, { height: 1 });
  const profiles = sheetProfiles(front, undefined, { stations: 5 });
  const torso = profiles.torso!;
  assert.equal(torso.at.length, 5);
  assert.equal(torso.profiles.left!.length, 5);
  assert.ok(Math.abs(torso.profiles.left![1] - 0.1) < 0.01, `torso half width ${torso.profiles.left![1]}`);
  assert.deepEqual(Object.keys(profiles.legs).sort(), ["leg-l", "leg-r"]);
  const leg = profiles.legs["leg-l"];
  assert.equal(leg.confidence, "medium");
  assert.ok(leg.profiles.outer!.every((r) => Math.abs(r - 0.035) < 0.006), leg.profiles.outer!.join(" "));
  assert.deepEqual(Object.keys(profiles.arms).sort(), ["arm-l", "arm-r"]);
  assert.ok(profiles.skipped.some((s) => s.startsWith("depths")));
});

test("a side view adds the torso's front and back depths, facing the way its feet point", () => {
  const image = sheet(520, 360);
  figure(image, 130, 20, 300);
  ellipse(image, 390, 68, 33, 33, FUR);
  rect(image, 370, 95, 400, 110, FUR);
  rect(image, 360, 110, 420, 206, FUR);
  rect(image, 375, 206, 405, 305, FUR);
  rect(image, 375, 305, 440, 320, FUR);
  const read = readSheet(image);
  const profiles = sheetProfiles(measureView(read, 1), measureView(read, 2));
  assert.ok(profiles.torso!.profiles.front);
  assert.ok(profiles.torso!.notes.some((n) => n.includes("faces image right")));
});

test("a zoom crops a region and scales it 2 to 4 times with crisp edges", () => {
  const image = sheet(260, 360);
  figure(image, 130, 20, 300);
  const read = readSheet(image);
  const { anatomy } = measureView(read, 1);
  const [head] = regionBoxes(anatomy, "head", read);
  const zoom = zoomBox(read, head.name, head.box);
  assert.ok(zoom.factor >= 2 && zoom.factor <= 4);
  assert.equal(zoom.image.width, head.box.w * zoom.factor);
  assert.equal(regionBoxes(anatomy, "hands", read).length, 2);
  assert.equal(zoomBox(read, "box", { x: 0, y: 0, w: 10, h: 10 }, 3).image.width, 30);
});

test("a trace of a rectangular ear is a handful of points in body units", () => {
  const image = sheet(260, 360);
  figure(image, 130, 40, 300);
  rect(image, 166, 10, 186, 60, FUR);
  const read = readSheet(image);
  const { anatomy, measurement } = measureView(read, 1, { height: 1, heightTo: "top" });
  const trace = traceShape(anatomy, measurement, { part: "ear-right" });
  assert.ok(trace.points.length >= 4 && trace.points.length <= 8, `${trace.points.length} points`);
  const top = Math.max(...trace.points.map((p) => p[1]));
  assert.ok(Math.abs(top - 1) < 0.01);
  const open = traceShape(anatomy, measurement, { part: "ear-right", open: true });
  assert.equal(open.closed, false);
  assert.ok(open.points.length < trace.points.length + 2);
});

test("the overview, crops and grid are drawn the sizes they promise", () => {
  const image = sheet(400, 260);
  figure(image, 100, 20, 200);
  figure(image, 300, 40, 180);
  const read = readSheet(image);
  assert.equal(viewsOverview(read).width, 400);
  const crop = viewCrop(read, read.views[1]);
  assert.ok(crop.width < 200 && crop.height > 180);
  const measured = measureView(read, 1);
  const grid = gridImage(read, measured.anatomy, measured.measurement);
  assert.ok(grid.height >= 700 && grid.width > crop.width);
});

test("PNG and JPEG sheets read back as they were written", (t) => {
  const dir = mkdtempSync(join(tmpdir(), "squash-sheet-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const image = sheet(40, 30);
  rect(image, 10, 10, 20, 20, SHIRT);
  const png = writePng(join(dir, "a.png"), image);
  assert.deepEqual(readRaster(png), image);
  const encoded = jpeg.encode({ width: 40, height: 30, data: Buffer.from(image.data) }, 95);
  writeFileSync(join(dir, "a.jpg"), encoded.data);
  const back = readRaster(join(dir, "a.jpg"));
  assert.equal(back.width, 40);
  assert.ok(Math.abs(back.data[(15 * 40 + 15) * 4] - SHIRT[0]) < 12);
});

test("tools/recipe.sh kit sheet views writes the overview, a crop a view and views.json, and prints their paths", (t) => {
  const dir = mkdtempSync(join(tmpdir(), "squash-sheet-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const image = sheet(400, 260);
  figure(image, 100, 20, 200);
  figure(image, 300, 40, 180);
  const file = writePng(join(dir, "pair.png"), image);
  const out = join(dir, "out");
  const result = spawnSync(process.execPath, [join(kitDir, "run.ts"), "sheet", "views", file, `out=${out}`, "names=front,side"], { encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
  for (const name of ["views.png", "front.png", "side.png", "views.json"]) {
    assert.ok(existsSync(join(out, name)), name);
    assert.ok(result.stdout.includes(join(out, name)), name);
  }
  const json = JSON.parse(readFileSync(join(out, "views.json"), "utf8"));
  assert.deepEqual(json.views.map((v: { name: string }) => v.name), ["front", "side"]);
  assert.equal(json.views[0].mask, undefined);
});
