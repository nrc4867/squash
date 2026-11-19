import { existsSync } from "node:fs";

/** Where Git for Windows keeps its bash, which runs tools/recipe.sh there. */
const WINDOWS_GIT_BASHES = ["C:\\Program Files\\Git\\bin\\bash.exe", "C:\\Program Files (x86)\\Git\\bin\\bash.exe"];

/**
 * The bash that runs tools/recipe.sh: SQUASH_BASH when set (tools/recipe.sh sets it to itself), else Git's bash on
 * Windows, else `bash` on the path.
 */
export function findBash(): string {
  const named = process.env.SQUASH_BASH;
  if (named) return named;
  if (process.platform === "win32") {
    const found = WINDOWS_GIT_BASHES.find((path) => existsSync(path));
    if (found) return found;
  }
  return "bash";
}
