package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/**
 * A tube's widths as curves along its bone: one open curve a direction, x along the part from its socket end, y the radius toward that
 * direction, built as drawn into the part's Side and Top outlines, rounded at its tip (and its start when [closed]).
 */
@Serializable
@Doc("A tube part's widths as curves along its bone, one a direction: x along the part from its socket end, y its radius that way. They become its Side and Top outlines as drawn, rounded to the axis at its length (and at 0 when closed). On a figure, the fit draws the profiles it leaves out.")
data class RecipeTube(
    @Doc("Its length along its bone: outline x runs 0 to this.") @Range(min = 0.0)
    val length: Float,
    @Doc("Radius curves by direction: forward (or front), back, up, down, left and right; a pair's left side may name outer (its left) and inner (its right). Its rest decides which two Side takes and which two Top takes (frames.md); a Top direction left out mirrors the other. Each is an open curve through a few [x, r] places, smooth but at its corners, or its Bézier points.")
    val profiles: Map<String, RecipeOutline> = emptyMap(),
    @Doc("Rounded at its socket end too, as a torso or head drawn all round is; open there when left out, as a limb is.")
    val closed: Boolean = false,
    @Doc("Ends square at its length rather than rounding over: a sleeve's cut end.")
    val flat: Boolean = false,
)
