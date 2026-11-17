package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import kotlinx.serialization.Serializable

/**
 * A drawn outline in a part's own axes: either a few places it passes [through], smooth but at its [corners], or the joined cubic
 * Béziers themselves as [bezier], a start point then two handles and an end point per segment.
 */
@Serializable
@Doc("A drawn outline: a few [x, y] places it passes through (smooth, with corners where named), or the cubic Bézier points themselves. x runs along the part from its socket end; y is across.")
data class RecipeOutline(
    @Doc("Places the outline passes through, [x, y] each. Few points: 6 to 14 for a body, 3 to 8 for a limb.")
    val through: List<List<Float>>? = null,
    @Doc("Indices into through that are sharp corners, as an ear's tip.")
    val corners: List<Int> = emptyList(),
    @Doc("Whether through closes back to its first place, as a torso or head drawn all round does; an open outline runs from one side of the axis to the other.")
    val closed: Boolean = false,
    @Doc("The Bézier points [x, y] themselves: a start point, then two handles and an end point per segment (3n + 1 points). In place of through.")
    val bezier: List<List<Float>>? = null,
)
