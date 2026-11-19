import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { writePng } from "../../src/sheet/raster/io.ts";
import type { Raster } from "../../src/sheet/raster/image.ts";

/** Where a sheet command writes, printing each file's path as it goes. */
export class SheetOutput {
  readonly files: string[] = [];
  readonly dir: string;

  constructor(dir: string) {
    this.dir = dir;
    mkdirSync(dir, { recursive: true });
  }

  /** Writes a value as JSON, leaving out masks and pixel arrays. */
  json(name: string, value: unknown): string {
    const path = join(this.dir, name);
    writeFileSync(path, JSON.stringify(value, (key, v) => (key === "mask" || ArrayBuffer.isView(v) ? undefined : v), 2) + "\n");
    return this.done(path);
  }

  png(name: string, image: Raster): string {
    return this.done(writePng(join(this.dir, name), image));
  }

  private done(path: string): string {
    this.files.push(path);
    console.log(path);
    return path;
  }
}

/** A file name's safe form: letters, digits and dashes. */
export function fileName(text: string): string {
  return text.replace(/[^A-Za-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "") || "view";
}
