import { test } from "node:test";
import assert from "node:assert/strict";
import { faired, gaussianSmoothed } from "../src/geom/fair.ts";
import { roundClose } from "../src/sheet/core/distance.ts";
import { blankMask } from "../src/sheet/core/mask.ts";
import { buildBody } from "../src/propose/creature/body.ts";
import { drawnHead } from "../src/propose/creature/drawn/head.ts";
import { unimodal } from "../src/propose/creature/drawn/profile.ts";
import { figureOutline } from "../src/propose/creature/outline.ts";
import { ParamValues, planParams } from "../src/propose/creature/params.ts";
import { loadPlan } from "../src/propose/creature/plan.ts";
import { scaleAlong } from "../src/propose/creature/widths.ts";

/** The turns, in degrees, between neighbouring segments of a run, largest first, its corners aside. */
function turns(points: ReadonlyArray<readonly [number, number]>, corners: readonly number[] = []): number[] {
  const out: number[] = [];
  for (let i = 1; i + 1 < points.length; i++) {
    if (corners.includes(i)) continue;
    const a = Math.atan2(points[i][1] - points[i - 1][1], points[i][0] - points[i - 1][0]);
    const b = Math.atan2(points[i + 1][1] - points[i][1], points[i + 1][0] - points[i][0]);
    let d = Math.abs(b - a);
    if (d > Math.PI) d = 2 * Math.PI - d;
    out.push((d * 180) / Math.PI);
  }
  return out.sort((x, y) => y - x);
}

test("a noisy traced run fairs into a few even places, kept sharp only at a real corner", () => {
  const run: Array<[number, number]> = [];
  for (let i = 0; i <= 200; i++) run.push([i / 200 + (i % 2 ? 0.004 : -0.004), 0]);
  for (let i = 1; i <= 200; i++) run.push([1 + (i % 2 ? 0.004 : -0.004), i / 200]);
  const fair = faired(run, { step: 0.005, smoothing: 0.03, spacing: 0.1, cornerDegrees: 60, cornerSpan: 0.03 });
  assert.ok(fair.places.length >= 15 && fair.places.length <= 25, `${fair.places.length} places`);
  assert.equal(fair.corners.length, 1, "the right angle is the one corner");
  const corner = fair.places[fair.corners[0]];
  assert.ok(Math.hypot(corner[0] - 1, corner[1]) < 0.03, `the corner stays at the turn: ${corner}`);
  assert.ok(fair.places.every(([x, y]) => Math.abs(y) < 0.01 || Math.abs(x - 1) < 0.01), "the wobble is smoothed away");
});

test("smoothing holds a run's ends", () => {
  const run: Array<[number, number]> = [[0, 0], [1, 1], [2, 0], [3, 1], [4, 0]];
  const smooth = gaussianSmoothed(run, 1);
  assert.deepEqual(smooth[0], [0, 0]);
  assert.deepEqual(smooth[4], [4, 0]);
});

test("a round closing fills an inward corner with an arc and leaves the outside alone", () => {
  const mask = blankMask(60, 60);
  for (let y = 10; y < 50; y++) for (let x = 10; x < 50; x++) if (!(x >= 30 && y < 30)) mask.data[y * 60 + x] = 1;
  const closed = roundClose(mask, 8);
  assert.equal(closed.data[28 * 60 + 32], 1, "the notch's inner corner fills");
  assert.equal(closed.data[12 * 60 + 48], 0, "far from the corner the notch stays open");
  assert.equal(closed.data[5 * 60 + 5], 0, "nothing grows outside");
});

test("a radius profile only rises to its widest and falls after it", () => {
  assert.deepEqual(unimodal([0.1, 0.3, 0.25, 0.28, 0.5, 0.4, 0.42, 0.1]), [0.1, 0.25, 0.25, 0.28, 0.5, 0.4, 0.4, 0.1]);
});

test("width scales run through their places as one smooth curve", () => {
  const scales = [1, 1.2, 0.8, 1.1, 1];
  assert.equal(scaleAlong(scales, 0), 1);
  assert.ok(Math.abs(scaleAlong(scales, 0.5) - 0.8) < 1e-9);
  assert.ok(Math.abs(scaleAlong(scales, 1) - 1) < 1e-9);
  assert.ok(scaleAlong(scales, 0.375) < 1.2 && scaleAlong(scales, 0.375) > 0.8);
});

test("a proposed biped's sides are few-placed smooth curves, turning sharply only at an armpit", () => {
  const plan = loadPlan("biped");
  const rest = buildBody(plan, new ParamValues(planParams(plan, ["front", "side"])), false);
  const outline = figureOutline(rest, 0.3);
  for (const stroke of [...outline.front, ...outline.side]) {
    assert.ok(stroke.points.length <= 24, `${stroke.id}: ${stroke.points.length} places`);
    const [, second = 0] = turns(stroke.points, stroke.corners);
    assert.ok(second < 40, `${stroke.id} turns ${second.toFixed(0)} degrees between places, away from its sharpest turn`);
  }
});

test("a head no view reads is drawn from its ellipsoid, round and closed, an upright one narrowing to its chin", () => {
  const plan = loadPlan("biped");
  const posed = buildBody(plan, new ParamValues(planParams(plan, ["front"])), true);
  const head = posed.ends.find((e) => e.end.id === plan.head)!;
  const drawn = drawnHead({ views: new Map(), places: new Map(), layers: [] }, head, [], "upright");
  assert.ok(Math.abs(drawn.length - 2 * head.radii[1]) < 1e-6, "as tall as the head");
  const left = drawn.profiles.left;
  const widest = Math.max(...left);
  assert.ok(Math.abs(widest - head.radii[2]) < 0.05 * head.radii[2], `as wide as the head: ${widest} against ${head.radii[2]}`);
  assert.ok(left[1] < drawn.profiles.left[left.length - 2], "narrower at the chin than at the crown");
});
