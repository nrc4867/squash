import type { BonePose, RecipeKey, RecipeKeyChange } from "../schema/recipe.ts";
import type { FigureOutlineStroke } from "./index.ts";

/** The strokes of a key's outline, as a figure outline's: only those that change, in either view. */
export interface KeyOutline {
  front?: FigureOutlineStroke[];
  side?: FigureOutlineStroke[];
}

export interface PoseKeyOptions {
  /** The pose the key corrects, over the rest: the bones it turns drive it. */
  pose: BonePose[];
  /** The outline redrawn in that pose, fitted by the build to changes of the parts it touches. */
  outline?: KeyOutline;
  /** Changes given directly; a part named here keeps these over what the outline fits. */
  changes?: RecipeKeyChange[];
  /** The ground's height under the root part's origin; the figure's when left out. */
  rootHeight?: number;
  /** Width rounds on the keyed body's silhouette; 4 when left out. */
  rounds?: number;
  /** Whether the outline fits the blends of the joins it reaches too; on when left out. */
  joins?: boolean;
}

function stroke(s: FigureOutlineStroke) {
  return {
    id: s.id,
    through: s.points.map((p) => [...p]),
    ...(s.corners?.length ? { corners: [...s.corners] } : {}),
    ...(s.mirrored ? { mirrored: true } : {}),
  };
}

/**
 * A recipe's pose key: the body's shape corrected in `pose`, by an outline redrawn in that pose (few points, smooth, only the strokes
 * that change) or by width and blend changes, blended in by joint angle as a pose nears it and gone at the rest.
 */
export function poseKey(name: string, options: PoseKeyOptions): RecipeKey {
  const { outline } = options;
  return {
    name,
    pose: options.pose,
    ...(outline
      ? {
          outline: {
            front: (outline.front ?? []).map(stroke),
            side: (outline.side ?? []).map(stroke),
            ...(options.rootHeight !== undefined ? { rootHeight: options.rootHeight } : {}),
            ...(options.rounds !== undefined ? { rounds: options.rounds } : {}),
            ...(options.joins === false ? { joins: false } : {}),
          },
        }
      : {}),
    ...(options.changes?.length ? { changes: options.changes } : {}),
  };
}
