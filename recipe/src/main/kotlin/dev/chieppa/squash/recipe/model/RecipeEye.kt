package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.EyeLook
import kotlinx.serialization.Serializable

/** A painted eye on a head, mirrored across it unless told not to, placed by u, v or by a place on the face. */
@Serializable
@Doc("A painted eye on a head. Place it by u and v, or by face [x, y] seen from straight ahead (x to the character's left).")
data class RecipeEye(
    val id: String,
    @Doc("The head it is painted on.")
    val on: String,
    @Doc("The patch u and v lie on.")
    val patch: String = BODY_PATCH,
    @Doc("Where along the head its middle sits.")
    val u: Float? = null,
    @Doc("Where round the head its middle sits.")
    val v: Float? = null,
    @Doc("Its middle as [x, y] on the face seen from straight ahead, in place of u and v.")
    val face: List<Float>? = null,
    @Doc("How it looks: opening, iris, pupil, highlight, lid, lashes, brow, and their palette slots (white, iris, pupil, highlight, ink).")
    val look: EyeLook = EyeLook(),
    @Doc("How it looks when the head is seen from the front; the look alone when left out.")
    val front: EyeLook? = null,
    @Doc("How far from its look to the front look a front view goes, 0 to 1.")
    val fromFront: Float = 1f,
    @Doc("Gaze toward the head's front, a share of the opening's half width.")
    val gazeForward: Float = 0f,
    @Doc("Gaze up, a share of the opening's half height.")
    val gazeUp: Float = 0f,
    @Doc("Whether it is painted again on the other side of the head.")
    val mirrored: Boolean = true,
    @Doc("Whether it lies flat across the face, a decal projected from the way the head faces (turned out by the look's lookOut), in place of following the head's curve from the surface's normal at its middle. Flat keeps a large eye on a small, round head reading as drawn from the front.")
    val flat: Boolean = false,
    @Doc("How far the upper lid stands down from open (0) to the look's closed line (1).") @Range(min = 0.0, max = 1.0)
    val upperClose: Float = 0f,
    @Doc("How far the lower lid stands up from open (0) to the look's closed line (1).") @Range(min = 0.0, max = 1.0)
    val lowerRise: Float = 0f,
)
