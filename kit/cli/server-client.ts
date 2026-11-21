// tools/recipe.sh's client for the recipe server: node kit/cli/server-client.ts --engine <version> [--watch] -- <args ...>.
// Exits with the build's code, or FALL_BACK (75) for tools/recipe.sh to build in a JVM of its own.
import { FALL_BACK, sendBuild, serverPort } from "../src/run/server.ts";

const argv = process.argv.slice(2);
const split = argv.indexOf("--");
const flags = split < 0 ? argv : argv.slice(0, split);
const engineAt = flags.indexOf("--engine");
const engine = engineAt >= 0 ? flags[engineAt + 1] : undefined;
if (!engine || split < 0) {
  process.stderr.write("usage: node kit/cli/server-client.ts --engine <version> [--watch] -- <args ...>\n");
  process.exitCode = FALL_BACK;
} else {
  process.exitCode = await sendBuild({ port: serverPort(), engine, args: argv.slice(split + 1), watch: flags.includes("--watch") });
}
