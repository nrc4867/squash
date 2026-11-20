package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.copies.DEGREES_TO_RADIANS
import dev.chieppa.squash.character.copies.turnedAbout
import dev.chieppa.squash.document.FaceStyle
import dev.chieppa.squash.document.KeyView
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.ViewKeys
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.smoothstep

/** How far a fixed face follows the camera. */
private const val FIXED_FOLLOW = 0.85f

/** How far a turning face follows the camera on a head template that names none of its own. */
private const val TURNING_FOLLOW = 0.3f

/** Each head template's face style and how far its turning face follows the camera. */
private val TEMPLATE_FACES: Map<String, Pair<FaceStyle, Float>> = mapOf(
    "head" to (FaceStyle.TURNING to 0.3f),
    "head-round" to (FaceStyle.TURNING to 0.45f),
    "head-bird" to (FaceStyle.TURNING to 0.25f),
)

/**
 * Where a face's mask starts and is whole, by how far a point lies toward the head's facing from its middle: one sliding as one piece is
 * whole over the front half, one gathering eases up across the whole front.
 */
private const val MASK_BACK = -0.85f
private const val KEEP_WHOLE = 0.1f
private const val GATHER_WHOLE = 0.95f

/** Above this share of the way up from the head's middle a point leaves the face, twice as fast as it rises, so the crown and the ears' roots stay. */
private const val CROWN = 0.5f
private const val CROWN_FALL = 2f

/**
 * How a head's face follows the camera: [follow] from 0 (fixed on the head) to 1 (turned fully to the viewer up to the side view), and
 * whether it [keeps] its features' size by sliding as one piece.
 */
data class FaceFollow(val style: FaceStyle, val follow: Float, val keeps: Boolean) {
    /**
     * How many degrees the face turns round the head's up toward the camera at [view]: the follow's share of the view's angle up to the
     * side; none from three-quarter behind round to the back, so past the side the face slides back onto the head and hides behind it.
     */
    fun degreesAt(view: KeyView): Float = when (view) {
        KeyView.THREE_QUARTER, KeyView.SIDE -> follow * view.degrees
        KeyView.FRONT, KeyView.THREE_QUARTER_BACK, KeyView.BACK -> 0f
    }

    /**
     * How much of a turn the face takes at [point], from 0 to 1: whole on the face, nothing over the back and the crown, eased
     * between, read about [frame]'s middle.
     */
    fun mask(point: Vec3, frame: HeadFrame): Float {
        val from = point - frame.center
        if (from.length == 0f) return 1f
        val way = from.normalized()
        val front = (way dot frame.forward) - maxOf(0f, (way dot frame.up) - CROWN) * CROWN_FALL
        return smoothstep(MASK_BACK, if (keeps) KEEP_WHOLE else GATHER_WHOLE, front)
    }

    /** [point] turned [degrees] round [frame]'s up through its middle. */
    fun turned(point: Vec3, frame: HeadFrame, degrees: Float): Vec3 =
        if (degrees == 0f) point else frame.center + (point - frame.center).turnedAbout(frame.up, degrees * DEGREES_TO_RADIANS)

    companion object {
        val NONE = FaceFollow(FaceStyle.TURNING, 0f, false)

        /** How a head of [template] follows the camera under [keys]: their own choices over the template's, none without defaults. */
        fun of(keys: ViewKeys, template: TemplateId): FaceFollow {
            val (templateStyle, templateFollow) = TEMPLATE_FACES[template.value] ?: (FaceStyle.TURNING to TURNING_FOLLOW)
            val style = keys.face ?: templateStyle
            val styleFollow = if (style == FaceStyle.FIXED) FIXED_FOLLOW else if (keys.face == null) templateFollow else TURNING_FOLLOW
            val follow = keys.faceFollow ?: if (keys.auto) styleFollow else 0f
            return FaceFollow(style, follow, keys.keepFace ?: (style == FaceStyle.FIXED))
        }

        /** The face style of a head of [template] under [keys]. */
        fun styleOf(keys: ViewKeys, template: TemplateId): FaceStyle = keys.face ?: TEMPLATE_FACES[template.value]?.first ?: FaceStyle.TURNING
    }
}
