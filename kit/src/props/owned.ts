import type { RecipeGeneric, RecipePart } from "../schema/recipe.ts";
import type { Rest } from "../frames/rest.ts";
import { oval } from "../shapes/oval.ts";
import { outline, type Shape } from "../shapes/shape.ts";
import { SMALL } from "../parts/generic.ts";
import { socketName } from "../parts/rig.ts";

export interface PropSpec {
  id: string;
  /** The part it sits on. */
  on: string;
  /** A hand-made socket number on that part (add it with `socket(n, u, v, { sink: 0 })`), or a socket's name. */
  socket: number | string;
  /** The garment that owns it, if it is built only while that garment is worn. */
  garment?: string;
  color: string;
  rest?: Rest;
  template?: "blob" | "tube" | "sheet";
  side: Shape;
  top: Shape;
  size?: number;
  generic?: RecipeGeneric;
}

/** A prop part drawn as Side and Top shapes: a heel, a lens, a pendant; owned by a garment when `garment` is given. */
export function prop(spec: PropSpec): RecipePart {
  const part: RecipePart = {
    id: spec.id,
    template: spec.template ?? "blob",
    on: spec.on,
    socket: typeof spec.socket === "number" ? socketName(spec.socket) : spec.socket,
    role: "other",
    rest: spec.rest ?? "forward",
    generic: spec.generic ?? SMALL,
    outlines: { side: outline(spec.side), top: outline(spec.top) },
    color: spec.color,
  };
  if (spec.garment) part.garment = spec.garment;
  if (spec.size !== undefined) part.size = spec.size;
  return part;
}

export interface RoundPropSpec extends Omit<PropSpec, "side" | "top"> {
  /** Its half width and half height across. */
  radius: number;
  /** How far it stands out from where it sits. */
  depth: number;
}

/** A round prop standing out `depth` from its socket, `radius` across: a button, a stud, a buckle's boss. */
export function roundProp(spec: RoundPropSpec): RecipePart {
  const half = spec.depth / 2;
  const disc = oval({ center: [half, 0], rx: half, ry: spec.radius });
  return prop({ ...spec, side: disc, top: disc });
}
