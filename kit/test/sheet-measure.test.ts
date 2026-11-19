// Measuring a synthetic standing figure: landmarks in pixels, heads and body units, and the author's own marks.
import { test } from "node:test";
import assert from "node:assert/strict";
import { measureSheet, measureView, readSheet } from "squash-kit/sheet";
import { ellipse, FIGURE, figure, sheet } from "./sheet/figures.ts";

const TOP = 20;
const H = 300;
const image = sheet(260, 360);
figure(image, 130, TOP, H);
const measured = measureView(readSheet(image), 1, { height: 1.5 });
const { landmarks, plan } = measured.measurement;

/** The row a share of the figure's height lies on. */
const row = (share: number) => TOP + share * H;

function near(actual: number, expected: number, slack: number, what: string): void {
  assert.ok(Math.abs(actual - expected) <= slack, `${what}: ${actual}, expected ${expected} within ${slack}`);
}

test("the landmarks a standing figure shows are found near where they are drawn", () => {
  const slack = 0.025 * H;
  near(landmarks.earTip.y, row(FIGURE.earTip), 1, "ear tip");
  near(landmarks.crown.y, row(FIGURE.crown), 2, "crown");
  near(landmarks.chin.y, row(FIGURE.chin), slack, "chin");
  near(landmarks.shoulder.y, row(FIGURE.shoulder), slack, "shoulder");
  near(landmarks.armpit.y, row(FIGURE.armpit), 1, "armpit");
  near(landmarks.crotch.y, row(FIGURE.crotch), 1, "crotch");
  near(landmarks.ankle.y, row(FIGURE.ankle), 2, "ankle");
  near(landmarks.handTip.y, row(FIGURE.handTip), 1, "hand tip");
  for (const name of ["crown", "chin", "neck", "shoulder", "armpit", "crotch", "ankle", "handTip", "earTip"]) {
    assert.equal(landmarks[name].status, "found", name);
  }
  assert.equal(landmarks.knee.status, "estimated");
});

test("heights are above the ground in body units for the asked height to the crown, and in heads", () => {
  assert.equal(plan.crown, 1.5);
  near(plan.crotch, (1.5 * (1 - FIGURE.crotch)) / (1 - FIGURE.crown), 0.01, "crotch height");
  near(measured.measurement.headPixels, (FIGURE.chin - FIGURE.crown) * H, 0.025 * H, "head");
  near(measured.measurement.heads, (1 - FIGURE.crown) / (FIGURE.chin - FIGURE.crown), 0.3, "heads");
  const toTop = measureView(readSheet(image), 1, { height: 1.5, heightTo: "top" }).measurement;
  assert.equal(toTop.plan.top, 1.5);
  assert.equal(measured.measurement.ground, TOP + H);
});

test("the width profile has a row for every figure row, with two runs between the legs", () => {
  const profile = measured.measurement.profile;
  assert.equal(profile.length, H);
  const leg = profile.find((r) => r.y === Math.round(row(0.8)))!;
  assert.equal(leg.runs, 2);
  near(leg.right - leg.left, (0.18 * 1.5) / (1 - FIGURE.crown), 0.01, "leg span");
});

test("both arms and ears stand apart as pieces with their tips", () => {
  const kinds = measured.measurement.pieces.map((p) => `${p.kind} ${p.side}`).sort();
  assert.deepEqual(kinds, ["arm image-left", "arm image-right", "ear image-left", "ear image-right"]);
});

test("landmarks= marks replace what is found, in the image's pixels, and the head unit follows them", () => {
  const given = measureSheet(image, { height: 1.5, landmarks: "crotch:130,250;chin:130,110" });
  assert.equal(given.landmarks.crotch.status, "given");
  assert.equal(given.landmarks.crotch.y, 250);
  near(given.plan.crotch, (1.5 * (TOP + H - 250)) / (TOP + H - given.landmarks.crown.y), 0.001, "given crotch");
  assert.equal(given.headPixels, 110 - given.landmarks.crown.y);
});

test("an ear or bun standing over the axis is left above the crown, so the head is the skull", () => {
  const bun = sheet(260, 360);
  figure(bun, 130, TOP + 30, H - 30);
  ellipse(bun, 130, TOP + 40, 8, 20, [90, 150, 210]);
  const m = measureSheet(bun, { height: 1 }).landmarks;
  near(m.crown.y, TOP + 30 + FIGURE.crown * (H - 30), 0.02 * H, "crown under the bun");
  assert.equal(m.top.y, TOP + 20);
});

test("box= measures just that part of the sheet", () => {
  const two = sheet(520, 360);
  figure(two, 130, TOP, H);
  figure(two, 390, 60, 260);
  const second = measureSheet(two, { box: { x: 260, y: 0, w: 260, h: 360 }, height: 1 });
  assert.equal(second.ground, 320);
  assert.equal(second.view.box.h, 260);
});
