import type { Point } from "../geom/point.ts";
import type { RecipeArea, RecipeGarment, RecipeHole, RecipePart, RecipeStroke } from "../schema/recipe.ts";
import { prop } from "../props/owned.ts";
import { shape } from "../shapes/shape.ts";
import { opening, type OpeningShape } from "./opening.ts";
import { garment } from "./wardrobe.ts";

/** How far from the nape toward the brow a crown's sides run straight before they round up over the top, a share of the way. */
const CROWN_STRAIGHT = 0.3;

/** How many places the round of a crown's front is drawn through, a quarter of a rounded square from the side to the brow. */
const CROWN_ARC_POINTS = 10;

/** How square a crown's front is: 2 an ellipse's quarter, higher flatter across the brow and rounder only at its corners. */
const CROWN_SQUARENESS = 3;

/** How tall a peaked cap's binding is at its hem, in the head's u: three rows of its chart. */
const CAP_BINDING = 3 / 64;

/** What a beanie is cut from: a dome of cloth over the back and top of a head, a folded band at its hem, and holes the ears come through. */
export interface BeanieSpec extends Partial<Omit<RecipeGarment, "areas">> {
  /** The beanie's palette slot. */
  color: string;
  /** The head it sits on. */
  head?: string;
  /** How far forward over the top it reaches, in the head's u from the back of the skull. */
  brow: number;
  /** Where its hem crosses the back of the head above the neck at the sides, in the head's u; it rises to half that over the middle, clear of the neck's join. */
  nape?: number;
  /** How far down the sides it reaches, in the head's v either side of its top. */
  sides?: number;
  /** The head's v at its top: 0 on a head resting forward. */
  top?: number;
  /** How tall its folded band is at the hem, in the head's u; 0 for none. */
  band?: number;
  /** The band's palette slot, the knit's back face; the beanie's when left out. */
  bandColor?: string;
  /** How thick the band's fold stands out, in body units. */
  fold?: number;
  /** The ears that come through it (a pair by its left side); none for a beanie the ears tuck under. */
  earHoles?: string[];
  /** An opening round each ear wider than the ear, how far past its root ring the cloth opens; left out, the cloth meets the ear where it comes through. */
  earHole?: OpeningShape;
  /** Whether a cuff band of the knit rings each ear where it comes through. */
  earCuffs?: boolean;
}

/**
 * The crown of a head's chart a hat paints: the back of the skull from `nape`, `sides` down each side, rounding up to `brow` over the
 * top. One side's half, mirrored across the head's middle, so its outline never crosses the chart's seam at v 0.
 */
function crown(head: string, nape: number, brow: number, top: number, sides: number, inset = 0): RecipeStroke {
  const reach = brow - nape;
  const side = sides - inset;
  const back = (share: number) => nape * share + inset;
  const from = nape + reach * CROWN_STRAIGHT;
  const along = brow - inset - from;
  const arc: Point[] = Array.from({ length: CROWN_ARC_POINTS + 1 }, (_, i): Point => {
    const t = (Math.PI / 2) * (i / CROWN_ARC_POINTS);
    const power = 2 / CROWN_SQUARENESS;
    return [from + along * Math.sin(t) ** power, top + side * Math.cos(t) ** power];
  });
  const half: Point[] = [[back(0.5), top], [back(0.7), top + side * 0.5], [back(1), top + side], ...arc];
  return { on: head, shape: "lasso", points: half, mirrored: true };
}

/** The strokes that open a hole round each ear, to close an area: the cells within the hole's reach of each ear's root unpainted; none without a shape. */
function earOpenings(ears: string[], shape: OpeningShape | undefined): RecipeStroke[] {
  return shape ? ears.map((ear) => opening(ear, shape)) : [];
}

/** The holes each ear comes through, with a cuff where asked. */
function earHolesOf(ears: string[], cuff: boolean): RecipeHole[] {
  return ears.map((part) => ({ part, cuff }));
}

/**
 * A beanie: knit over the back and top of the head from the nape to `brow`, its hem a smooth curve over the sides, a folded band round
 * it, and each ear in `earHoles` coming out through it, the knit meeting the ear where it comes through (a cuff of knit round its root
 * with `earCuffs`, a wider opening with `earHole`). Ears left out tuck under the knit. Fit, ease and the rest pass through as on any garment.
 */
export function beanie(spec: BeanieSpec): RecipeGarment {
  const {
    color, head = "head", brow, sides = 0.3, top = 0, nape = 0.08, band = 0.06, bandColor, fold = 0.005, earHoles = [], earHole, earCuffs = false, ...rest
  } = spec;
  if (!(brow > nape)) throw new Error(`beanie: brow lies past the nape, above ${nape}, not ${brow}`);
  const holes = earOpenings(earHoles, earHole);
  const areas: RecipeArea[] = [];
  if (band > 0) {
    const inset = Math.min(band, (brow - nape) / 3);
    const banded: RecipeArea = { id: "band", name: "Band", fold, paint: [crown(head, nape, brow, top, sides)] };
    if (bandColor) banded.color = bandColor;
    areas.push(banded, { id: "knit", name: "Knit", paint: [crown(head, nape, brow, top, sides, inset), ...holes] });
  } else {
    areas.push({ id: "knit", name: "Knit", paint: [crown(head, nape, brow, top, sides), ...holes] });
  }
  const made = garment({ id: "beanie", slot: "head", fit: 0.006, hemRoll: 0.003, ...rest, color, areas });
  if (earHoles.length > 0) made.holes = [...(rest.holes ?? []), ...earHolesOf(earHoles, earCuffs)];
  return made;
}

/** What a peaked cap is: a beanie's crown, with a narrow binding for its band, and a stiff peak standing out over the brow. */
export interface PeakedCapSpec extends BeanieSpec {
  /** The hand-made socket on the head the peak sits on: at the brow's middle, `sink: 0` and an `outset` past the cap's fit. */
  peakSocket: number;
  /** How far the peak stands out, in body units. */
  peak: number;
  /** How wide the peak is, in body units. */
  peakWidth: number;
  /** How thick the peak is at its root, in body units. */
  peakThickness?: number;
  /** Degrees the peak tips down from the head's forward. */
  peakDip?: number;
  /** The peak's palette slot; the cap's when left out. */
  peakColor?: string;
}

/**
 * A peaked cap: a crown of cloth as a beanie's, bound at its hem by a narrow band of its own colour, its ears coming through as a beanie's do,
 * and a stiff peak the cap owns, a lens-sectioned plate resting forward from a socket at the brow, wider than long and rounded at its
 * front. Returns the garment and the peak part; the head needs the peak's socket.
 */
export function peakedCap(spec: PeakedCapSpec): { garment: RecipeGarment; peak: RecipePart } {
  const { peakSocket, peak, peakWidth, peakThickness = 0.008, peakDip = 4, peakColor, ...crownSpec } = spec;
  const cap = beanie({ id: "cap", band: CAP_BINDING, bandColor: spec.color, fold: 0.003, ...crownSpec });
  const head = crownSpec.head ?? "head";
  const half = peakWidth / 2;
  const root = peakThickness / 2;
  const side: Point[] = [[0, root], [peak * 0.6, root * 0.7], [peak, 0], [peak * 0.6, -root * 0.7], [0, -root]];
  const steps = 8;
  const front: Point[] = Array.from({ length: steps + 1 }, (_, i): Point => {
    const t = (Math.PI * i) / steps - Math.PI / 2;
    return [peak * Math.cos(t), half * Math.sin(t)];
  });
  const peakPart = prop({
    id: `${cap.id}-peak`,
    template: "blob",
    on: head,
    socket: peakSocket,
    garment: cap.id,
    color: peakColor ?? spec.color,
    rest: "forward",
    lean: -peakDip,
    generic: { section: "lens" },
    side: shape(side),
    top: shape(front),
  });
  return { garment: cap, peak: peakPart };
}
