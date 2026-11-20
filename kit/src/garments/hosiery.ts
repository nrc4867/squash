import type { RecipeArea, RecipeGarment, RecipeNet } from "../schema/recipe.ts";
import { garment, LAYER } from "./wardrobe.ts";

/** The fewest rows of u a band keeps on a part's chart: two cells. */
const BAND_ROWS = 2 / 64;

/** What stockings are cut from: the leg from `top` (its u) down and the whole foot, an opaque band at the top and, if asked, an opaque toe. */
export interface StockingsSpec extends Partial<Omit<RecipeGarment, "areas">> {
  /** The stockings' palette slot. */
  color: string;
  /** Where the top sits on the leg, in its u: 0 at the hip. */
  top: number;
  /** How tall the opaque band at the top is, in the leg's u; 0 for none. */
  band?: number;
  /** The band's palette slot; the stockings' when left out. */
  bandColor?: string;
  /** How far back from the tips the reinforced toe reaches, in the foot's u; none when left out. */
  toe?: number;
  /** The toe's palette slot; the band's when left out. */
  toeColor?: string;
  leg?: string;
  foot?: string;
}

/** The stocking-only fields of a spec, which are not garment fields. */
const STOCKING_FIELDS = ["color", "top", "band", "bandColor", "toe", "toeColor", "leg", "foot", "opacity", "net"] as const;

/** The garment fields of a stockings spec, passed through to the garment. */
function garmentFields(spec: StockingsSpec): Partial<RecipeGarment> {
  const rest: Record<string, unknown> = { ...spec };
  for (const field of STOCKING_FIELDS) delete rest[field];
  return rest as Partial<RecipeGarment>;
}

/** The areas of stockings: the leg and foot as `body`, a band at the top and a toe, each whole and opaque. */
function stockingAreas(spec: StockingsSpec, body: Omit<RecipeArea, "id" | "paint">): RecipeArea[] {
  const { top, band = 0.05, bandColor, toe, toeColor, leg = "leg-l", foot = "foot-l" } = spec;
  const areas: RecipeArea[] = [{
    id: "leg",
    name: "Leg",
    ...body,
    paint: [
      { on: leg, shape: "band", u: [top, 1] },
      { on: foot, shape: "band", u: [0, 1] },
    ],
  }];
  if (band > 0) {
    const area: RecipeArea = { id: "band", name: "Band", paint: [{ on: leg, shape: "band", u: [top, top + Math.max(band, BAND_ROWS)] }] };
    if (bandColor) area.color = bandColor;
    areas.push(area);
  }
  if (toe !== undefined) {
    const area: RecipeArea = { id: "toe", name: "Toe", paint: [{ on: foot, shape: "band", u: [1 - Math.max(toe, BAND_ROWS), 1] }] };
    const color = toeColor ?? bandColor;
    if (color) area.color = color;
    areas.push(area);
  }
  return areas;
}

/** What sheer stockings are: stockings whose leg and foot show the skin through them. */
export interface SheerStockingsSpec extends StockingsSpec {
  /** How much of the skin the leg hides where it faces the viewer, 0 to 1: about 0.2 for sheer, 0.5 for opaque-looking tights. */
  opacity?: number;
}

/**
 * Sheer stockings over a leg pair from `top` down and the whole foot: the skin shows through the leg, tinted by the stockings' colour and
 * darker toward the silhouette, under an opaque band at the top and, with `toe`, an opaque reinforced toe. Fit, ink and any garment field
 * pass through.
 */
export function sheerStockings(spec: SheerStockingsSpec): RecipeGarment {
  const { color, opacity = 0.25 } = spec;
  return garment({ id: "stockings", slot: "legs", layer: LAYER.under, fit: 0.004, inkWeight: 0.5, ...garmentFields(spec), color, areas: stockingAreas(spec, { opacity }) });
}

/** What fishnet stockings are: stockings whose leg and foot are a net. */
export interface FishnetStockingsSpec extends StockingsSpec {
  /** The net; a diamond fishnet 0.03 apart with threads 0.004 wide when left out, any field of it overriding. */
  net?: Partial<RecipeNet>;
}

/**
 * Fishnet stockings over a leg pair from `top` down and the whole foot: a diamond net of threads over the skin, under an opaque band at
 * the top and, with `toe`, an opaque toe. `net: { pattern: "square" }` makes a square mesh; hole, thread and angle set its spacing, thread
 * width and slant. Fit, ink and any garment field pass through.
 */
export function fishnetStockings(spec: FishnetStockingsSpec): RecipeGarment {
  const { color, net = {} } = spec;
  const knit: RecipeNet = { pattern: "diamond", hole: 0.03, thread: 0.004, angle: 40, ...net };
  return garment({ id: "fishnets", slot: "legs", layer: LAYER.under, fit: 0.004, inkWeight: 0.5, ...garmentFields(spec), color, areas: stockingAreas(spec, { net: knit }) });
}
