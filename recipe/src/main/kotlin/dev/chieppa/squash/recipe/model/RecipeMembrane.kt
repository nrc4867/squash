package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.DEFAULT_MEMBRANE_BILLOW
import dev.chieppa.squash.document.DEFAULT_MEMBRANE_EDGE
import dev.chieppa.squash.document.DEFAULT_MEMBRANE_SINK
import dev.chieppa.squash.document.DEFAULT_MEMBRANE_THICKNESS
import kotlinx.serialization.Serializable

/** A thin sheet of skin spanned between parts, by two rails of edges. */
@Serializable
@Doc("A thin sheet of skin spanned between parts: a patagium from wrist to ankle, a wing from arm and finger to flank. It rides the bones of the parts it is anchored to and inks only its free edge. Webs between the digits of one hand or foot are its template's webbing parameters instead.")
data class RecipeMembrane(
    val id: String,
    @Doc("The first rail: edges in order from the membrane's root to where its free edge meets this side.")
    val first: List<RecipeMembraneEdge>,
    @Doc("The second rail, the same way. The free edge runs between the two rails' ends.")
    val second: List<RecipeMembraneEdge>,
    @Doc("How far the free edge curves in between the rails' ends, a share of their reach: 0 straight.") @Range(min = 0.0, max = 0.6)
    val edge: Float = DEFAULT_MEMBRANE_EDGE,
    @Doc("How far its middle billows off the plane between its rails, a share of the gap between them: toward the ground where it lies level, out from the body where it hangs beside it; negative the other way.") @Range(min = -0.4, max = 0.4)
    val billow: Float = DEFAULT_MEMBRANE_BILLOW,
    @Doc("How thick it is half way between its rails, a share of how deep its rails sit in their parts.") @Range(min = 0.05, max = 0.8)
    val thickness: Float = DEFAULT_MEMBRANE_THICKNESS,
    @Doc("How deep into its parts its rails sit: 0 on their surface, 1 on their middle.") @Range(min = 0.0, max = 1.0)
    val sink: Float = DEFAULT_MEMBRANE_SINK,
    @Doc("Its palette slot; the base colour of the part its first rail starts on when left out.")
    val color: String? = null,
    @Doc("Whether it is built again on the other side: each part of a pair named by its left id is read as its right twin there, and v is mirrored on a part in the middle.")
    val mirrored: Boolean = true,
)

/** One edge of a membrane's rail. */
@Serializable
@Doc("A stretch of a part's surface a membrane is anchored along, from [u, v] to [u, v]; a socket point where the two are the same.")
data class RecipeMembraneEdge(
    @Doc("The part, by id; a pair by its left side's id.")
    val part: String,
    @Doc("[u, v] where the edge starts: u along the part from its socket end, v round it (0 top, 0.25 left, 0.5 underside, 0.75 right).")
    val from: List<Float>,
    @Doc("[u, v] where the edge ends; the same as from for a point.")
    val to: List<Float>? = null,
    @Doc("The run of the part it lies on, such as a digit (fingers:2); the part's first run reaching each u when left out.")
    val patch: String? = null,
)
