package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.Vec3

/** How deep a color's default shadow tone shifts in two-tone. See [shadeShifted]. */
private const val SHADOW_DEPTH = 0.24f

/** How deep a crease's or a wrinkle's shade band shifts what it lies on, as a shadow tone shifts its color. */
private const val CREASE_DEPTH = 0.3f

/** How much of a color the far side of a pair keeps. */
private const val FAR_SIDE_SHARE = 0.82f

/**
 * How far off the body's plane the camera is, as a share of its distance, where a far side starts to darken and where it is all the
 * way dark: head-on neither side is far, and an orbit passes through the ramp with no step in tone.
 */
private const val HEAD_ON_SHARE = 0.02f
private const val FULLY_FAR_SHARE = 0.22f

/** How much of its [shadeShifted] depth each channel of a saturated color loses: blue most, so it warms and gains saturation as it falls. */
private const val WARM_RED_LOSS = 0.7f
private const val WARM_GREEN_LOSS = 1.1f
private const val WARM_BLUE_LOSS = 1.7f

/** How much of its depth each channel of a pale, grey or white color loses: green a little more than blue, so white falls to a cool lilac grey. */
private const val COOL_RED_LOSS = 1.05f
private const val COOL_GREEN_LOSS = 1.1f
private const val COOL_BLUE_LOSS = 0.8f

/** The saturation, max less min over max, below which a color shades all cool and above which all warm, eased between. */
private const val COOL_BELOW = 0.06f
private const val WARM_ABOVE = 0.22f

/** How far from straight up or down the view must be, as the length of its level part, for a view light to turn with it. */
private const val LEAST_LEVEL = 0.05f

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

/** Whether the light turns with the view, so every side is lit as the front is, or stays fixed to the body. */
enum class LightFrame { VIEW, BODY }

/**
 * One object's shading choice. Flat by default, which needs nothing but its color. [toLight] is the way to the light in body space as seen
 * from the front; with a [LightFrame.VIEW] light it turns round the body's up with the camera, so a back or side view is lit from the
 * viewer's front, above and left as the front view is.
 */
@Immutable
data class ObjectShading(
    val mode: Shading = Shading.FLAT,
    val toLight: Vec3 = PartMaterial.DEFAULT_TO_LIGHT,
    val shadowDepth: Float = SHADOW_DEPTH,
    val farSide: FarSideDarkening = FarSideDarkening(),
    val light: LightFrame = LightFrame.VIEW,
) {
    /** The way to the light seen with the camera at [toCamera] from the body. */
    fun lightFor(toCamera: Vec3): Vec3 = if (light == LightFrame.BODY) toLight else viewLight(toLight, toCamera)
}

/**
 * [toLight], set for a camera in front of the body along x, turned round y to where [toCamera] lies round it; unturned for a view from
 * straight above or below.
 */
fun viewLight(toLight: Vec3, toCamera: Vec3): Vec3 {
    val level = kotlin.math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z)
    if (level < LEAST_LEVEL * toCamera.length || level == 0f) return toLight
    val c = toCamera.x / level
    val s = toCamera.z / level
    return Vec3(toLight.x * c - toLight.z * s, toLight.y, toLight.x * s + toLight.z * c)
}

/** The two colors a color shows as: [lit], and [shadow] where two-tone falls in shade. */
@Immutable
data class Tones(val lit: Color, val shadow: Color)

/** Turns a color and its slot's own shadow tone, if it has one, into what it shows as on one object in one view. */
fun interface ToneRule {
    fun tones(color: Color, ownShadow: Color?, shading: ObjectShading, side: BodySide?, toCamera: Vec3): Tones
}

/** A color kept as it is when lit; in shadow its slot's own tone or a [shadeShifted] one; all of it shifted further on a far side. */
val DefaultToneRule = ToneRule { color, ownShadow, shading, side, toCamera ->
    val far = 1f - shading.farSide.shareFor(side, toCamera)
    Tones(color.shadeShifted(far), (ownShadow ?: color.shadeShifted(shading.shadowDepth)).shadeShifted(far))
}

/**
 * This color darker by [depth], at its own alpha, its channels falling unevenly by how saturated it is: a saturated color warms, hue
 * moving toward orange, so it does not turn grey; a pale, grey or white one cools to a lilac grey, so white in shade reads
 * as white and not tan.
 */
fun Color.shadeShifted(depth: Float): Color {
    if (depth == 0f) return this
    val most = maxOf(red, green, blue)
    val saturation = if (most > 0f) (most - minOf(red, green, blue)) / most else 0f
    val warm = ((saturation - COOL_BELOW) / (WARM_ABOVE - COOL_BELOW)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    fun loss(cool: Float, warmLoss: Float) = 1f - (cool + (warmLoss - cool) * warm) * depth
    return Color(
        (red * loss(COOL_RED_LOSS, WARM_RED_LOSS)).coerceIn(0f, 1f),
        (green * loss(COOL_GREEN_LOSS, WARM_GREEN_LOSS)).coerceIn(0f, 1f),
        (blue * loss(COOL_BLUE_LOSS, WARM_BLUE_LOSS)).coerceIn(0f, 1f),
        alpha,
    )
}

/**
 * What a crease's shade band multiplies the color under it by: white shifted by [CREASE_DEPTH] as a shadow tone shifts white, a cool
 * grey, so a band darkens any color without turning it tan. In a two-tone shadow the band multiplies by three quarters as much.
 */
val CREASE_TONES: Tones = Color.White.shadeShifted(CREASE_DEPTH).let { Tones(it, Color.White.shadeShifted(CREASE_DEPTH * IN_SHADOW_SHARE)) }

/** How deep a cloth fold's band shifts what it lies on: lighter than a joint's crease, so hanging folds read soft. */
private const val FOLD_DEPTH = 0.16f

/** What a cloth fold's band multiplies the color under it by, as [CREASE_TONES] but lighter. */
val FOLD_TONES: Tones = Color.White.shadeShifted(FOLD_DEPTH).let { Tones(it, Color.White.shadeShifted(FOLD_DEPTH * IN_SHADOW_SHARE)) }

/** The luminance under which cloth is dark enough for its folds' ridges to show a highlight, how far toward white a highlight lies, and its most opacity. */
private const val DARK_CLOTH = 0.4f
private const val HIGHLIGHT_LIFT = 0.35f
const val HIGHLIGHT_OPACITY = 0.6f

/** Whether [this] color is dark enough that shade alone barely shows on it, so its folds' ridges catch a highlight. */
val Color.isDarkCloth: Boolean get() = 0.2126f * red + 0.7152f * green + 0.0722f * blue < DARK_CLOTH

/** The highlight a fold's ridge shows on cloth in [tones]: each tone lifted toward white. */
fun highlightOf(tones: Tones): Tones = Tones(tones.lit.lifted(), tones.shadow.lifted())

private fun Color.lifted(): Color = Color(red + (1f - red) * HIGHLIGHT_LIFT, green + (1f - green) * HIGHLIGHT_LIFT, blue + (1f - blue) * HIGHLIGHT_LIFT, alpha)

/** How much of its depth a crease's band keeps where it lies in a two-tone shadow. */
private const val IN_SHADOW_SHARE = 0.75f
