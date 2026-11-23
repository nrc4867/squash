import { roundTo } from "../../geom/point.ts";
import type { ReadSheet } from "../../reader/sheet.ts";
import type { ViewName, ViewPlace } from "../../reader/types.ts";
import type { Proposal, Proposer } from "../proposer.ts";
import { buildBody, profileRadius, type BuiltBody } from "./body.ts";
import { drawnHead, type DrawnHead } from "./drawn/head.ts";
import { offParts } from "./drawn/offParts.ts";
import { fitEvidence } from "./evidence.ts";
import { extentOf, fitBody } from "./fit.ts";
import { figureOutline } from "./outline.ts";
import type { ParamValues } from "./params.ts";
import { WIDTH_PLACES } from "./widths.ts";
import { loadPlan, type CreaturePlan } from "./plan.ts";
import { rasterBody } from "./raster.ts";
import { proposalRecipe } from "./recipe.ts";

/** A head in body units, when the readings give neither the views' scale nor the subject's height. */
const DEFAULT_HEAD = 0.3;

/** The recipe id a proposal takes unless the readings' options name one. */
const DEFAULT_ID = "proposed-body";

/** Body units to one of the plan's head units: from the views' scale, else the subject's height (its length, lying), else a default head. */
function bodyUnit(sheet: ReadSheet, rest: BuiltBody, places: Map<ViewName, ViewPlace>, headHeight: number): number {
  const { unitsPerPixel, height } = sheet.readings;
  const place = places.values().next().value!;
  if (unitsPerPixel) return unitsPerPixel * place.scale;
  const ext = extentOf(rest);
  if (height) return height / (sheet.views.has("front") || sheet.views.has("side") ? ext.y[1] : ext.x[1] - ext.x[0]);
  return DEFAULT_HEAD / headHeight;
}

/** The body's proportions in head units: where each landmark stands, each part's length and its widths at each of its width places. */
function proportions(plan: CreaturePlan, rest: BuiltBody, values: ParamValues, headHeight: number, unit: number) {
  const heads = (v: number) => roundTo(v / headHeight, 3);
  const builtOf = (part: string) => rest.parts.get(part)!;
  return {
    head: roundTo(headHeight * unit),
    heightInHeads: heads(extentOf(rest).y[1]),
    landmarks: Object.fromEntries([...rest.landmarks].map(([id, p]) => [id, p.map(heads)])),
    parts: Object.fromEntries(
      plan.parts.map((part) => [
        part.id,
        {
          length: heads(builtOf(part.id).length),
          widths: Object.fromEntries(Object.keys(part.radii).map((name) => [name, WIDTH_PLACES.map((t) => heads(profileRadius(part, values, name, t)))])),
        },
      ]),
    ),
  };
}

/** Proposes a creature's bare body at rest from a sheet: the plan the readings name, fitted to the views given. */
export const creatureProposer: Proposer = {
  id: "creature",
  propose(sheet: ReadSheet): Proposal {
    const { readings } = sheet;
    const plan = loadPlan(readings.plan);
    const layers = (readings.layers ?? []).map((l) => ({ ...l, looseness: l.looseness ?? plan.layers[l.kind] ?? 0 }));
    const held = readings.held ?? [];
    const fit = fitBody(plan, sheet.views, layers, held);
    const rest = buildBody(plan, fit.values, false);
    const head = rest.ends.find((e) => e.end.id === plan.head)!;
    const headHeight = 2 * head.radii[1];
    const unit = bodyUnit(sheet, rest, fit.places, headHeight);
    const id = typeof readings.options?.id === "string" ? readings.options.id : DEFAULT_ID;
    const outline = figureOutline(rest, unit);
    const drawn = new Map<string, DrawnHead>();
    for (const end of fit.posed.ends) {
      const draw = end.end.recipe?.draw;
      if (!draw) continue;
      const shapes = fit.posed.ends.filter((e) => e.end.on === end.end.on && !e.end.recipe);
      drawn.set(end.end.id, drawnHead({ views: sheet.views, places: fit.places, layers }, end, shapes, draw, fit.posed.parts.get(end.end.on)));
    }
    const headIds = new Set(fit.posed.ends.filter((e) => e.end.id === plan.head || (e.end.on === plan.ends.find((x) => x.id === plan.head)!.on && !e.end.recipe)).map((e) => e.end.id));
    const off = offParts({ views: sheet.views, places: fit.places, layers }, fit.posed, Object.keys(plan.drawnOff ?? {}), headIds);
    const recipe = proposalRecipe(plan, rest, unit, outline, id, { drawn, values: fit.values, off });
    const sizes = new Map([...sheet.views].map(([name, v]) => [name, [v.mask.width, v.mask.height] as [number, number]]));
    const evidence = fitEvidence(fit, sizes, layers, held, plan.head);
    const summary = proportions(plan, rest, fit.values, headHeight, unit);
    const counts = (source: string) => evidence.measures.filter((m) => m.source === source).length;
    return {
      evidence,
      places: Object.fromEntries(fit.places),
      shapes: Object.fromEntries([...sheet.views].map(([name, v]) => [name, rasterBody(fit.posed, name, fit.places.get(name)!, v.mask.width, v.mask.height)])),
      files: [
        { name: "proposal.json", value: { format: "squash-body-proposal/1", plan: plan.id, views: [...sheet.views.keys()], fit: roundTo(fit.cost), proportions: summary, evidence } },
        { name: "figure.json", value: outline },
        { name: `${id}.recipe.json`, value: recipe },
      ],
      lines: [
        `  ${plan.name} from ${[...sheet.views.keys()].join(", ")}: fit ${roundTo(fit.cost, 3)}, ${summary.heightInHeads} heads tall, a head ${summary.head} body units`,
        `  measures: ${counts("read")} read, ${counts("inferred")} inferred, ${counts("default")} from the plan, ${counts("held")} held`,
      ],
    };
  },
};
