import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { kitDir } from "../src/run/paths.ts";

/** Files present once the kit's own dependencies are installed. */
const INSTALLED_MARKS = ["typescript", "pngjs", "jpeg-js"].map((name) => join(kitDir, "node_modules", name, "package.json"));
/** One shell line, so npm is found as npm.cmd on Windows too. */
const INSTALL = "npm ci --no-audit --no-fund --loglevel=error";

/** Installs the kit's own dependencies inside kit/ (once, from its lock file) when they are missing. */
export function ensureDependencies(): void {
  if (INSTALLED_MARKS.every((mark) => existsSync(mark))) return;
  console.error("squash-kit: installing the kit's dependencies inside kit/ (once)");
  const result = spawnSync(INSTALL, { cwd: kitDir, stdio: "inherit", shell: true });
  if (result.status !== 0 || !INSTALLED_MARKS.every((mark) => existsSync(mark))) throw new Error("squash-kit: `npm ci` in kit/ failed");
}
