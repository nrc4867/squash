// Foot and hand sizes on invented figures: touching feet, widths near lengths, views that disagree, and feet traced in their own frame.
import { test } from "node:test";
import assert from "node:assert/strict";
import { measureView, readAcross, readSheet, traceShape, type Measurement, type Reading } from "squash-kit/sheet";
import { ellipse, FUR, rect, sheet } from "./sheet/figures.ts";
import { toon } from "./sheet/toons.ts";

const H = 240;
const CX = 120;
const TOP = 20;

/** A front view whose feet touch off the axis: the left foot under its leg, the right one turned out, meeting at x(0.04). */
function touchingFeet() {
  const image = sheet(260, 280);
  const y = (share: number) => TOP + share * H;
  const x = (share: number) => CX + share * H;
  ellipse(image, CX, y(0.16), 0.11 * H, 0.11 * H, FUR);
  rect(image, x(-0.03), y(0.25), x(0.03), y(0.31), FUR);
  rect(image, x(-0.1), y(0.3), x(0.1), y(0.62), FUR);
  rect(image, x(-0.09), y(0.6), x(-0.02), y(0.95), FUR);
  rect(image, x(0.02), y(0.6), x(0.09), y(0.95), FUR);
  ellipse(image, x(-0.025), y(0.97), 0.066 * H, 0.03 * H, FUR);
  ellipse(image, x(0.105), y(0.97), 0.066 * H, 0.03 * H, FUR);
  return measureView(readSheet(image), 1, { height: 1 });
}

/** A side view facing the image's right: one leg over a foot whose heel reaches back 20 pixels and toes forward 40. */
function sideFoot() {
  const image = sheet(200, 240);
  ellipse(image, 100, 50, 30, 30, FUR);
  ellipse(image, 135, 60, 16, 8, FUR);
  rect(image, 75, 80, 125, 160, FUR);
  rect(image, 88, 160, 112, 205, FUR);
  rect(image, 80, 205, 140, 220, FUR);
  return measureView(readSheet(image), 1, { height: 1 });
}

test("touching feet in a front view read each foot's own width, split at the notch between them, not their span", () => {
  const measured = touchingFeet();
  const footWidth = measured.measurement.proportions.footWidth;
  assert.ok(footWidth, JSON.stringify(measured.measurement.unread));
  assert.equal(footWidth.side, "both");
  const own = 2 * 0.066 * H;
  for (const line of footWidth.lines) {
    const w = line.to.x - line.from.x;
    assert.ok(Math.abs(w - own) <= 4, `a foot ${w} pixels wide, its own width ${own.toFixed(1)}`);
  }
  assert.match(footWidth.reason ?? "", /notch/);
});

test("a hand nearly as wide as it is long is unsure, and says so", () => {
  const measured = measureView(readSheet(toon({ hand: { rx: 22, ry: 24 } }).image), 1, { height: 1 });
  const handWidth = measured.measurement.proportions.handWidth;
  assert.equal(handWidth.status, "unsure");
  assert.match(handWidth.reason ?? "", /of its length wide/);
});

/** A copy of a measurement as another view, holding only the given sizes as shares of the head. */
function asView(m: Measurement, name: string, facing: "front" | "back" | "side", sizes: Record<string, number>): Measurement {
  const proportions = Object.fromEntries(
    Object.entries(sizes).map(([key, ofHead]): [string, Reading] => [key, { name: key, kind: "size", value: ofHead * m.head, ofHead, ofHeight: ofHead / m.heads, status: "found", lines: [] }]),
  );
  return { ...m, view: { ...m.view, name, guess: { ...m.view.guess, facing } }, proportions };
}

test("views that disagree on a size are unsure and named; a foot width near its length is unsure", () => {
  const base = sideFoot().measurement;
  const across = readAcross([asView(base, "front", "front", { footWidth: 0.3 }), asView(base, "back", "back", { footWidth: 0.42 }), asView(base, "side", "side", { footLength: 0.9 })]);
  assert.equal(across.footWidth.status, "unsure");
  assert.match(across.footWidth.reason ?? "", /back reads 0\.42 heads, front 0\.30/);
  assert.equal(across.footLength.status, "found");
  const agreeing = readAcross([asView(base, "front", "front", { footWidth: 0.3 }), asView(base, "back", "back", { footWidth: 0.32 }), asView(base, "side", "side", { footLength: 0.9 })]);
  assert.equal(agreeing.footWidth.status, "found");
  const near = readAcross([asView(base, "front", "front", { footWidth: 0.5 }), asView(base, "side", "side", { footLength: 0.6 })]);
  assert.equal(near.footWidth.status, "unsure");
  assert.match(near.footWidth.reason ?? "", /of the length wide/);
});

test("a foot traced as=side runs heel negative to toes positive, y up from the ankle", () => {
  const { anatomy, measurement } = sideFoot();
  const trace = traceShape(anatomy, measurement, { part: "foot-left", as: "side" });
  const outline = trace.outline!;
  const xs = outline.through.map((p) => p[0]);
  const ys = outline.through.map((p) => p[1]);
  const px = measurement.bodyPerPixel;
  assert.ok(Math.abs(Math.min(...xs) + 20 * px) <= 2 * px, `heel at ${Math.min(...xs)}`);
  assert.ok(Math.abs(Math.max(...xs) - 40 * px) <= 2 * px, `toes at ${Math.max(...xs)}`);
  assert.ok(Math.max(...ys) <= px && Math.min(...ys) < -10 * px, `y from ${Math.min(...ys)} to ${Math.max(...ys)}`);
  assert.ok(outline.through.length >= 4 && outline.through.length <= 14, `${outline.through.length} points`);
  assert.match(outline.line, /^side: \{ through: \[\[/);
  assert.ok(Math.abs(outline.size.length - 60 * px) <= 2 * px);
});

test("a foot traced as=front is its section in -1 to 1, mirrored onto the recipe's left foot", () => {
  const { anatomy, measurement } = touchingFeet();
  const trace = traceShape(anatomy, measurement, { part: "foot-left", as: "front" });
  const outline = trace.outline!;
  for (const [x, y] of outline.through) assert.ok(Math.abs(x) <= 1 && Math.abs(y) <= 1, `${x}, ${y}`);
  assert.equal(Math.max(...outline.through.map((p) => p[0])), 1);
  assert.equal(Math.min(...outline.through.map((p) => p[1])), -1);
  assert.ok(outline.notes.some((n) => /mirrored in x/.test(n)) || outline.notes.some((n) => /character's left/.test(n)), outline.notes.join("; "));
});
