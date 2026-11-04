package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.Vec3

/** How deep a color's default shadow tone shifts in two-tone. See [warmShifted]. */
private const val SHADOW_DEPTH = 0.24f

/** How much of a color the far side of a pair keeps. */
private const val FAR_SIDE_SHARE = 0.82f

/**
 * How far off the body's plane the camera is, as a share of its distance, where a far side starts to darken and where it is all the
 * way dark: head-on neither side is far, and an orbit passes through the ramp with no step in tone.
 */
private const val HEAD_ON_SHARE = 0.02f
private const val FULLY_FAR_SHARE = 0.22f

/** How much of its [warmShifted] depth each channel of a color loses: blue most, so a pale color warms and gains saturation as it falls. */
private const val WARM_RED_LOSS = 0.7f
private const val WARM_GREEN_LOSS = 1.1f
private const val WARM_BLUE_LOSS = 1.7f

/** How a mesh is lit: one color, or a lit color and a shadow color with a hard edge. */
enum class Shading { FLAT, TWO_TONE }

/** Which side of the body's plane, z = 0, a paired part lies on. */
enum class BodySide { POSITIVE_Z, NEGATIVE_Z }

/** Darkens the side of a pair facing away from the camera, passing from one side to the other as the character turns. */
@Immutable
data class FarSideDarkening(val enabled: Boolean = true, val share: Float = FAR_SIDE_SHARE) {
    /** What share of its color a part on [side] keeps, seen with the camera at [toCamera] from the body. */
    fun shareFor(side: BodySide?, toCamera: Vec3): Float {
        if (!enabled || side == null) return 1f
        val away = -(if (side == BodySide.POSITIVE_Z) toCamera.z else -toCamera.z) / toCamera.length
        val t = ((away - HEAD_ON_SHARE) / (FULLY_FAR_SHARE - HEAD_ON_SHARE)).coerceIn(0f, 1f)
        return 1f + (share - 1f) * t * t * (3f - 2f * t)
    }
}

/** One object's shading choice. Flat by default, which needs nothing but its color. */
@Immutable
data class ObjectShading(
    val mode: Shading = Shading.FLAT,
    val toLight: Vec3 = PartMaterial.DEFAULT_TO_LIGHT,
    val shadowDepth: Float = SHADOW_DEPTH,
    val farSide: FarSideDarkening = FarSideDarkening(),
)

/** The two colors a color shows as: [lit], and [shadow] where two-tone falls in shade. */
@Immutable
data class Tones(val lit: Color, val shadow: Color)

/** Turns a color and its slot's own shadow tone, if it has one, into what it shows as on one object in one view. */
fun interface ToneRule {
    fun tones(color: Color, ownShadow: Color?, shading: ObjectShading, side: BodySide?, toCamera: Vec3): Tones
}

/** A color kept as it is when lit; in shadow its slot's own tone or a warm shift of it; all of it shifted further on a far side. */
val DefaultToneRule = ToneRule { color, ownShadow, shading, side, toCamera ->
    val far = 1f - shading.farSide.shareFor(side, toCamera)
    Tones(color.warmShifted(far), (ownShadow ?: color.warmShifted(shading.shadowDepth)).warmShifted(far))
}

/**
 * This color darker by [depth] and warmer with it, at its own alpha: the channels fall unevenly, so hue moves toward orange and a
 * pale color does not turn grey.
 */
fun Color.warmShifted(depth: Float): Color = if (depth == 0f) this else Color(
    red * (1f - WARM_RED_LOSS * depth),
    green * (1f - WARM_GREEN_LOSS * depth),
    blue * (1f - WARM_BLUE_LOSS * depth),
    alpha,
)
