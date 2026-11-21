import type { RecipeArea, RecipeGarment, RecipeStroke, RecipeTuck } from "../schema/recipe.ts";
import { foldedBand } from "./opening.ts";
import { garment } from "./wardrobe.ts";

/** How a glove meets the digits: a glove finger over each, or a mitten over the fingers together with the thumb apart. */
export type GloveDigits = "fingers" | "mitten";

/** What a glove is cut from: the whole hand, a cuff up the arm from the wrist, and how it covers the digits. */
export interface GloveSpec extends Partial<Omit<RecipeGarment, "areas">> {
  /** The glove's palette slot. */
  color: string;
  /** The hand it covers (a pair by its left side). */
  hand?: string;
  /** The arm its cuff runs up (a pair by its left side). */
  arm?: string;
  /** How far up the arm the cuff reaches from the wrist, in the arm's u; 0 for a glove ending at the wrist. */
  cuff?: number;
  /** The cuff's palette slot; the glove's when left out. */
  cuffColor?: string;
  /** How thick the cuff's turned-back edge stands out, in body units; 0 lies flat. */
  fold?: number;
  /** Glove fingers over each digit, or a mitten over the fingers with the thumb in a finger of its own. */
  digits?: GloveDigits;
}

/**
 * A glove: cloth over the whole hand, each digit in a glove finger of its own that bends with it, or a mitten with the fingers fused under
 * one cover and the thumb apart, and a cuff up the arm from the wrist, turned back by `fold`. A hand of any digit count takes it as it
 * is built. Fit and the rest pass through as on any garment.
 */
export function glove(spec: GloveSpec): RecipeGarment {
  const { color, hand = "hand-l", arm = "arm-l", cuff = 0.12, cuffColor, fold = 0, digits = "fingers", ...rest } = spec;
  const covered: RecipeStroke[] = [{ on: hand, shape: "band", u: [0, 1] }];
  const body: RecipeArea = { id: "glove", name: "Glove", paint: covered };
  const areas: RecipeArea[] = [body];
  if (cuff > 0) {
    const from = Math.max(0, 1 - cuff);
    if (fold > 0) {
      covered.push({ on: arm, shape: "band", u: [from, 1] });
      areas.push(foldedBand("cuff", arm, from, Math.min(1, from + Math.max(cuff / 2, 2 / 64)), { fold, color: cuffColor, name: "Cuff" }));
    } else {
      const band: RecipeArea = { id: "cuff", name: "Cuff", paint: [{ on: arm, shape: "band", u: [from, 1] }] };
      if (cuffColor) band.color = cuffColor;
      areas.push(band);
    }
  }
  const tucks: RecipeTuck[] = [{ part: hand, kind: "fingers", tucked: digits === "mitten" }];
  return garment({ id: digits === "mitten" ? "mitten" : "glove", slot: "hands", fit: 0.003, hemRoll: 0.002, ...rest, color, areas, tucks: [...tucks, ...(rest.tucks ?? [])] });
}

/** What a hoof boot is cut from: the whole hoof, its digits among it, and a shaft up the leg from the fetlock. */
export interface HoofBootSpec extends Partial<Omit<RecipeGarment, "areas">> {
  /** The boot's palette slot. */
  color: string;
  /** The hoof it covers (a pair by its left side). */
  hoof?: string;
  /** The leg its shaft runs up (a pair by its left side). */
  leg?: string;
  /** How far up the leg the shaft reaches from the fetlock, in the leg's u; 0 for a cover over the hoof alone. */
  shaft?: number;
  /** The shaft's top band's palette slot; the boot's when left out. */
  topColor?: string;
  /** How tall the top band is, in the leg's u. */
  topBand?: number;
}

/**
 * A hoof boot: cloth over the whole hoof, each toe of a cloven hoof in its own and its sole, and a shaft up the leg over the fetlock,
 * which bends with the leg and the hoof as the body does, with a band at its top. Fit and the rest pass through as on any garment.
 */
export function hoofBoot(spec: HoofBootSpec): RecipeGarment {
  const { color, hoof = "hoof-l", leg = "leg-l", shaft = 0.2, topColor, topBand = 0.05, ...rest } = spec;
  const shod: RecipeStroke[] = [{ on: hoof, shape: "band", u: [0, 1] }];
  const boot: RecipeArea = { id: "boot", name: "Boot", paint: shod };
  const areas: RecipeArea[] = [boot];
  if (shaft > 0) {
    const from = Math.max(0, 1 - shaft);
    shod.push({ on: leg, shape: "band", u: [from, 1] });
    const top: RecipeArea = { id: "top", name: "Top", paint: [{ on: leg, shape: "band", u: [from, Math.min(1, from + Math.max(topBand, 2 / 64))] }] };
    if (topColor) top.color = topColor;
    areas.push(top);
  }
  return garment({ id: "hoof-boot", slot: "feet", fit: 0.004, hemRoll: 0.003, ...rest, color, areas });
}
