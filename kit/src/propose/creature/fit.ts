import { blankMask, erode, type Mask } from "../../sheet/core/mask.ts";
import type { HeldMark } from "../../reader/readings.ts";
import type { ViewSilhouette } from "../../reader/silhouette.ts";
import type { HidingLayer, Px, ViewName } from "../../reader/types.ts";
import { buildBody, type BuiltBody } from "./body.ts";
import { ParamValues, planParams, type Param, type ParamGroup } from "./params.ts";
import type { CreaturePlan } from "./plan.ts";
import { rasterBody, toPixel, type ViewPlace } from "./raster.ts";

/** The fit works on views shrunk to at most this many pixels on their longest side. */
const FIT_PIXELS = 180;

/** Body drawn where the view shows nothing costs this many times a view's pixel the body leaves bare: ears, hair and tufts may stand off a body, nothing lies inside the outline but body. */
const OVER_WEIGHT = 3;

/** How hard a mark held by hand pulls its landmark, per square head unit it lies off. */
const HELD_WEIGHT = 4;

/** A step is given up once it shrinks to this share of where it started. */
const LEAST_STEP_SHARE = 1 / 64;

/** The fit's stages: the groups each moves and the sweeps it takes. */
const STAGES: Array<{ groups: ParamGroup[]; sweeps: number }> = [
  { groups: ["place"], sweeps: 8 },
  { groups: ["place", "length", "pose"], sweeps: 12 },
  { groups: ["place", "length", "pose", "width", "size"], sweeps: 14 },
  { groups: ["width", "size", "length"], sweeps: 8 },
];

/** One view as the fit reads it: the sheet's silhouette shrunk, with the hiding layers loosened off it, and where the body lies over it. */
interface FitView {
  name: ViewName;
  sheet: Mask;
  target: Mask;
  base: Px;
  count: number;
}

/** What the fit gives: the parameters, the body as the sheet poses it, each view's place in the sheet's own pixels, and its cost. */
export interface FitResult {
  values: ParamValues;
  posed: BuiltBody;
  places: Map<ViewName, ViewPlace>;
  cost: number;
}

/** A mask shrunk by `factor`: a pixel is on where at least half the pixels it covers are. */
function shrunk(mask: Mask, factor: number): Mask {
  const width = Math.ceil(mask.width / factor);
  const height = Math.ceil(mask.height / factor);
  const out = blankMask(width, height);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      let on = 0;
      let all = 0;
      for (let dy = 0; dy < factor; dy++) {
        for (let dx = 0; dx < factor; dx++) {
          const sx = x * factor + dx;
          const sy = y * factor + dy;
          if (sx >= mask.width || sy >= mask.height) continue;
          all++;
          on += mask.data[sy * mask.width + sx];
        }
      }
      out.data[y * width + x] = on * 2 >= all && all > 0 ? 1 : 0;
    }
  }
  return out;
}

/** The silhouette with each hiding layer's box shrunk in by its looseness: the body lies that far inside what hides it. */
export function loosened(mask: Mask, layers: readonly HidingLayer[], view: ViewName, pixelsPerHead: number, factor: number): Mask {
  let out = mask;
  for (const layer of layers) {
    if (layer.view !== view) continue;
    const r = Math.round((layer.looseness * pixelsPerHead) / factor);
    if (r <= 0) continue;
    const eroded = erode(out, r);
    const next = { ...out, data: Uint8Array.from(out.data) };
    const [bx, by, bw, bh] = layer.box.map((v) => v / factor);
    for (let y = Math.max(0, Math.floor(by)); y < Math.min(out.height, Math.ceil(by + bh)); y++) {
      for (let x = Math.max(0, Math.floor(bx)); x < Math.min(out.width, Math.ceil(bx + bw)); x++) next.data[y * out.width + x] = eroded.data[y * out.width + x];
    }
    out = next;
  }
  return out;
}

function count(mask: Mask): number {
  let n = 0;
  for (const v of mask.data) n += v;
  return n;
}

/** How far the body lies off the view: body where the view shows none, weighted, and view the body leaves bare, over the view's area. */
function viewCost(model: Mask, target: Mask, area: number): number {
  let over = 0;
  let under = 0;
  for (let i = 0; i < model.data.length; i++) {
    const m = model.data[i];
    const t = target.data[i];
    if (m && !t) over++;
    else if (t && !m) under++;
  }
  return (OVER_WEIGHT * over + under) / Math.max(1, area);
}

/** The pull of a parameter toward its neutral value. */
function holdCost(p: Param): number {
  if (p.hold === 0) return 0;
  if (p.group === "pose" || p.neutral === 0) return p.hold * (p.value - p.neutral) ** 2;
  return p.hold * Math.log(p.value / p.neutral) ** 2 * 100;
}

/**
 * Fits the plan's body to the sheet's views by model-based silhouette fitting: lengths, width scales, end sizes and the pose the sheet
 * shows are moved one at a time, by steps that halve when neither way helps, to lower how far the body's silhouette lies off each view.
 */
export function fitBody(plan: CreaturePlan, views: ReadonlyMap<ViewName, ViewSilhouette>, layers: readonly HidingLayer[], held: readonly HeldMark[]): FitResult {
  const names = [...views.keys()];
  const values = new ParamValues(planParams(plan, names));
  const longest = Math.max(...[...views.values()].map((v) => Math.max(v.mask.width, v.mask.height)));
  const factor = Math.max(1, Math.ceil(longest / FIT_PIXELS));
  const neutral = buildBody(plan, values, true);
  const standing = views.get("front") ?? views.get("side");
  const modelExtent = extentOf(neutral);
  const pixelsPerHead = standing ? standing.box.h / Math.max(1e-6, modelExtent.y[1]) : (views.get("top")!.box.w / Math.max(1e-6, modelExtent.z[1] - modelExtent.z[0]));
  const fitViews: FitView[] = names.map((name) => {
    const silhouette = views.get(name)!;
    const sheet = shrunk(silhouette.mask, factor);
    const target = loosened(sheet, layers, name, pixelsPerHead, factor);
    const box = silhouette.box;
    let base: Px;
    if (name === "front") base = [silhouette.middle, silhouette.ground + 1];
    else if (name === "side") base = [box.x + box.w / 2 - pixelsPerHead * ((modelExtent.x[0] + modelExtent.x[1]) / 2), silhouette.ground + 1];
    else base = [silhouette.middle, box.y + box.h / 2 + pixelsPerHead * ((modelExtent.x[0] + modelExtent.x[1]) / 2)];
    return { name, sheet, target, base, count: count(target) };
  });
  const placeOf = (fv: FitView): ViewPlace => {
    const s = pixelsPerHead * values.get("scale", 1);
    const [bx, by] = fv.base;
    if (fv.name === "side") return { origin: [bx + s * values.get("side.x"), by], scale: s };
    if (fv.name === "top") return { origin: [bx, by + s * values.get("top.y")], scale: s };
    return { origin: [bx, by], scale: s };
  };
  const cost = (): number => {
    const body = buildBody(plan, values, true);
    let total = 0;
    for (const fv of fitViews) {
      const place = placeOf(fv);
      const small: ViewPlace = { origin: [place.origin[0] / factor, place.origin[1] / factor], scale: place.scale / factor };
      total += viewCost(rasterBody(body, fv.name, small, fv.sheet.width, fv.sheet.height), fv.target, fv.count);
    }
    for (const p of values.list) total += holdCost(p);
    for (const mark of held) {
      const at = body.landmarks.get(mark.id);
      const fv = fitViews.find((v) => v.name === mark.view);
      if (!at || !fv) continue;
      const place = placeOf(fv);
      const [x, y] = toPixel(mark.view, place, at);
      total += (HELD_WEIGHT * ((x - mark.at[0]) ** 2 + (y - mark.at[1]) ** 2)) / place.scale ** 2;
    }
    return total;
  };
  let best = cost();
  for (const stage of STAGES) {
    const moving = values.list.filter((p) => stage.groups.includes(p.group));
    const steps = new Map(moving.map((p) => [p.key, p.step]));
    for (let sweep = 0; sweep < stage.sweeps; sweep++) {
      let moved = false;
      for (const p of moving) {
        const step = steps.get(p.key)!;
        if (step < p.step * LEAST_STEP_SHARE) continue;
        const was = p.value;
        let improved = false;
        for (const sign of [1, -1]) {
          p.value = Math.max(p.min, Math.min(p.max, p.group === "pose" || p.group === "place" || p.neutral === 0 ? was + sign * step : was * (1 + sign * step)));
          const c = cost();
          if (c < best - 1e-9) {
            best = c;
            improved = true;
            break;
          }
          p.value = was;
        }
        if (improved) moved = true;
        else steps.set(p.key, step / 2);
      }
      if (!moved && [...steps.entries()].every(([k, s]) => s < values.param(k)!.step * LEAST_STEP_SHARE)) break;
    }
  }
  const places = new Map<ViewName, ViewPlace>(fitViews.map((fv) => [fv.name, placeOf(fv)]));
  return { values, posed: buildBody(plan, values, true), places, cost: best };
}

/** A body's reach along each axis, head units. */
export function extentOf(body: BuiltBody): { x: [number, number]; y: [number, number]; z: [number, number] } {
  const ext = { x: [Infinity, -Infinity] as [number, number], y: [Infinity, -Infinity] as [number, number], z: [Infinity, -Infinity] as [number, number] };
  for (const built of body.parts.values()) {
    for (const s of built.sections) {
      for (const r of s.round) {
        const p = [0, 1, 2].map((i) => s.center[i] + r.dir[i] * s.radius(r.name));
        ext.x = [Math.min(ext.x[0], p[0]), Math.max(ext.x[1], p[0])];
        ext.y = [Math.min(ext.y[0], p[1]), Math.max(ext.y[1], p[1])];
        ext.z = [Math.min(ext.z[0], p[2]), Math.max(ext.z[1], p[2])];
      }
    }
  }
  for (const end of body.ends) {
    for (let a = 0; a < 3; a++) {
      for (const sign of [-1, 1]) {
        const p = [0, 1, 2].map((i) => end.center[i] + sign * end.axes[a][i] * end.radii[a]);
        ext.x = [Math.min(ext.x[0], p[0]), Math.max(ext.x[1], p[0])];
        ext.y = [Math.min(ext.y[0], p[1]), Math.max(ext.y[1], p[1])];
        ext.z = [Math.min(ext.z[0], p[2]), Math.max(ext.z[1], p[2])];
      }
    }
  }
  return ext;
}

