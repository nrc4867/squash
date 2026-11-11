package dev.chieppa.squash.screens.look.compare

import java.awt.image.BufferedImage
import kotlin.math.abs

/** How far a channel may stray from the flat background and still be background. */
private const val KEY_TOLERANCE = 10

/** Which pixels of a [width] by [height] image the character covers, row by row. */
class Silhouette(val width: Int, val height: Int, private val covered: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = covered[y * width + x]

    companion object {
        /** The pixels of [image] that are not its flat background, read as the colour of its top-left corner. */
        fun keyed(image: BufferedImage): Silhouette {
            val ground = image.getRGB(0, 0)
            val covered = BooleanArray(image.width * image.height) { at -> !near(image.getRGB(at % image.width, at / image.width), ground) }
            return Silhouette(image.width, image.height, covered)
        }

        private fun near(a: Int, b: Int) = (0..2).all { abs((a shr (it * 8) and 0xFF) - (b shr (it * 8) and 0xFF)) <= KEY_TOLERANCE }
    }
}

/** How two silhouettes of one size agree inside [inside]: the pixels both cover over the pixels either covers, or null where neither covers any. */
fun intersectionOverUnion(reference: Silhouette, squash: Silhouette, inside: (Int, Int) -> Boolean = { _, _ -> true }): Float? {
    var both = 0
    var either = 0
    for (y in 0 until reference.height) for (x in 0 until reference.width) {
        if (!inside(x, y)) continue
        val a = reference[x, y]
        val b = squash[x, y]
        if (a && b) both++
        if (a || b) either++
    }
    return if (either == 0) null else both.toFloat() / either
}
