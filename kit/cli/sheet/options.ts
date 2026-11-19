import { basename, dirname, extname, join, resolve } from "node:path";
import { parseBox, type Box } from "../../src/sheet/core/box.ts";

/** A sheet command's `key=value` options. */
export class SheetOptions {
  private readonly values: Map<string, string>;

  constructor(words: readonly string[] | Map<string, string>) {
    this.values = new Map();
    if (words instanceof Map) {
      for (const [k, v] of words) this.values.set(k, v);
      return;
    }
    for (const word of words) {
      const eq = word.indexOf("=");
      if (eq <= 0) throw new Error(`option "${word}" is not key=value`);
      this.values.set(word.slice(0, eq), word.slice(eq + 1));
    }
  }

  /** These options with one more set. */
  with(key: string, value: string): SheetOptions {
    return new SheetOptions(new Map([...this.values, [key, value]]));
  }

  text(key: string): string | undefined {
    return this.values.get(key);
  }

  number(key: string): number | undefined {
    const text = this.values.get(key);
    if (text === undefined) return undefined;
    const value = Number(text);
    if (!Number.isFinite(value)) throw new Error(`${key}=${text} is not a number`);
    return value;
  }

  flag(key: string): boolean {
    return this.values.get(key) === "true";
  }

  box(key = "box"): Box | undefined {
    const text = this.values.get(key);
    return text ? parseBox(text) : undefined;
  }

  list(key: string): string[] | undefined {
    return this.values.get(key)?.split(",").map((s) => s.trim()).filter(Boolean);
  }

  /** Several boxes, `x,y,w,h;x,y,w,h`. */
  boxes(key: string): Box[] | undefined {
    const text = this.values.get(key);
    return text ? text.split(";").filter(Boolean).map(parseBox) : undefined;
  }

  /** Several pixel points, `x,y;x,y`. */
  points(key: string): Array<[number, number]> | undefined {
    const text = this.values.get(key);
    if (!text) return undefined;
    return text.split(";").filter(Boolean).map((pair) => {
      const [x, y] = pair.split(",").map(Number);
      if (!Number.isFinite(x) || !Number.isFinite(y)) throw new Error(`${key}: "${pair}" is not x,y`);
      return [x, y];
    });
  }

  /** The out= folder, or `<image>-sheet` beside the image. */
  outDir(image: string): string {
    const given = this.values.get("out");
    if (given) return resolve(given);
    return join(dirname(resolve(image)), `${basename(image, extname(image))}-sheet`);
  }
}
