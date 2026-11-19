/** What the runner was asked: a command, the character file, and the options and questions after it. */
export interface RunArgs {
  command: string;
  file: string;
  /** Everything after the file, passed on to tools/recipe.sh. */
  rest: string[];
  /** The out= folder, when given: the recipe JSON goes there, and recipe.sh writes its build there. */
  out?: string;
  /** For `sheet`, its own command; `file` is then the sheet image. */
  sheet?: string;
}

export const COMMANDS = ["build", "check", "json", "probe", "heights", "sheet"] as const;
export type Command = (typeof COMMANDS)[number];

export const USAGE = `usage: tools/recipe.sh kit [build|check|json|probe|heights] <character.ts> [options ...]
       tools/recipe.sh kit sheet <views|measure|grid|zoom|palette|profiles|trace> <image> [options ...]
  build (the default)  typecheck, run, write <name>.recipe.json beside the file, build and render it with tools/recipe.sh
  check                typecheck only
  json                 typecheck, run and write the recipe JSON, no build
  probe                typecheck, run, write the JSON, then tools/recipe.sh probe with the questions after the file
  heights              typecheck, run, write the JSON, probe it, and print its exported "heights" planned against built
  sheet                read a character sheet image: views, measurements, grids, zooms, palette, profiles, traces
Options after the file go to tools/recipe.sh (render=false, outfits=all, close=face,hand,foot, ...);
out=<folder> puts the recipe JSON and the build there.`;

export function isCommand(word: string): word is Command {
  return (COMMANDS as readonly string[]).includes(word);
}

export function parseArgs(argv: readonly string[]): RunArgs | undefined {
  const words = [...argv];
  const command = words.length > 0 && isCommand(words[0]) ? words.shift()! : "build";
  const sheet = command === "sheet" ? words.shift() : undefined;
  const file = words.shift();
  if (!file) return undefined;
  const out = words.find((word) => word.startsWith("out="))?.slice("out=".length);
  return { command, file, rest: words, out, sheet };
}
