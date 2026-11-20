package dev.chieppa.squash.character.block

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.DigitAxis
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()

/**
 * One digit's own shape on a part, from parameters named for its patch with a dot in place of the colon: `fingers.2.length` multiplies
 * the third finger's length, `toes.0.width` the inner toe's thickness, and `fingers.thumb.spread` turns the thumb that many degrees
 * further out in the fan. They are read after a drawing fits the solid, so a digit drawn longer on the sheet stays as long.
 */
object DigitOverrides {
    const val LENGTH = "length"
    const val WIDTH = "width"
    const val SPREAD = "spread"

    private val keys = setOf(LENGTH, WIDTH, SPREAD)

    /** Whether [name] is a digit's own parameter: a row, a digit and one of [LENGTH], [WIDTH] and [SPREAD], dot-separated. */
    fun isOverride(name: String): Boolean {
        val pieces = name.split('.')
        return pieces.size == 3 && pieces[0].isNotEmpty() && pieces[1].isNotEmpty() && pieces[2] in keys
    }

    /** The parameter naming [key] of the digit whose patch is [patch], `row:digit`. */
    fun param(patch: String, key: String): String = patch.replace(':', '.') + "." + key

    /** [values] without any digit's own parameter. */
    fun without(values: Map<String, Float>): Map<String, Float> = values.filterKeys { !isOverride(it) }

    /**
     * [axis], the digit with patch [patch], lengthened, thickened and turned by its own parameters in [values]; turned in the plane of
     * the part's axis and [across], the coordinate its row's roots run across, away from the row's middle at [middle].
     */
    fun apply(axis: DigitAxis, patch: String, values: Map<String, Float>, across: Int, middle: Float): DigitAxis {
        val length = values[param(patch, LENGTH)] ?: 1f
        val width = values[param(patch, WIDTH)] ?: 1f
        val spread = values[param(patch, SPREAD)] ?: 0f
        if (length == 1f && width == 1f && spread == 0f) return axis
        val side = if (coordinate(axis.root, across) >= middle) 1f else -1f
        val direction = if (spread == 0f) axis.direction else turned(axis.direction, across, side * spread * DEGREES_TO_RADIANS)
        return DigitAxis(axis.root, direction, axis.length * length, axis.radius * width, axis.beyond * length)
    }

    private fun coordinate(p: Vec3, across: Int): Float = if (across == 1) p.y else p.z

    private fun turned(d: Vec3, across: Int, radians: Float): Vec3 {
        val c = cos(radians)
        val s = sin(radians)
        val lateral = coordinate(d, across)
        val x = d.x * c - lateral * s
        val turned = d.x * s + lateral * c
        return (if (across == 1) Vec3(x, turned, d.z) else Vec3(x, d.y, turned)).normalized()
    }
}
