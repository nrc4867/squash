import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { Ajv2020 } from "ajv/dist/2020.js";
import { buildBody } from "../src/propose/creature/body.ts";
import { fitEvidence } from "../src/propose/creature/evidence.ts";
import { partFrame } from "../src/propose/creature/frames.ts";
import { figureOutline } from "../src/propose/creature/outline.ts";
import { ParamValues, planParams } from "../src/propose/creature/params.ts";
import { loadPlan, shippedPlans } from "../src/propose/creature/plan.ts";
import { proposalRecipe } from "../src/propose/creature/recipe.ts";
import { readSheetViews } from "../src/reader/sheet.ts";
import { editPage } from "../src/reader/page/page.ts";
import { recipeJson } from "../src/run/write.ts";
import { schemaPath } from "../src/run/paths.ts";

const SHEETS = join(dirname(fileURLToPath(import.meta.url)), "sheets");

function neutral(planName: string, views: Array<"front" | "side" | "top"> = ["front", "side", "top"]) {
  const plan = loadPlan(planName);
  const values = new ParamValues(planParams(plan, views));
  return { plan, values, rest: buildBody(plan, values, false) };
}

test("every shipped plan loads and hangs together", () => {
  for (const name of shippedPlans()) {
    const plan = loadPlan(name);
    assert.equal(plan.id, name);
    assert.ok(plan.ends.some((e) => e.id === plan.head));
  }
});

test("a part's profiles are named by its rest as the figure fit names them", () => {
  const names = (rest: "up" | "down" | "forward" | "back", kind: "trunk" | "limb") => partFrame({ rest, kind }).round.map((r) => r.name).sort();
  assert.deepEqual(names("up", "trunk"), ["back", "front", "left", "right"]);
  assert.deepEqual(names("forward", "trunk"), ["down", "left", "right", "up"]);
  assert.deepEqual(names("down", "limb"), ["back", "front", "inner", "outer"]);
});

test("a body stands on the ground with its landmarks placed", () => {
  for (const name of shippedPlans()) {
    const { rest, plan } = neutral(name);
    let low = Infinity;
    for (const part of rest.parts.values()) for (const s of part.sections) low = Math.min(low, s.center[1]);
    assert.ok(low >= 0, `${name} stands on y 0`);
    for (const mark of plan.landmarks) assert.ok(rest.landmarks.has(mark.id), `${name} places ${mark.id}`);
  }
});

test("a biped's outline is its left half with the crotch mirrored, cut at the head, hands and feet, and read from no top", () => {
  const { rest } = neutral("biped");
  const outline = figureOutline(rest, 0.3);
  assert.ok(outline.front.length >= 2);
  assert.ok(outline.front.every((s) => s.points.every(([x]) => x >= 0)), "the front view draws the left half");
  assert.ok(outline.front.some((s) => s.mirrored), "the inner leg runs on across the middle");
  assert.ok(outline.side.length >= 1);
  assert.equal(outline.top?.length ?? 0, 0, "an upright body lies level nowhere");
  const top = Math.max(...outline.front.flatMap((s) => s.points.map(([, y]) => y)));
  const chin = rest.landmarks.get("chin")![1] * 0.3;
  assert.ok(top <= chin + 0.01, `the front stops at the chin: ${top} against ${chin}`);
});

test("a quadruped's level trunk is drawn from above", () => {
  const { rest } = neutral("quadruped");
  const outline = figureOutline(rest, 0.3);
  assert.ok((outline.top?.length ?? 0) >= 1);
  assert.ok(outline.side.length >= 2, "back and belly between the legs");
});

test("a proposal's recipe places every root, numbers its sockets and passes the schema", () => {
  for (const name of shippedPlans()) {
    const { plan, rest } = neutral(name);
    const outline = figureOutline(rest, 0.3);
    const made = proposalRecipe(plan, rest, 0.3, outline, `${name}-test`);
    for (const part of made.parts ?? []) for (const [i, s] of (part.sockets ?? []).entries()) assert.equal(s.n, i + 1);
    const limbs = (made.parts ?? []).filter((p) => p.pair && p.tube);
    for (const limb of limbs) {
      const parent = made.parts!.find((p) => p.id === limb.on)!;
      const socket = parent.sockets!.find((s) => `socket.${s.n}` === limb.socket)!;
      assert.ok((socket.reach ?? 0) > 0, `${name}: ${limb.id}'s root is placed off its parent's axis`);
    }
    const schema = JSON.parse(readFileSync(schemaPath, "utf8"));
    const validate = new Ajv2020({ strict: false }).compile(schema);
    assert.ok(validate(JSON.parse(recipeJson(made))), `${name}: ${JSON.stringify(validate.errors?.slice(0, 2))}`);
  }
});

test("a sheet's views read their silhouettes where the sheet's own numbers put them", () => {
  const sheet = readSheetViews(join(SHEETS, "woman", "readings.json"));
  const truth = JSON.parse(readFileSync(join(SHEETS, "woman", "views.json"), "utf8"));
  const front = sheet.views.get("front")!;
  assert.ok(Math.abs(front.middle - truth.views.front.middle) < 3, `middle ${front.middle} against ${truth.views.front.middle}`);
  assert.ok(Math.abs(front.ground - truth.views.front.ground) < 2, `ground ${front.ground} against ${truth.views.front.ground}`);
  assert.deepEqual([...sheet.views.keys()], ["front", "side", "top"]);
});

test("a width no given view reads comes from the plan, and one under a hiding layer is inferred", () => {
  const { plan, values } = neutral("biped", ["front"]);
  const posed = buildBody(plan, values, true);
  const place = { origin: [300, 700] as [number, number], scale: 100 };
  const fit = { values, posed, places: new Map([["front" as const, place]]), cost: 0 };
  const sizes = new Map([["front" as const, [600, 800] as [number, number]]]);
  const plain = fitEvidence(fit, sizes, [], [], plan.head);
  const torsoFront = plain.measures.filter((m) => m.id.startsWith("torso.front."));
  assert.ok(torsoFront.length > 0 && torsoFront.every((m) => m.source === "default"), "the front view cannot read the torso's depth");
  assert.ok(plain.measures.filter((m) => m.id.startsWith("torso.left.")).every((m) => m.source === "read"));
  const layer = { id: "shirt", kind: "clothing", looseness: 0.06, view: "front" as const, box: [0, 0, 600, 800] as [number, number, number, number] };
  const hidden = fitEvidence(fit, sizes, [layer], [], plan.head);
  assert.ok(hidden.measures.filter((m) => m.id.startsWith("torso.left.")).every((m) => m.source === "inferred" && m.layer === "shirt"));
});

test("the edit page carries its views, evidence, held marks and layers", () => {
  const image = { width: 2, height: 2, data: new Uint8Array(16).fill(255) };
  const html = editPage([{ name: "front", image }], { marks: [{ id: "knee", view: "front", at: [1, 1], source: "read" }], measures: [] }, [], [], { clothing: 0.06 }, false);
  assert.match(html, /id="view-front"/);
  assert.match(html, /"knee"/);
});
