// A hair outline traced from a synthetic sheet: the hair's region keyed by its colour, its tips and valleys found, and the view read back.
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { hairColors, outlineFeatures, traceHair } from "squash-kit/sheet";
import { hairOutline, readHairOutline } from "../src/hair/outline.ts";
import { ellipse, sheet } from "./sheet/figures.ts";
import type { Pixel } from "squash-kit/sheet";

const HAIR: Pixel = [110, 66, 38];
const SKIN: Pixel = [250, 220, 200];

/** A star of `spikes` points round the origin, counter-clockwise, its points out at 1 and its notches in at 0.5. */
function star(spikes: number): [number, number][] {
  return Array.from({ length: spikes * 2 }, (_, i) => {
    const a = (Math.PI * i) / spikes;
    const r = i % 2 === 0 ? 1 : 0.5;
    return [r * Math.cos(a), r * Math.sin(a)];
  });
}

test("a star's points are its tips and its notches its valleys", () => {
  const { tips, valleys } = outlineFeatures(star(5));
  assert.deepEqual(tips, [0, 2, 4, 6, 8]);
  assert.deepEqual(valleys, [1, 3, 5, 7, 9]);
  const circle = Array.from({ length: 48 }, (_, i) => [Math.cos((2 * Math.PI * i) / 48), Math.sin((2 * Math.PI * i) / 48)] as [number, number]);
  assert.deepEqual(outlineFeatures(circle), { tips: [], valleys: [] });
});

test("the hair over a face is traced in the head's drawn units", () => {
  const image = sheet(120, 120);
  ellipse(image, 60, 50, 30, 30, HAIR);
  ellipse(image, 60, 70, 22, 26, SKIN);
  const trace = traceHair(image, { view: "front", box: { x: 0, y: 0, w: 120, h: 120 }, colors: [HAIR], tolerance: 16, origin: [60, 96], unitsPerPixel: 0.01 });
  assert.equal(trace.view, "front");
  assert.ok(trace.points.length >= 8);
  const ys = trace.points.map(([, y]) => y);
  assert.ok(Math.abs(Math.max(...ys) - 0.76) < 0.03, `the top of the hair at 0.76, not ${Math.max(...ys)}`);
  const xs = trace.points.map(([x]) => x);
  assert.ok(Math.abs(Math.max(...xs) - 0.3) < 0.03 && Math.abs(Math.min(...xs) + 0.3) < 0.03, "as wide as the hair either side");
  assert.throws(() => traceHair(image, { view: "front", box: { x: 0, y: 0, w: 120, h: 120 }, colors: [[0, 200, 0]], tolerance: 10, origin: [60, 96], unitsPerPixel: 0.01 }));
});

test("an outline written by the sheet reads back as a recipe's view, the front needed", () => {
  const dir = mkdtempSync(join(tmpdir(), "kit-hair-"));
  try {
    const path = join(dir, "hair-front.json");
    writeFileSync(path, JSON.stringify({ view: "front", points: [[-0.2, 0], [0.2, 0], [0, 0.4]], tips: [2], valleys: [], pixels: [], colors: ["#6e4226"] }));
    const front = readHairOutline(path);
    assert.deepEqual(front, { view: "front", points: [[-0.2, 0], [0.2, 0], [0, 0.4]], tips: [2], valleys: [] });
    assert.deepEqual(hairOutline([front]), { views: [front] });
    assert.deepEqual(hairOutline([front], { fit: false }), { views: [front], fit: false });
    assert.throws(() => hairOutline([{ ...front, view: "back" }]));
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("an outline in the sheet head's lengths keeps its units and eye line through a recipe", () => {
  const image = sheet(120, 120);
  ellipse(image, 60, 50, 30, 30, HAIR);
  ellipse(image, 60, 70, 22, 26, SKIN);
  const trace = traceHair(image, { view: "front", box: { x: 0, y: 0, w: 120, h: 120 }, colors: [HAIR], exclude: [SKIN], origin: [60, 96], unitsPerPixel: 1 / 76, heads: true, eyes: 0.34 });
  assert.equal(trace.heads, true);
  assert.equal(trace.eyes, 0.34);
  assert.ok(Math.abs(Math.max(...trace.points.map(([, y]) => y)) - 1) < 0.05, "the top of the hair a head length over the chin");
  const dir = mkdtempSync(join(tmpdir(), "kit-hair-"));
  try {
    const path = join(dir, "hair-front.json");
    writeFileSync(path, JSON.stringify(trace));
    const read = readHairOutline(path);
    assert.equal(read.heads, true);
    assert.equal(read.eyes, 0.34);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("the hair's tones are picked above the eyes, the skin and paper left out", () => {
  const image = sheet(120, 120);
  ellipse(image, 60, 50, 30, 30, HAIR);
  ellipse(image, 50, 40, 8, 8, [190, 130, 80]);
  ellipse(image, 60, 70, 22, 26, SKIN);
  const picked = hairColors(image, { x: 0, y: 0, w: 120, h: 120 }, 60, HAIR, [SKIN, [244, 240, 232]]);
  assert.deepEqual(picked[0], HAIR);
  assert.ok(picked.length >= 2, "the shine is a tone of its own");
  assert.ok(picked.every((c) => Math.abs(c[0] - SKIN[0]) + Math.abs(c[1] - SKIN[1]) + Math.abs(c[2] - SKIN[2]) > 40), "no tone is the skin's");
});
