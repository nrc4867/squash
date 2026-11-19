// Hand, foot and face readings, the facing guess, pale parts, clothing edges and the targets, on an invented toon child.
import { test } from "node:test";
import assert from "node:assert/strict";
import { palette, targets } from "squash-kit";
import { clothEdges, measureView, readSheet, sheetProfiles, targetsOf, type Reading } from "squash-kit/sheet";
import type { ToonOptions } from "./sheet/toons.ts";
import { toon, TOON } from "./sheet/toons.ts";

function measure(options: ToonOptions = {}, read: { keepPale?: boolean } = {}) {
  const { image } = toon(options);
  const sheet = readSheet(image, read);
  return { sheet, measured: measureView(sheet, 1, { height: 1 }) };
}

function near(actual: number, expected: number, slack: number, what: string): void {
  assert.ok(Math.abs(actual - expected) <= slack, `${what}: ${actual}, expected ${expected} within ${slack}`);
}

const head = TOON.chin - TOON.crown;

test("white socks nearly the paper's colour stay in the figure, so the view reaches the ground", () => {
  const { sheet } = measure({ socks: true });
  assert.equal(sheet.views.length, 1);
  assert.equal(sheet.views[0].box.y + sheet.views[0].box.h, TOON.ground);
  const cut = measure({ socks: true }, { keepPale: false }).sheet;
  assert.ok(cut.views[0].box.y + cut.views[0].box.h < TOON.ground - 5, "without the rescue the socks are keyed away");
});

test("a view with a face reads front and one with hair over its head reads back, each saying what it rests on", () => {
  const front = measure().sheet.views[0].guess;
  const back = measure({ back: true }).sheet.views[0].guess;
  assert.equal(front.facing, "front");
  assert.equal(back.facing, "back");
  assert.ok(back.cover > 0.88 && front.cover < 0.82, `cover ${back.cover} and ${front.cover}`);
  assert.match(back.reason ?? "", /one colour/);
});

test("a tuft a few pixels off the axis does not break the head: the figure is a few heads tall, not dozens", () => {
  const { measured } = measure({ tuft: 6 });
  assert.ok(measured.measurement.heads < 8, `heads ${measured.measurement.heads}`);
  near(measured.anatomy.marks.chin.y, TOON.chin, 12, "chin");
});

test("hands are read by the skin colour at the sleeves' ends: length and width as shares of the head", () => {
  const { measured } = measure();
  const { handLength, handWidth } = measured.measurement.proportions;
  assert.equal(handLength.status, "found");
  near(handLength.ofHead, (2 * TOON.handRy) / head, 0.1, "hand length");
  near(handWidth.ofHead, (2 * TOON.handRx) / head, 0.08, "hand width");
  near(handLength.ofHeight, handLength.value / measured.measurement.height, 0.01, "share of height");
});

test("a bigger hand reads bigger", () => {
  const small = measure().measured.measurement.proportions.handLength.ofHead;
  const big = measure({ hand: { rx: 18, ry: 34 } }).measured.measurement.proportions.handLength.ofHead;
  assert.ok(big > 1.25 * small, `${big} against ${small}`);
});

test("feet: the width is read from a front view and the length is said to be out of reach", () => {
  const { measured } = measure();
  const { footWidth } = measured.measurement.proportions;
  assert.ok(footWidth, JSON.stringify(measured.measurement.unread));
  near(footWidth.ofHead, (2 * TOON.footRx) / head, 0.15, "foot width");
  assert.match(measured.measurement.unread.footLength, /foreshortens/);
});

test("the face: the eye line, the eyes' width and their spacing", () => {
  const p = measure().measured.measurement.proportions;
  assert.equal(p.eyeLine.status, "found");
  near(p.eyeLine.ofHead, (TOON.eyeY - TOON.crown) / head, 0.06, "eye line");
  near(p.eyeWidth.ofHead, (2 * TOON.eyeRx) / head, 0.06, "eye width");
  near(p.eyeSpacing.ofHead, TOON.eyeApart / head, 0.06, "eye spacing");
  assert.equal(p.eyeLine.kind, "place");
  assert.equal(p.eyeWidth.kind, "size");
});

test("a back view reads no face, and says why", () => {
  const { measured } = measure({ back: true });
  assert.equal(measured.measurement.proportions.eyeLine, undefined);
  assert.match(measured.measurement.unread.eyeLine, /back view/);
});

test("given wrist and hand tip marks set a hand's length", () => {
  const { sheet } = measure();
  const given = measureView(sheet, 1, { height: 1, landmarks: "wrist:300,330;handTip:304,400" });
  const reading: Reading = given.measurement.proportions.handLength;
  assert.equal(reading.status, "given");
  near(reading.ofHead * given.measurement.headPixels, Math.hypot(4, 70), 1, "length in pixels");
});

test("targetsOf keeps the readings a build can be held to, as shares of the head", () => {
  const { measured } = measure();
  const list = targetsOf(measured.measurement);
  assert.ok(list.length > 0);
  for (const t of list) {
    assert.equal(t.of, "head");
    assert.equal(t.value, measured.measurement.proportions[t.name].ofHead);
    assert.ok(["found", "given"].includes(measured.measurement.proportions[t.name].status));
  }
  assert.ok(targetsOf(measured.measurement, true).length >= list.length);
});

test("a clothing edge is a row where the colour across the body jumps", () => {
  const { sheet, measured } = measure();
  const edges = clothEdges(sheet.image, measured.anatomy.core, measured.anatomy.figure, measured.anatomy.marks.neck.y);
  assert.ok(edges.some((e) => Math.abs(e.y - TOON.hemRow) <= 4), `edges ${JSON.stringify(edges)}`);
});

test("profiles from a view guessed three-quarter warn, and every radius is low confidence", () => {
  const { sheet } = measure();
  sheet.views[0].guess = { ...sheet.views[0].guess, facing: "three-quarter", confidence: 0.4 };
  const front = measureView(sheet, 1, { height: 1 });
  const profiles = sheetProfiles(front);
  assert.ok(profiles.warnings.some((w) => /three-quarter/.test(w)));
  assert.ok(profiles.torso === undefined || profiles.torso.confidence === "low");
  assert.ok(front.measurement.warnings.some((w) => /three-quarter/.test(w)));
  assert.notEqual(front.measurement.landmarks.hip.status, "found");
});

test("targets reads shares of the head and of the height, and refuses a name it does not measure", () => {
  assert.deepEqual(targets({ handLength: "0.4 head", footLength: "0.12 height" }), [
    { name: "handLength", of: "head", value: 0.4 },
    { name: "footLength", of: "height", value: 0.12 },
  ]);
  assert.deepEqual(targets({ eyeLine: { of: "head", value: 0.55, tolerance: 0.02 } }, { tolerance: 0.1 }), [{ name: "eyeLine", of: "head", value: 0.55, tolerance: 0.02 }]);
  assert.equal(targets({ handWidth: "0.2 head" }, { tolerance: 0.1 })[0].tolerance, 0.1);
  assert.throws(() => targets({ handLength: "long" }), /not a share/);
  assert.throws(() => targets({ shoeSize: "1 head" } as never), /not a measured proportion/);
});

test("a palette slot takes its own shadow tone", () => {
  const colors = palette({ skin: "#d8b48e", ink: "#101010" }, { shadow: { skin: "#b08a74" } });
  assert.deepEqual(colors.slots[0], { slot: "skin", color: "#d8b48e", shadow: "#b08a74" });
  assert.deepEqual(colors.slots[1], { slot: "ink", color: "#101010" });
});
