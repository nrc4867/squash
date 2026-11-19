import { existsSync } from "node:fs";
import { recipePathFor, writeRecipe } from "../src/run/write.ts";
import { probeRecipe, runRecipeSh } from "../src/run/recipe-sh.ts";
import { checkHeights, formatHeightChecks } from "../src/plan/check.ts";
import { parseArgs, USAGE, type Command, type RunArgs } from "./args.ts";
import { ensureDependencies } from "./install.ts";
import { loadCharacter, type Character } from "./load.ts";
import { runSheet } from "./sheet/run.ts";

/** The exit code for a problem in the character file, as tools/recipe.sh's for a recipe error. */
const EXIT_PROBLEM = 2;

async function checked(file: string): Promise<boolean> {
  ensureDependencies();
  const { typecheck } = await import("./typecheck.ts");
  const problems = typecheck(file);
  for (const problem of problems) console.error(problem);
  return problems.length === 0;
}

/** Typechecks, runs and writes the character file's recipe; undefined when it does not typecheck. */
async function written(args: RunArgs): Promise<(Character & { path: string }) | undefined> {
  if (!(await checked(args.file))) return undefined;
  const character = await loadCharacter(args.file);
  const path = writeRecipe(character.recipe, recipePathFor(args.file, args.out));
  console.log(path);
  return { ...character, path };
}

/** The options after the file that recipe.sh's probe takes: its questions, without out=. */
function questions(args: RunArgs): string[] {
  return args.rest.filter((word) => !word.startsWith("out="));
}

const commands: Record<Command, (args: RunArgs) => Promise<number>> = {
  async sheet(args) {
    ensureDependencies();
    return runSheet(args.sheet, args.file, args.rest);
  },
  async check(args) {
    return (await checked(args.file)) ? 0 : EXIT_PROBLEM;
  },
  async json(args) {
    return (await written(args)) ? 0 : EXIT_PROBLEM;
  },
  async build(args) {
    const character = await written(args);
    if (!character) return EXIT_PROBLEM;
    return (await runRecipeSh([character.path, ...args.rest])).code;
  },
  async probe(args) {
    const character = await written(args);
    if (!character) return EXIT_PROBLEM;
    return (await runRecipeSh(["probe", character.path, ...questions(args)])).code;
  },
  async heights(args) {
    const character = await written(args);
    if (!character) return EXIT_PROBLEM;
    if (!character.heights) {
      console.error(`${args.file}: exports no \`heights\`; add \`export const heights = heightMarks(plan, { ... })\``);
      return EXIT_PROBLEM;
    }
    const probed = await probeRecipe(character.path, ["part=all"], true);
    if (probed.code !== 0) {
      process.stderr.write(probed.output);
      return probed.code;
    }
    console.log(formatHeightChecks(checkHeights(probed.report, character.heights.heights, character.heights.marks)));
    return 0;
  },
};

export async function main(argv: readonly string[]): Promise<number> {
  const args = parseArgs(argv);
  if (!args) {
    console.error(USAGE);
    return EXIT_PROBLEM;
  }
  if (!existsSync(args.file)) {
    console.error(`squash-kit: no file ${args.file}`);
    return EXIT_PROBLEM;
  }
  try {
    return await commands[args.command as Command](args);
  } catch (error) {
    console.error(error instanceof Error ? (error.stack ?? error.message) : String(error));
    return EXIT_PROBLEM;
  }
}
