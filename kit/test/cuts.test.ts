import { test } from "node:test";
import assert from "node:assert/strict";
import { bluntBob, braid, bun, buzzCut, fade, flatTop, hairShell, trim, undercut } from "squash-kit";

test("the short-hair helpers name their preset and set the shell's fade", () => {
  assert.deepEqual(buzzCut({ on: "head" }), { on: "head", preset: "buzzCut" });
  assert.deepEqual(fade({ on: "head", level: "low" }).shell, { fade: "low" });
  assert.deepEqual(fade({ on: "head", level: "mid", shell: hairShell({ fadeTo: 0.2 }) }).shell, { fade: "mid", fadeTo: 0.2 });
  assert.deepEqual(undercut({ on: "head", sides: "high" }).shell, { fade: "high" });
  assert.equal(undercut({ on: "head" }).preset, "undercut");
});

test("trims cut a preset's groups level", () => {
  assert.deepEqual(trim(-0.5), { level: -0.5 });
  assert.deepEqual(trim(1.2, 10, false), { level: 1.2, tilt: 10, blunt: false });
  assert.deepEqual(flatTop({ on: "head", height: 1.4 }).groups?.map((g) => [g.id, g.trim?.level]), [["top", 1.4], ["crown", 1.4], ["fringe", 1.4]]);
  assert.equal(flatTop({ on: "head" }).groups, undefined);
  const bob = bluntBob({ on: "head", hem: -0.8, bangs: 0.3 });
  assert.deepEqual(bob.groups?.map((g) => g.id), ["back", "left", "right", "fringe"]);
  assert.equal(bob.groups?.[3].trim?.level, 0.3);
});

test("braids and buns pick one or two and place a low bun at the nape", () => {
  assert.equal(braid({ on: "head" }).preset, "braid");
  assert.equal(braid({ on: "head", twin: true, band: "ribbon" }).preset, "twinBraids");
  assert.equal(bun({ on: "head", space: true }).preset, "spaceBuns");
  assert.deepEqual(bun({ on: "head", low: true }).groups?.[0], { id: "bun", kind: "back", spread: 0, height: -0.7, rise: 45 });
});
