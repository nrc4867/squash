// The runner on character files outside the repo, with no package.json or install beside them.
import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { kitDir } from "../src/run/paths.ts";

const runner = join(kitDir, "run.ts");

function outside(name: string, source: string): { dir: string; file: string } {
  const dir = mkdtempSync(join(tmpdir(), "squash-kit-"));
  const file = join(dir, name);
  writeFileSync(file, source);
  return { dir, file };
}

function run(dir: string, ...args: string[]) {
  return spawnSync(process.execPath, [runner, ...args], { cwd: dir, encoding: "utf8" });
}

test("a type error in an outside file is reported with its file and line, and nothing is written", (t) => {
  const { dir } = outside(
    "broken.ts",
    'import { recipe } from "squash-kit";\n\nexport default recipe({\n  id: "broken",\n  name: 3,\n});\n',
  );
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const result = run(dir, "json", "broken.ts");
  assert.equal(result.status, 2);
  assert.match(result.stderr, /^broken\.ts:5:3: error TS2322: /m);
  assert.equal(existsSync(join(dir, "broken.recipe.json")), false);
});

test("a sound outside file is run and its recipe written beside it", (t) => {
  const { dir } = outside(
    "blob.ts",
    [
      'import { oval, outline, palette, recipe } from "squash-kit";',
      'const colors = palette({ fur: "#c08040" });',
      "const disc = oval({ center: [0.1, 0], rx: 0.1, ry: 0.08 });",
      "export default recipe({",
      '  id: "blob", name: "Blob", plan: "blank", palette: colors.slots,',
      '  parts: [{ id: "body", template: "blob", color: colors.slot("fur"), outlines: { side: outline(disc), top: outline(disc) } }],',
      "});",
      "",
    ].join("\n"),
  );
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const result = run(dir, "json", "blob.ts");
  assert.equal(result.status, 0, result.stderr);
  const written = JSON.parse(readFileSync(join(dir, "blob.recipe.json"), "utf8"));
  assert.equal(written.format, 1);
  assert.equal(written.parts[0].outlines.side.closed, true);
});
