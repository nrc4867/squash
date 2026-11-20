package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The views a head cheat is keyed at, by where the camera sits round the head's own facing. */
@Serializable
enum class KeyView(val degrees: Float) {
    @SerialName("front") FRONT(0f),
    @SerialName("three-quarter") THREE_QUARTER(45f),
    @SerialName("side") SIDE(90f),
    @SerialName("three-quarter-back") THREE_QUARTER_BACK(135f),
    @SerialName("back") BACK(180f),
    ;

    /** Whether the camera stands on the head's middle line here, so the view has no side. */
    val centred: Boolean get() = this == FRONT || this == BACK
}

/** What a key moves by name, found on each head: its muzzle, its ears or its nose. */
@Serializable
enum class KeyFeature {
    /** The head's run named muzzle, or the part on its muzzle socket, such as a beak. */
    @SerialName("muzzle") MUZZLE,

    /** The parts on the head's ear sockets, both of them. */
    @SerialName("ears") EARS,

    /** The head's piece named nose, such as nose leather, or the part on its nose socket. */
    @SerialName("nose") NOSE,

    /** The head itself, with everything on it: its shape. */
    @SerialName("head") HEAD,
}

/** How a head's face meets a turning camera. */
@Serializable
enum class FaceStyle {
    /** The face slides round toward the camera part of the way and the head's shape changes from view to view. */
    @SerialName("turning") TURNING,

    /** The head keeps one shape from the front round to the side and the face stays turned to the viewer, sliding only a little. */
    @SerialName("fixed") FIXED,
}

/**
 * How a key moves one thing on a head, in the head's own directions as seen with the camera on the head's left: forward along its
 * facing, up, and left, toward the camera; for the camera on the right Squash mirrors it. The thing is a [feature], or a [part] and
 * optionally one of its runs or pieces by name, [run]. It is moved by [forward], [up] and [left], shares of the head's length; turned
 * [yaw] degrees about the head's up (toward its left), [pitch] about its left (nose up) and [roll] about its facing, and [twist]
 * degrees about its own length (its front toward the head's left), each about its root; and scaled by [scale] about its root. A move
 * on a part carries everything on it.
 */
@Serializable
@Doc("How a key moves one thing on a head, in the head's own directions as seen with the camera on the head's left; Squash mirrors it for the camera on the right.")
data class KeyMove(
    @Doc("What it moves by name on each head: muzzle (the head's muzzle run, or the part on its muzzle socket such as a beak), ears (the parts on its ear sockets) or nose (its nose piece or the part on its nose socket). Give this or part.")
    val feature: KeyFeature? = null,
    @Doc("The part it moves, with everything on it. Give this or feature.")
    val part: PartId? = null,
    @Doc("One run or piece of the part by name, such as muzzle or nose, in place of the whole part.")
    val run: String? = null,
    @Doc("How far it moves along the head's facing, a share of the head's length.") @Range(min = -1.0, max = 1.0)
    val forward: Float = 0f,
    @Doc("How far it moves up the head, a share of the head's length.") @Range(min = -1.0, max = 1.0)
    val up: Float = 0f,
    @Doc("How far it moves toward the head's left, which is toward the camera in a keyed view, a share of the head's length.") @Range(min = -1.0, max = 1.0)
    val left: Float = 0f,
    @Doc("Degrees it turns about the head's up through its root, its front toward the head's left.") @Range(min = -90.0, max = 90.0)
    val yaw: Float = 0f,
    @Doc("Degrees it turns about the head's left through its root, its front up.") @Range(min = -90.0, max = 90.0)
    val pitch: Float = 0f,
    @Doc("Degrees it turns about the head's facing through its root, its top toward the head's left.") @Range(min = -90.0, max = 90.0)
    val roll: Float = 0f,
    @Doc("Degrees it turns about its own length through its root, its front toward the head's left: an ear opening toward the camera.") @Range(min = -90.0, max = 90.0)
    val twist: Float = 0f,
    @Doc("How much larger it is, about its root.") @Range(min = 0.25, max = 4.0)
    val scale: Float = 1f,
    @Doc("How much wider it is across the head, from left to right, about its root.") @Range(min = 0.25, max = 4.0)
    val width: Float = 1f,
    @Doc("How much taller it is along the head's up, about its root.") @Range(min = 0.25, max = 4.0)
    val height: Float = 1f,
    @Doc("How much deeper it is along the head's facing, about its root.") @Range(min = 0.25, max = 4.0)
    val depth: Float = 1f,
    @Doc("One of the part's areas by name, such as chin or cheeks: the move then shapes only that area, eased out past its edge, on the one surface.")
    val area: String? = null,
    @Doc("How far its surface swells out along its normals, a share of the head's length; negative sinks it.") @Range(min = -0.5, max = 0.5)
    val push: Float = 0f,
    @Doc("How far the half of its surface on the camera's side swells out along its normals, a share of the head's length: the near cheek filling out.") @Range(min = -0.5, max = 0.5)
    val nearPush: Float = 0f,
) {
    init {
        require((feature == null) != (part == null)) { "A key move names a feature or a part, not both and not neither" }
        require(run == null || part != null) { "A key move names a run only on a part" }
        require(listOf(scale, width, height, depth).all { it in MIN_KEY_SCALE..MAX_KEY_SCALE }) { "A key move scales from $MIN_KEY_SCALE to $MAX_KEY_SCALE" }
        require(area == null || feature == KeyFeature.HEAD || part != null) { "A key move names an area only on a part or the head" }
    }

    /** Whether it leaves what it moves where it is. */
    val isRest: Boolean get() = isStill && push == 0f && nearPush == 0f

    /** Whether it moves, turns and scales nothing, whatever it swells. */
    val isStill: Boolean
        get() = forward == 0f && up == 0f && left == 0f && yaw == 0f && pitch == 0f && roll == 0f && twist == 0f && scale == 1f &&
            width == 1f && height == 1f && depth == 1f

    /** Whether it reaches only some of the surface, an area or a swelling, rather than moving its element whole. */
    val isSoft: Boolean get() = area != null || push != 0f || nearPush != 0f
}

/** The least and most a key move scales by. */
const val MIN_KEY_SCALE = 0.25f
const val MAX_KEY_SCALE = 4f

/**
 * A head cheat at one [view]: the [moves] it makes there, for the camera on the head's [side], or on either side, mirrored for the
 * right, when null. The front and back have no side.
 */
@Serializable
@Doc("A head cheat at one view: what it moves there.")
data class ViewKey(
    @Doc("The view: front, three-quarter, side, three-quarter-back or back, by where the camera sits round the head's own facing.")
    val view: KeyView,
    @Doc("The camera's side of the head this key holds for: left or right. Left out, the key is for the left and Squash mirrors it for the right; the front and back take none.")
    val side: Side? = null,
    @Doc("What it moves.")
    val moves: List<KeyMove> = emptyList(),
) {
    init {
        require(side == null || !view.centred) { "The ${view.name.lowercase()} view has no side" }
    }
}

/**
 * A character's head cheats that follow the camera: with [auto], every head's defaults (its muzzle leaning toward the camera, its ears
 * turning toward profile, its face style's shape keys and its own back), and over them [keys], each move replacing the default for the
 * same thing at its view. [face] is how its faces meet a turning camera, [faceFollow] how far the face slides round toward it and
 * [keepFace] whether it slides as one piece; each left null takes the head template's default. See HLD: look, View keys.
 */
@Serializable
@Doc("Head cheats that follow the camera: what each view moves on the head, blended between neighbouring views as the camera turns round the head's own facing.")
data class ViewKeys(
    @Doc("Whether every head's default keys apply: its muzzle leans toward the camera and its ears turn toward profile. On when left out.")
    val auto: Boolean = true,
    @Doc("Keys per view, each move replacing the default for the same thing at that view.")
    val keys: List<ViewKey> = emptyList(),
    @Doc("How the face meets a turning camera: turning (the face slides part way round toward the camera and the head's shape changes from view to view) or fixed (the head keeps one shape from the front round to the side and the face stays turned to the viewer). The head template's when left out.")
    val face: FaceStyle? = null,
    @Doc("How far the face's features (eyes, brows, nose, mouth, muzzle, cheek marks) slide round the head toward the camera, from 0 (fixed on the head) to 1 (centred on the viewer up to the side view). Past the side view they slide back and hide behind the head. The face style's when left out.") @Range(min = 0.0, max = 1.0)
    val faceFollow: Float? = null,
    @Doc("Whether the face slides as one piece and keeps its features' size, rather than gathering toward the far side as it turns. The face style's when left out.")
    val keepFace: Boolean? = null,
) {
    init {
        require(faceFollow == null || faceFollow in 0f..1f) { "A face follows from 0 to 1" }
    }

    /** Whether nothing is keyed: no defaults, no face following and no keys. */
    val off: Boolean get() = !auto && (faceFollow ?: 0f) == 0f && keys.all { key -> key.moves.all { it.isRest } }
}

/** Sets the character's view keys. */
@Serializable
@SerialName("setViewKeys")
data class SetViewKeys(val keys: ViewKeys) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(viewKeys = keys)), SetViewKeys(document.character.viewKeys))
}
