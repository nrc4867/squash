package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.Vec3

/** How much of a color its shadow tone keeps in two-tone. */
private const val SHADOW_SHARE = 0.72f

/** How much of a color the far side of a pair keeps. */
private const val FAR_SIDE_SHARE = 0.82f

/** How far off the body's plane the camera must be, as a share of its distance, for a side to count as far: head-on neither is. */
private const val HEAD_ON_SHARE = 0.02f

/** How a mesh is lit: one color, or a lit color and a shadow color with a hard edge. */
enum class Shading { FLAT, TWO_TONE }

/** Which side of the body's plane, z = 0, a paired part lies on. */
enum class BodySide { POSITIVE_Z, NEGATIVE_Z }

/** Darkens the side of a pair facing away from the camera, swapping as the character turns. */
@Immutable
data class FarSideDarkening(val enabled: Boolean = true, val share: Float = FAR_SIDE_SHARE) {
    /** What share of its color a part on [side] keeps, seen with the camera at [toCamera] from the body. */
    fun shareFor(side: BodySide?, toCamera: Vec3): Float {
        if (!enabled || side == null) return 1f
        val across = if (side == BodySide.POSITIVE_Z) toCamera.z else -toCamera.z
        return if (across < -HEAD_ON_SHARE * toCamera.length) share else 1f
    }
}

/** One object's shading choice. Flat by default, which needs nothing but its color. */
@Immutable
data class ObjectShading(
    val mode: Shading = Shading.FLAT,
    val toLight: Vec3 = PartMaterial.DEFAULT_TO_LIGHT,
    val shadowShare: Float = SHADOW_SHARE,
    val farSide: FarSideDarkening = FarSideDarkening(),
)

/** The two colors a color shows as: [lit], and [shadow] where two-tone falls in shade. */
@Immutable
data class Tones(val lit: Color, val shadow: Color)

/** Turns a color into what it shows as on one object in one view. */
fun interface ToneRule {
    fun tones(color: Color, shading: ObjectShading, side: BodySide?, toCamera: Vec3): Tones
}

/** A color kept as it is when lit and multiplied down in shadow, all of it darker on a far side. */
val DefaultToneRule = ToneRule { color, shading, side, toCamera ->
    val far = shading.farSide.shareFor(side, toCamera)
    Tones(color.times(far), color.times(far * shading.shadowShare))
}

/** This color's channels times [share], at its own alpha. */
private fun Color.times(share: Float) = if (share == 1f) this else Color(red * share, green * share, blue * share, alpha)
