import type { EyeLidMark, EyeLids, RecipeEye } from "../schema/recipe.ts";

/** How closed lids meet: on a straight line, arched up in the middle as a happy closed eye, or sagging below the middle. */
export type ClosedShape = "straight" | "smile" | "frown";

const CLOSED: Record<ClosedShape, Pick<EyeLids, "closedAt" | "closedBend">> = {
  straight: { closedAt: -0.25, closedBend: 0 },
  smile: { closedAt: 0.2, closedBend: 0.45 },
  frown: { closedAt: -0.4, closedBend: -0.35 },
};

/**
 * Lids that meet on a closed line of [shape], turned [tilt] degrees with its front end up: `smile` arches its middle up as a happy closed
 * eye does, `frown` sags it; [lids] laid over these.
 */
export function closedLids(shape: ClosedShape = "straight", tilt = 0, lids: EyeLids = {}): EyeLids {
  return { ...CLOSED[shape], ...(tilt !== 0 ? { closedTilt: tilt } : {}), ...lids };
}

/** How an eyeshadow lies on the upper lid: the colour it fades into, how far up it fades and how far it reaches above the open lid. */
export interface EyeshadowSpec {
  fadeTo?: string;
  fade?: number;
  above?: number;
}

/** The upper lid painted [slot] from the lash line, fading up over `fade` heights into `fadeTo` or the skin, reaching `above` over the open lid. */
export function eyeshadow(slot: string, spec: EyeshadowSpec = {}, lids: EyeLids = {}): EyeLids {
  return { slot, fade: spec.fade ?? 0.8, above: spec.above ?? 0.35, ...(spec.fadeTo ? { fadeTo: spec.fadeTo } : {}), ...lids };
}

/** A liner wing from the back corner of the lash line, out and up past the corner; [mark] laid over the defaults. */
export function linerWing(mark: EyeLidMark = {}): EyeLidMark {
  return { along: -1, above: 0, out: 0.45, up: 0.3, width: 0.1, bend: 0.4, ...mark };
}

/** Where an eye's lids stand: 0 open, 1 on the closed line. */
export interface LidStand {
  upper?: number;
  lower?: number;
}

/** [eye] with its lids standing as [stand] says, for a character saved mid blink or drowsy. */
export function lidsAt(eye: RecipeEye, stand: LidStand): RecipeEye {
  return { ...eye, upperClose: stand.upper ?? 0, lowerRise: stand.lower ?? 0 };
}
