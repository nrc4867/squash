package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.BonePose
import kotlinx.serialization.Serializable

/**
 * A figure outline the body's tube parts are fitted to: the strokes of a front and a side view in body units, the pose they are drawn
 * in, and where the ground lies under the root part. Every tube part that gives no profiles takes its widths, its join's blend and the
 * reach of the socket it sits on from the fit.
 */
@Serializable
@Doc("The body drawn as a figure outline, front and side, which the tube parts that give no profiles are fitted to: their widths, the blend where each joins its parent, and where each limb's root sits. Head, hands and feet are left out: the strokes stop at the chin, the wrists and the ankles.")
data class RecipeFigure(
    @Doc("How high above the ground the outline stands on the root part's origin sits, in body units: its strokes' y is height above that ground.") @Range(min = 0.0)
    val rootHeight: Float,
    @Doc("The front view: the character's left half, x from the body's middle toward its left, y up from the ground. Each stroke runs with the figure on its right.")
    val front: List<RecipeFigureStroke> = emptyList(),
    @Doc("The side view from the character's left: x forward from the body's middle, y up from the ground. Each stroke runs with the figure on its right.")
    val side: List<RecipeFigureStroke> = emptyList(),
    @Doc("The pose the outline is drawn in, over the rest: each limb is read in its own bones as this pose shows them. The display pose when left out; [] for a figure drawn at rest.")
    val pose: List<BonePose>? = null,
    @Doc("Rounds of correction read off the built body's own silhouette, each a field evaluation: where the joined body lies off the outline, the parts' widths are moved by a share of it. 3 when left out; 0 builds the plain fit.") @Range(min = 0.0, max = 8.0)
    val rounds: Int? = null,
)

/** One open stretch of a figure's outline. */
@Serializable
@Doc("One open stretch of the outline: a smooth curve through a few places, sharp at its corners, or its Bézier points, running with the figure on its right.")
data class RecipeFigureStroke(
    @Doc("Its name, for messages and the misses a build marks.")
    val id: String,
    @Doc("Places the stroke passes through, [x, y] each: few, as drawn.")
    val through: List<List<Float>>? = null,
    @Doc("Indices into through that are sharp corners, as an armpit drawn tight.")
    val corners: List<Int> = emptyList(),
    @Doc("The Bézier points [x, y] themselves: a start point, then two handles and an end point per segment (3n + 1 points). In place of through.")
    val bezier: List<List<Float>>? = null,
    @Doc("Whether an end lying on the body's middle (x 0) runs on into its mirror image, so it crosses the middle level, as the crotch does.")
    val mirrored: Boolean = false,
)
