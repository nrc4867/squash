// Landmark confidence and checks on invented people: arms in several poses, sleeves, a loose shirt and a big head.
import { test } from "node:test";
import assert from "node:assert/strict";
import { checkImage, checkLandmarks, gridImage, measureView, readSheet, type Landmark } from "squash-kit/sheet";
import { person, type PersonOptions } from "./sheet/people.ts";

function read(options: PersonOptions) {
  const { image, truth } = person(options);
  const sheet = readSheet(image);
  const measured = measureView(sheet, 1, { height: 1.7 });
  return { sheet, truth, measured, marks: measured.measurement.landmarks as Record<string, Landmark> };
}

function near(actual: number, expected: number, slack: number, what: string): void {
  assert.ok(Math.abs(actual - expected) <= slack, `${what}: ${actual}, expected ${expected} within ${slack}`);
}

test("arms in an A-pose: the shoulder is where the outline leaves the neck and the armpit where the arm parts from the torso", () => {
  const { marks, truth, measured } = read({ arms: "a-pose" });
  near(marks.shoulder.y, truth.shoulder, 10, "shoulder");
  assert.equal(marks.armpit.status, "found");
  const head = measured.measurement.headPixels;
  assert.ok(marks.armpit.y > marks.shoulder.y && marks.armpit.y - marks.shoulder.y < 0.6 * head, `armpit ${marks.armpit.y} below shoulder ${marks.shoulder.y}`);
  assert.deepEqual(measured.measurement.checks, []);
});

test("sleeves and a collar band in another colour do not move the shoulder or the armpit", () => {
  const bare = read({ arms: "a-pose" }).marks;
  const sleeved = read({ arms: "a-pose", sleeves: true });
  near(sleeved.marks.shoulder.y, bare.shoulder.y, 6, "shoulder");
  assert.ok(sleeved.marks.armpit.y >= sleeved.marks.shoulder.y);
  assert.ok(sleeved.marks.armpit.y - sleeved.marks.shoulder.y < 0.6 * sleeved.measured.measurement.headPixels);
});

test("arms held against the body leave no gap: the armpit is estimated and says why", () => {
  const { marks, truth } = read({ arms: "against" });
  assert.equal(marks.armpit.status, "estimated");
  assert.match(marks.armpit.reason ?? "", /no gap/);
  assert.ok(marks.armpit.y > truth.shoulder && marks.armpit.y < truth.crotch);
  near(marks.shoulder.y, truth.shoulder, 14, "shoulder");
});

test("arms raised: the armpit is estimated, below the shoulder and above the crotch, and the reason says the arms are raised", () => {
  const { marks, truth } = read({ arms: "raised" });
  assert.equal(marks.armpit.status, "estimated");
  assert.match(marks.armpit.reason ?? "", /raised/);
  assert.ok(marks.armpit.y > marks.shoulder.y, `armpit ${marks.armpit.y} shoulder ${marks.shoulder.y}`);
  assert.ok(marks.armpit.y < truth.crotch);
  near(marks.shoulder.y, truth.shoulder, 20, "shoulder");
});

test("a loose shirt flaring over the hips does not make the armpit or the shoulder", () => {
  const { marks, truth, measured } = read({ arms: "a-pose", looseShirt: true });
  near(marks.shoulder.y, truth.shoulder, 10, "shoulder");
  assert.ok(marks.armpit.y - marks.shoulder.y < 0.6 * measured.measurement.headPixels);
});

test("a big head keeps the shoulder close below the chin", () => {
  const { marks, truth, measured } = read({ arms: "a-pose", headRadius: 75 });
  assert.ok(marks.shoulder.y - truth.chin < measured.measurement.headPixels);
  assert.ok(marks.shoulder.y > marks.chin.y);
  assert.equal(marks.armpit.status, "found");
});

test("given landmarks are never made unsure by a check, but the check is still reported", () => {
  const { image } = person({ arms: "a-pose" });
  const given = measureView(readSheet(image), 1, { landmarks: "armpit:200,700" });
  assert.equal(given.measurement.landmarks.armpit.status, "given");
  assert.ok(given.measurement.checks.length > 0);
});

test("checks name the landmarks that break a proportion, mark the found ones unsure and keep the reason", () => {
  const marks = {
    crown: { y: 0, status: "found" as const },
    chin: { y: 60, status: "found" as const },
    shoulder: { y: 100, status: "found" as const },
    armpit: { y: 250, status: "found" as const },
    waist: { y: 270, status: "found" as const },
    crotch: { y: 300, status: "found" as const },
    ground: { y: 600, status: "found" as const },
  };
  const { marks: checked, issues } = checkLandmarks(marks, 60);
  assert.equal(checked.armpit.status, "unsure");
  assert.match(checked.armpit.reason ?? "", /heads below the shoulder/);
  assert.equal(checked.crotch.status, "found");
  assert.ok(issues.some((i) => i.landmarks.includes("shoulder") && i.landmarks.includes("armpit")));
  const inverted = checkLandmarks({ ...marks, knee: { y: 280, status: "estimated" as const }, ankle: { y: 590, status: "found" as const } }, 60);
  assert.ok(inverted.issues.some((i) => i.landmarks.includes("knee")));
  assert.equal(inverted.marks.knee.status, "estimated");
});

test("the grid and the check image draw every landmark, the check image a tile each", () => {
  const { sheet, measured } = read({ arms: "against" });
  const grid = gridImage(sheet, measured.anatomy, measured.measurement);
  const check = checkImage(sheet, measured.anatomy, measured.measurement);
  assert.ok(grid.height > 0 && check.width > 3 * 300);
  const tiles = Math.ceil(Object.keys(measured.measurement.landmarks).filter((n) => !["top", "ground", "tailTop", "tailBottom"].includes(n)).length / 3);
  assert.ok(check.height > tiles * 100);
});
