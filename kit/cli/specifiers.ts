import { join } from "node:path";
import { KIT_SPECIFIER, kitIndexPath, kitSourceDir } from "../src/run/paths.ts";

/** What a character file may import the kit by, and the file each resolves to. */
export const kitEntries: Readonly<Record<string, string>> = {
  [KIT_SPECIFIER]: kitIndexPath,
  [`${KIT_SPECIFIER}/run`]: join(kitSourceDir, "run", "index.ts"),
};
