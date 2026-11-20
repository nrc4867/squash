import type { RecipeArea, RecipeGarment } from "../schema/recipe.ts";
import { foldedBand, openingBand, type OpeningShape } from "./opening.ts";
import { garment, LAYER } from "./wardrobe.ts";

/** The fewest rows of u a band keeps on a part's chart: two cells. */
const BAND_ROWS = 2 / 64;

/** What a crew sweater is cut from: the torso from `hem` (its u) up, a whole sleeve, a ribbed hem, cuffs and a collar round the neckline. */
export interface CrewSweaterSpec extends Partial<Omit<RecipeGarment, "areas">> {
  /** The sweater's palette slot. */
  color: string;
  /** The ribbing's palette slot; the sweater's when left out. */
  rib?: string;
  /** Where its hem sits on the torso, in the torso's u. */
  hem: number;
  /** How tall the ribbed hem band is, in the torso's u. */
  hemBand?: number;
  /** How long the ribbed cuff is, in the arm's u, ending at the wrist. */
  cuffBand?: number;
  /** Where the sleeve ends on the arm, in its u: 1 at the wrist. */
  sleeve?: number;
  /** The neckline: round (a crew) or v, how far past the neck and how deep at the front, in body units. */
  neckline?: OpeningShape;
  /** How wide the collar's rib band is round the neckline, in body units. */
  collar?: number;
  /** The ease the ribbing pulls the cloth in to, in body units. */
  gather?: number;
  torso?: string;
  arm?: string;
  neck?: string;
}

/**
 * A crew sweater (or a V-neck with `neckline: { cut: "v", depth }`): a body over the torso from `hem`, the arms from the shoulder and the
 * neck, whose flare into the shoulders stays cloth past the neckline's opening,
 * a rib area with the hem band, the cuffs and a collar band following the neckline, all gathered in snug. Ease, hang and drops pass
 * through as on any garment.
 */
export function crewSweater(spec: CrewSweaterSpec): RecipeGarment {
  const {
    color, rib, hem, hemBand = 0.04, cuffBand = 0.08, sleeve = 1, neckline = { cut: "round", depth: 0.025 }, collar = 0.018, gather = 0.004,
    torso = "torso", arm = "arm-l", neck = "neck", ...rest
  } = spec;
  const body: RecipeArea = {
    id: "body",
    name: "Body",
    paint: [
      { on: torso, shape: "band", u: [hem, 1] },
      { on: arm, shape: "band", u: [0, sleeve] },
      { on: neck, shape: "band", u: [0, 1] },
    ],
  };
  const ribArea: RecipeArea = {
    id: "rib",
    name: "Ribbing",
    gather,
    paint: [
      { on: torso, shape: "band", u: [hem, hem + Math.max(hemBand, BAND_ROWS)] },
      { on: arm, shape: "band", u: [sleeve - Math.max(cuffBand, BAND_ROWS), sleeve] },
      ...openingBand(neck, neckline, collar),
    ],
  };
  if (rib) ribArea.color = rib;
  return garment({ id: "sweater", slot: "top", layer: LAYER.over, fit: 0.02, hemRoll: 0.003, ...rest, color, areas: [body, ribArea] });
}

/** What cuffed jeans are cut from: the torso up to `waist` (its u), the legs to `hem` (their u), the hem rolled up `cuff` of the leg. */
export interface CuffedJeansSpec extends Partial<Omit<RecipeGarment, "areas">> {
  color: string;
  /** The cuff's palette slot, the denim's back face; the jeans' when left out. */
  cuffColor?: string;
  /** Where the waistband sits on the torso, in its u. */
  waist: number;
  /** Where the legs end, in the leg's u. */
  hem: number;
  /** How tall the rolled cuff is, in the leg's u. */
  cuff?: number;
  /** How thick the roll stands out over the leg, in body units. */
  fold?: number;
  torso?: string;
  leg?: string;
}

/** Jeans with rolled cuffs: the denim over the torso to the waist and down the legs, the cuff a fold showing the denim's back face. */
export function cuffedJeans(spec: CuffedJeansSpec): RecipeGarment {
  const { color, cuffColor, waist, hem, cuff = 0.06, fold = 0.006, torso = "torso", leg = "leg-l", ...rest } = spec;
  const denim: RecipeArea = {
    id: "body",
    name: "Denim",
    paint: [
      { on: torso, shape: "band", u: [0, waist] },
      { on: leg, shape: "band", u: [0, hem] },
    ],
  };
  const rolled = foldedBand("cuff", leg, hem - Math.max(cuff, BAND_ROWS), hem, { fold, color: cuffColor, name: "Cuff" });
  return garment({ id: "jeans", slot: "bottom", layer: LAYER.waistband, fit: 0.012, hemRoll: 0.004, ...rest, color, areas: [denim, rolled] });
}

/** What a turtleneck collar is: up the neck to `top` (its u), the top `foldBand` of it folded back down over the rest by `fold`. */
export interface TurtleneckSpec {
  neck?: string;
  /** Where the collar's top edge, its fold, sits on the neck, in its u. */
  top: number;
  /** How far down the neck the fold-over reaches, in the neck's u. */
  foldBand?: number;
  /** How thick the fold-over stands out, in body units. */
  fold?: number;
  /** The ease the ribbing pulls the collar in to, in body units. */
  gather?: number;
  /** The ribbing's palette slot; the garment's when left out. */
  color?: string;
}

/**
 * A turtleneck collar's two areas: a rib band up the neck from its root, gathered snug, and its top folded back down over it, standing out
 * by `fold` with an inked edge. Add them after the body's area; the neck is painted, so it comes out of no opening.
 */
export function turtleneck(spec: TurtleneckSpec): RecipeArea[] {
  const { neck = "neck", top, foldBand = 0.25, fold = 0.006, gather = 0.006, color } = spec;
  const collar: RecipeArea = { id: "collar", name: "Collar", gather, paint: [{ on: neck, shape: "band", u: [0, top] }] };
  if (color) collar.color = color;
  return [collar, foldedBand("collar-fold", neck, top - Math.max(foldBand, BAND_ROWS), top, { fold, color, name: "Fold", gather })];
}
