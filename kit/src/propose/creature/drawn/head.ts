import { add, scale, type V3 } from "../../../geom/vec3.ts";
import { radiusToward, type BuiltEnd, type BuiltPart } from "../body.ts";
import { reachInEnds } from "./ellipsoids.ts";
import { eased, unimodal } from "./profile.ts";
import { hiddenAt, reachIn, viewShowing, type PlacedViews } from "./views.ts";

/** How a head is drawn: up from the chin (a round head on an upright neck) or forward from the back of the skull (a snouted head). */
export type HeadDrawing = "upright" | "forward";

/** Stations along a drawn head, bunched toward its ends so it rounds off. */
const STATIONS = 9;

/** A drawn head stands out past the fit's ellipsoids by at most this share of their reach: the sheet shapes it within them. */
const MOST_PAST_FIT = 1.12;

/** An upright head's lower half is never narrower than this share of its neck's top: the jaw runs down into the neck. */
const JAW_ON_NECK = 0.95;

/**
 * Where no view reads an upright head's width (hair over it), its lower stretch narrows toward the chin by up to this share of the
 * ellipsoid's reach, over this share of its length up from the chin: a jaw rather than a ball's underside.
 */
const JAW_TAPER = 0.22;
const JAW_SPAN = 0.45;

/** How far each station's radii ease toward their neighbours'. */
const EASE = 0.35;

/** The profiles a head is drawn with, by drawing: each the axis (0 forward, 1 up, 2 left) and side it reaches toward. */
const PROFILES: Record<HeadDrawing, { along: 0 | 1; profiles: Array<{ name: string; axis: 0 | 1 | 2; sign: 1 | -1 }> }> = {
  upright: {
    along: 1,
    profiles: [
      { name: "front", axis: 0, sign: 1 },
      { name: "back", axis: 0, sign: -1 },
      { name: "left", axis: 2, sign: 1 },
    ],
  },
  forward: {
    along: 0,
    profiles: [
      { name: "up", axis: 1, sign: 1 },
      { name: "down", axis: 1, sign: -1 },
      { name: "left", axis: 2, sign: 1 },
    ],
  },
};

/** A head as drawn, head units: its length along its axis from its root, its stations along it, and its radii toward each profile. */
export interface DrawnHead {
  /** Where its root (the chin, or the back of the skull) lies along the axis through the head's middle, from that middle. */
  root: number;
  length: number;
  at: number[];
  profiles: Record<string, number[]>;
}

/** How much of the ellipsoid's width an unread upright head keeps at a share up from its chin. */
function jaw(share: number): number {
  return share >= JAW_SPAN ? 1 : 1 - JAW_TAPER * (1 - share / JAW_SPAN);
}

/**
 * A head drawn from the sheet: along the head's axis through its middle, at stations bunched toward its ends, each profile reaches as far
 * as the view showing it does, within the fit's ellipsoids (the head and what shapes it with it, as a snout) stood out a little, so ears,
 * horns and hair standing off it are left out; where no view shows a station or a layer hides it, the ellipsoids' own reach, an
 * upright head's width narrowing toward the chin. Each
 * profile then only rises to its widest and falls after it, and is eased; an upright head's lower half keeps its neck's width, so the
 * jaw runs down into the neck.
 */
export function drawnHead(placed: PlacedViews, head: BuiltEnd, shapes: readonly BuiltEnd[], drawing: HeadDrawing, neck?: BuiltPart): DrawnHead {
  const { along, profiles } = PROFILES[drawing];
  const axis = head.axes[along];
  const all = [head, ...shapes];
  const back = reachInEnds(all, head.center, scale(axis, -1));
  const front = reachInEnds(all, head.center, axis);
  const length = back + front;
  const at = Array.from({ length: STATIONS }, (_, i) => (length * (1 - Math.cos((Math.PI * (i + 1)) / (STATIONS + 1)))) / 2);
  const out: Record<string, number[]> = {};
  for (const profile of profiles) {
    const dir = scale(head.axes[profile.axis], profile.sign);
    const view = viewShowing(placed, axis, dir);
    const radii = at.map((s) => {
      const p: V3 = add(head.center, scale(axis, s - back));
      const fit = reachInEnds(all, p, dir);
      if (!view || hiddenAt(placed, view, p)) return drawing === "upright" && profile.axis === 2 ? fit * jaw(s / length) : fit;
      const read = reachIn(placed, view, p, dir, fit * MOST_PAST_FIT);
      return read === undefined ? fit : Math.min(read, fit * MOST_PAST_FIT);
    });
    const shaped = eased(unimodal(radii), EASE);
    const top = neck?.sections.filter((x) => x.t <= 1).at(-1);
    out[profile.name] =
      drawing === "upright" && top ? shaped.map((r, i) => (at[i] < length / 2 ? Math.max(r, JAW_ON_NECK * radiusToward(top, dir)) : r)) : shaped;
  }
  return { root: -back, length, at, profiles: out };
}
