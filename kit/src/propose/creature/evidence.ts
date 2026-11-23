import { add, dot, scale, sub, type V3 } from "../../geom/vec3.ts";
import type { HeldMark } from "../../reader/readings.ts";
import type { Evidence, HidingLayer, Measure, Px, Source, ViewName } from "../../reader/types.ts";
import type { Mask } from "../../sheet/core/mask.ts";
import { radiusToward, type BuiltBody, type Section } from "./body.ts";
import type { FitResult } from "./fit.ts";
import { WIDTH_PLACES } from "./params.ts";
import { rasterBody, toPixel, toPixelDirection } from "./raster.ts";
import { showsLength } from "./reads.ts";

/** Each view's depth: the body direction it looks along. */
const DEPTH: Record<ViewName, V3> = { front: [1, 0, 0], side: [0, 0, 1], top: [0, 1, 0] };

/** A direction lies across a view when it leans off the view's depth by at least this much. */
const ACROSS_VIEW = 0.5;

/** The section of a part nearest a share along it, its end caps aside. */
function sectionAt(sections: readonly Section[], t: number): Section {
  return sections.reduce((best, s) => (Math.abs(s.t - t) < Math.abs(best.t - t) ? s : best));
}

/** The views among `given` that read a part's radius toward `dir`: it lies across them and they show the part along its length. */
function readingViews(body: BuiltBody, part: string, dir: V3, given: readonly ViewName[]): ViewName[] {
  const built = body.parts.get(part)!;
  return given.filter((view) => Math.abs(dot(dir, DEPTH[view])) < ACROSS_VIEW && showsLength(built, view));
}

function insideBox(layers: readonly HidingLayer[], view: ViewName, [x, y]: Px): HidingLayer | undefined {
  return layers.find((l) => l.view === view && x >= l.box[0] && y >= l.box[1] && x <= l.box[0] + l.box[2] && y <= l.box[1] + l.box[3]);
}

function covered(mask: Mask, [x, y]: Px): boolean {
  const ix = Math.round(x);
  const iy = Math.round(y);
  return ix >= 0 && iy >= 0 && ix < mask.width && iy < mask.height && mask.data[iy * mask.width + ix] === 1;
}

/**
 * What the fit read, where it read it on each view, in head units: every landmark as a mark, every part's length along it, and its
 * widths at its root, middle and tip toward each profile, from its middle to its edge. A width no given view reads is the plan's
 * default; one under a hiding layer (a box the user gave, or an end that hides the body, as folded wings) is inferred.
 */
export function fitEvidence(
  fit: FitResult,
  sizes: ReadonlyMap<ViewName, [number, number]>,
  layers: readonly HidingLayer[],
  held: readonly HeldMark[],
  headId: string,
): Evidence {
  const given = [...fit.places.keys()];
  const body = fit.posed;
  const evidence: Evidence = { marks: [], measures: [] };
  const head = body.ends.find((e) => e.end.id === headId)!;
  const headHeight = 2 * head.radii[1];
  for (const view of given) {
    const place = fit.places.get(view)!;
    const [width, height] = sizes.get(view)!;
    const hiding = rasterBody(body, view, place, width, height, (owner) => body.ends.some((e) => e.end.id === owner && e.end.hides));
    for (const [id, at] of body.landmarks) {
      const isHeld = held.some((h) => h.id === id && h.view === view);
      evidence.marks.push({ id, view, at: toPixel(view, place, at), source: isHeld ? "held" : "read" });
    }
    for (const built of body.parts.values()) {
      const tip = built.joints[built.joints.length - 1];
      evidence.measures.push({
        id: `${built.part.id}.length`,
        view,
        from: toPixel(view, place, built.joints[0]),
        to: toPixel(view, place, tip),
        value: built.length / headHeight,
        source: given.some((v) => showsLength(built, v)) ? "read" : "default",
      });
      if (!showsLength(built, view)) continue;
      for (const t of WIDTH_PLACES) {
        const section = sectionAt(built.sections.filter((s) => s.t >= 0), t);
        const [bx, by] = toPixelDirection(view, sub(tip, built.joints[0]));
        const bone = Math.hypot(bx, by) || 1;
        section.round.forEach((r, k) => {
          if (built.part.kind === "trunk" && r.name === "right") return;
          const radius = radiusToward(section, r.dir);
          const from = toPixel(view, place, section.center);
          const readers = readingViews(body, built.part.id, r.dir, given);
          const acrossThis = Math.abs(dot(r.dir, DEPTH[view])) < ACROSS_VIEW;
          if (!acrossThis && readers.length > 0) return;
          // A width along this view's depth that no view reads is shown across the part here, so the default is seen in place.
          const side = k < 2 ? 1 : -1;
          const to: Px = acrossThis
            ? toPixel(view, place, add(section.center, scale(r.dir, radius)))
            : [from[0] - (side * by * radius * place.scale) / bone, from[1] + (side * bx * radius * place.scale) / bone];
          const layer = insideBox(layers, view, to);
          const hidden = covered(hiding, to);
          const source: Source = readers.length === 0 ? "default" : layer || hidden ? "inferred" : "read";
          const measure: Measure = { id: `${built.part.id}.${r.name}.${t}`, view, from, to, value: radius / headHeight, source };
          if (layer) measure.layer = layer.id;
          evidence.measures.push(measure);
        });
      }
    }
    {
      const [dx, dy] = toPixelDirection(view, head.axes[1]);
      const c = toPixel(view, place, head.center);
      const r = head.radii[1] * place.scale;
      evidence.measures.push({ id: "head", view, from: [c[0] - dx * r, c[1] - dy * r], to: [c[0] + dx * r, c[1] + dy * r], value: 1, source: "read" });
    }
  }
  return evidence;
}
