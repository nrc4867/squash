// The kit's runner: node kit/run.ts [build|check|json|probe|heights] <character.ts> [options ...]; tools/recipe.sh kit runs it.
import { main } from "./cli/main.ts";

process.exitCode = await main(process.argv.slice(2));
