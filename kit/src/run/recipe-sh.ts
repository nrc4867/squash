import { spawn } from "node:child_process";
import { existsSync, statSync } from "node:fs";
import { findBash } from "./bash.ts";
import { recipeShPath } from "./paths.ts";
import { parseProbe, type ProbeReport } from "../probe/parse.ts";

/** recipe.sh options as `key=value`: a list joins with commas, true and false print as words. */
export type RecipeOptions = Record<string, string | number | boolean | readonly string[]>;

export interface RecipeShResult {
  /** recipe.sh's exit code: 0 built, 2 a recipe error. */
  code: number;
  /** Everything it printed. */
  output: string;
  /** The files it wrote, from the lines it printed that name an existing file. */
  files: string[];
}

/** A printed line's label (`character: `) and timing (` (6185 ms)`), around the path it names. */
const LABEL = /^[a-z][a-z ]*: /;
const TIMING = / \(\d+ ms\)$/;

/** The files recipe.sh's output names, a line each, with or without a label and a timing. */
export function printedFiles(output: string): string[] {
  return output
    .split(/\r?\n/)
    .map((line) => line.trim().replace(LABEL, "").replace(TIMING, ""))
    .filter((path) => path.length > 0 && existsSync(path) && statSync(path).isFile());
}

export function optionArgs(options: RecipeOptions = {}): string[] {
  return Object.entries(options).map(([key, value]) => `${key}=${Array.isArray(value) ? value.join(",") : String(value)}`);
}

/** Runs tools/recipe.sh with `args`, echoing its output as it comes unless `quiet`. */
export function runRecipeSh(args: readonly string[], quiet = false): Promise<RecipeShResult> {
  return new Promise((done, fail) => {
    const child = spawn(findBash(), [recipeShPath, ...args], { stdio: ["ignore", "pipe", "pipe"] });
    let output = "";
    const take = (echo: NodeJS.WriteStream) => (chunk: Buffer) => {
      const text = chunk.toString();
      output += text;
      if (!quiet) echo.write(text);
    };
    child.stdout.on("data", take(process.stdout));
    child.stderr.on("data", take(process.stderr));
    child.on("error", fail);
    child.on("close", (code) => {
      done({ code: code ?? 1, output, files: printedFiles(output) });
    });
  });
}

/** Builds a recipe file: the character file, renders and measure.txt, by `options` (render=false, outfits=all, ...). */
export function buildRecipe(recipePath: string, options: RecipeOptions = {}, quiet = false): Promise<RecipeShResult> {
  return runRecipeSh([recipePath, ...optionArgs(options)], quiet);
}

/** Probes a built recipe: `questions` as recipe.sh takes them (part=all, uv=head:0.5,0.2, ...), read into a report. */
export async function probeRecipe(
  recipePath: string,
  questions: readonly string[] = ["part=all"],
  quiet = false,
): Promise<RecipeShResult & { report: ProbeReport }> {
  const result = await runRecipeSh(["probe", recipePath, ...questions], quiet);
  return { ...result, report: parseProbe(result.output) };
}
