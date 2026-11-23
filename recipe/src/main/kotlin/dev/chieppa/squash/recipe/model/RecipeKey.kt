package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.BonePose
import kotlinx.serialization.Serializable

/**
 * A pose key: the body's shape corrected in one pose, blended in as a pose nears it by the angles of the bones its pose turns and gone
 * at the rest. The correction is a figure outline redrawn in that pose, fitted to changes of the parts' widths and joins, or the
 * changes themselves.
 */
@Serializable
@Doc("A pose key: a correction of the body's shape in one pose, blended in by joint angle as a pose nears it and gone at the rest the body is built in. Give an outline redrawn in the key's pose, which the build fits to changes of the parts it touches, or the changes themselves; both, and a part's changes given replace the fitted ones.")
data class RecipeKey(
    @Doc("Its name, unique among the keys.")
    val name: String,
    @Doc("The pose it corrects, over the rest: the bones it turns drive it. A pair's bones on one side drive that side's half of the body; a pose naming one side only drives the other through the twin bones.")
    val pose: List<BonePose>,
    @Doc("The figure outline redrawn in the key's pose: only the strokes that change, few points, smooth. Read in each limb's own bones as posed, and fitted to changes of the tube parts' widths and of the blends where they join.")
    val outline: RecipeKeyOutline? = null,
    @Doc("Changes of the parts' widths and joins, read over the parts' own settings, so an edit of the rest keeps them.")
    val changes: List<RecipeKeyChange> = emptyList(),
)

/** A figure outline redrawn in a key's pose. */
@Serializable
@Doc("A figure outline redrawn in a key's pose: strokes as a figure's, in body units standing on the ground, only where the key changes the body.")
data class RecipeKeyOutline(
    @Doc("The front view: the character's left half, x from the body's middle toward its left, y up from the ground. Each stroke runs with the figure on its right.")
    val front: List<RecipeFigureStroke> = emptyList(),
    @Doc("The side view from the character's left: x forward from the body's middle, y up from the ground.")
    val side: List<RecipeFigureStroke> = emptyList(),
    @Doc("How high above the ground the root part's origin sits, in body units; the figure's when left out.") @Range(min = 0.0)
    val rootHeight: Float? = null,
    @Doc("Rounds of correction on the keyed body's silhouette. 4 when left out.") @Range(min = 1.0, max = 8.0)
    val rounds: Int? = null,
    @Doc("Whether the blends where the parts it reaches join are fitted to it too. On when left out.")
    val joins: Boolean = true,
)

/** What a key changes on one part. */
@Serializable
@Doc("What a key changes on one part, read over its own settings.")
data class RecipeKeyChange(
    @Doc("The part: a pair by its id changes both sides.")
    val part: String,
    @Doc("Width scales by direction: forward (or front), back, up, down, left, right, outer or inner as a tube's profiles name them, or side+, side-, top+ and top- for a side of the part's Side or Top outline. Each is a run of [x, scale] places along the part, eased between them and held level past its ends.")
    val widths: Map<String, List<List<Float>>> = emptyMap(),
    @Doc("How much the blend where it joins its parent is scaled by; a part with no blend of its own keeps its default.") @Range(min = 0.25, max = 4.0)
    val blend: Float? = null,
)
