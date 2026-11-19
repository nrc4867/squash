import ts from "typescript";
import { join, relative, resolve } from "node:path";
import { kitDir } from "../src/run/paths.ts";
import { kitEntries } from "./specifiers.ts";

/** The compiler options a character file is checked under: strict, and only syntax Node can strip. */
export function characterOptions(): ts.CompilerOptions {
  return {
    target: ts.ScriptTarget.ESNext,
    module: ts.ModuleKind.Preserve,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
    allowImportingTsExtensions: true,
    noEmit: true,
    strict: true,
    erasableSyntaxOnly: true,
    verbatimModuleSyntax: true,
    skipLibCheck: true,
    types: ["node"],
    typeRoots: [join(kitDir, "node_modules", "@types")],
    paths: Object.fromEntries(Object.entries(kitEntries).map(([specifier, file]) => [specifier, [file]])),
  };
}

/** One problem as `file:line:column: message`, the file relative to where the runner was started. */
function describe(diagnostic: ts.Diagnostic): string {
  const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n");
  if (!diagnostic.file || diagnostic.start === undefined) return `error TS${diagnostic.code}: ${message}`;
  const { line, character } = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
  const file = relative(process.cwd(), diagnostic.file.fileName) || diagnostic.file.fileName;
  return `${file}:${line + 1}:${character + 1}: error TS${diagnostic.code}: ${message}`;
}

/** Typechecks a character file against the kit's types; returns its problems, none when it is sound. */
export function typecheck(path: string): string[] {
  const program = ts.createProgram([resolve(path)], characterOptions());
  return ts.getPreEmitDiagnostics(program).map(describe);
}
