// The face reader on invented heads: pale eye whites, big eyes under a fringe, a muzzled animal and a turned head.
import { test } from "node:test";
import assert from "node:assert/strict";
import { measureView, readSheet, type Measurement } from "squash-kit/sheet";
import { fringedHead, FACE, muzzledHead, paleEyedHead, turnedHead } from "./sheet/faces.ts";
import { toon, TOON } from "./sheet/toons.ts";

const head = TOON.chin - TOON.crown;

function read(draw: (image: never, cx: number) => void, facing?: "three-quarter"): Measurement {
  const { image } = toon({ head: draw as never });
  const sheet = readSheet(image);
  if (facing) sheet.views[0].guess = { ...sheet.views[0].guess, facing };
  return measureView(sheet, 1, { height: 1 }).measurement;
}

/** A reading's row on the sheet: its depth down the head from the measured crown, in the measured head's pixels. */
function rowOf(m: Measurement, ofHead: number): number {
  return m.landmarks.crown.y + ofHead * m.headPixels;
}

function near(actual: number, expected: number, slack: number, what: string): void {
  assert.ok(Math.abs(actual - expected) <= slack, `${what}: ${actual}, expected ${expected} within ${slack}`);
}

test("eyes whose whites are nearly the skin's colour are read by their outline, white included", () => {
  const m = read(paleEyedHead);
  const p = m.proportions;
  assert.equal(p.eyeLine.status, "found", JSON.stringify(m.unread));
  near(rowOf(m, p.eyeLine.ofHead), FACE.eyeY, 3, "eye line");
  near(p.eyeWidth.ofHead, 24 / head, 0.04, "eye width");
  near(p.eyeHeight.ofHead, 20 / head, 0.04, "eye height");
  near(p.eyeSpacing.ofHead, 40 / head, 0.04, "eye spacing");
  assert.equal(m.face?.eyes.length, 2);
});

test("the nose shade and the mouth line on the middle line are read by their depth down the head", () => {
  const m = read(paleEyedHead);
  const p = m.proportions;
  near(rowOf(m, p.nose.ofHead), FACE.noseY, 3, "nose");
  near(rowOf(m, p.mouth.ofHead), FACE.mouthY + 1, 3, "mouth");
  near(p.chinLength.ofHead * m.headPixels, m.landmarks.chin.y - FACE.mouthY - 1, 4, "chin length");
});

test("big eyes under a fringe are still read, their height said to be cut short", () => {
  const m = read(fringedHead);
  const p = m.proportions;
  assert.ok(p.eyeLine && p.eyeWidth, JSON.stringify(m.unread));
  near(p.eyeWidth.ofHead, 30 / head, 0.05, "eye width");
  near(p.eyeSpacing.ofHead, 44 / head, 0.05, "eye spacing");
  assert.ok(m.face!.eyes.every((e) => e.coveredTop));
  assert.equal(p.eyeHeight.status, "unsure");
  assert.match(p.eyeHeight.reason ?? "", /hair lies over/);
});

test("a muzzled head reads the nose pad at the muzzle's top and the mouth line below it, against the muzzle's colour", () => {
  const m = read(muzzledHead);
  const p = m.proportions;
  assert.ok(m.face?.muzzle, "a muzzle is found");
  assert.ok(p.nose && p.mouth, JSON.stringify(m.unread));
  near(rowOf(m, p.nose.ofHead), FACE.noseY - 1, 3, "nose");
  near(rowOf(m, p.mouth.ofHead), FACE.mouthY + 1, 3, "mouth");
  assert.ok(p.mouth.ofHead > p.nose.ofHead);
  near(p.eyeSpacing.ofHead, 52 / head, 0.06, "eye spacing");
});

test("a turned head reads both eyes, the wider one as the eye's width, and says the turn makes them unsure", () => {
  const m = read(turnedHead, "three-quarter");
  const p = m.proportions;
  assert.ok(p.eyeWidth, JSON.stringify(m.unread));
  assert.equal(p.eyeWidth.status, "unsure");
  assert.match(p.eyeWidth.reason ?? "", /three-quarter/);
  near(p.eyeWidth.ofHead, 28 / head, 0.05, "the near eye's width");
  const [far, near_] = m.face!.eyes;
  assert.ok(far.width < 0.8 * near_.width, `eyes ${far.width} and ${near_.width}`);
});

test("two eyes that disagree by more than a quarter are unsure, with the reason", () => {
  const m = read(turnedHead);
  assert.equal(m.proportions.eyeWidth.status, "unsure");
  assert.match(m.proportions.eyeWidth.reason ?? "", /apart/);
});

test("the face check image draws the region, the eyes, the nose and the mouth on the head zoom", async () => {
  const { image } = toon({ head: paleEyedHead as never });
  const sheet = readSheet(image);
  const measured = measureView(sheet, 1, { height: 1 });
  const { regionBoxes, zoomBox, drawFace } = await import("squash-kit/sheet");
  const [region] = regionBoxes(measured.anatomy, "head", sheet);
  const zoom = zoomBox(sheet, "head", region.box, 3);
  const before = Uint8Array.from(zoom.image.data);
  drawFace(zoom, measured.measurement.face!);
  let changed = 0;
  for (let i = 0; i < before.length; i++) if (before[i] !== zoom.image.data[i]) changed++;
  assert.ok(changed > 500, `drew ${changed} values`);
});

test("targets keep only the readings a build measures", async () => {
  const { targetsOf } = await import("squash-kit/sheet");
  const list = targetsOf(read(paleEyedHead), true);
  assert.ok(list.length > 0);
  for (const t of list) assert.ok(["eyeLine", "eyeWidth", "eyeSpacing", "handLength", "handWidth", "footLength", "footWidth"].includes(t.name), t.name);
});
