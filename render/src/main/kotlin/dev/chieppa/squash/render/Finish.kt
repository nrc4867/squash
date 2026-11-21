package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The shape a shine from the light takes, as [FINISH_WGSL] reads it. */
enum class Shine(val code: Float) {
    NONE(0f),
    SPOT(1f),
    BAND(2f),
    STREAK(3f),
}

/**
 * How a fill shades past its lit and shadow tones: in [steps] flat tones, the middle one of three halfway between them; a [rim] of
 * [rimWidth] along its silhouette; a [shine] of [shineSize] in [shineColor] from the light, a band bordered by [reflection]; and a [glow]
 * of that strength, which ignores the light, with a halo [halo] pixels wide. [PLAIN] draws as two tones always have.
 */
@Immutable
data class Finish(
    val steps: Int = 2,
    val rimWidth: Float = 0f,
    val rim: Color = Color.White,
    val shine: Shine = Shine.NONE,
    val shineSize: Float = 0f,
    val shineColor: Color = Color.White,
    val reflection: Color = Color.Black,
    val glow: Float = 0f,
    val halo: Float = 0f,
) {
    /** Whether this draws as two tones always have, so the shaders keep their plain path. */
    val plain: Boolean get() = this == PLAIN

    /** Whether it draws a halo round itself. */
    val glowing: Boolean get() = glow > 0f && halo > 0f

    /** The vectors [FINISH_WGSL] reads, [FINISH_VECTORS] of them, into [data] from [at]; the next free place. */
    fun writeInto(data: FloatArray, at: Int): Int {
        var next = at
        fun put(a: Float, b: Float, c: Float, d: Float) {
            data[next] = a
            data[next + 1] = b
            data[next + 2] = c
            data[next + 3] = d
            next += VECTOR
        }
        put(if (plain) 0f else 1f, steps.toFloat(), rimWidth, shine.code)
        put(0f, 0f, 0f, shineSize)
        put(rim.red, rim.green, rim.blue, glow)
        put(shineColor.red, shineColor.green, shineColor.blue, halo)
        put(reflection.red, reflection.green, reflection.blue, 0f)
        return next
    }

    companion object {
        val PLAIN = Finish()
    }
}

/** Vectors a [Finish] takes in a uniform. */
internal const val FINISH_VECTORS = 5

private const val VECTOR = 4
