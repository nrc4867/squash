package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.stretch.gpu.GpuImage
import kotlin.math.abs
import kotlin.test.assertTrue

/** How far a channel may be from the color a pixel is matched against. */
private const val CHANNEL_TOLERANCE = 2

/** The least share of an image that must differ from its commonest color, and the fewest colors it must have. */
private const val MIN_DRAWN_SHARE = 0.005f
private const val MIN_COLORS = 4

/** Fails unless the image holds a real drawing rather than one flat color. */
fun assertNotBlank(pixels: IntArray, name: String) {
    val counts = pixels.asIterable().groupingBy { it }.eachCount()
    val drawn = 1f - counts.values.max().toFloat() / pixels.size
    assertTrue(counts.size >= MIN_COLORS && drawn >= MIN_DRAWN_SHARE, "$name is blank: ${counts.size} colors, $drawn drawn")
}

fun ImageBitmap.argbPixels(): IntArray {
    val map = toPixelMap()
    return IntArray(width * height) { map[it % width, it / width].toArgb() }
}

/** How many of [rect]'s pixels are [color]. */
fun GpuImage.countIn(rect: PixelRect, color: Color): Int {
    val wanted = color.toArgb()
    return (rect.y until rect.bottom).sumOf { y -> (rect.x until rect.right).count { x -> close(this[x, y], wanted) } }
}

private fun close(a: Int, b: Int) = (0..2).all { abs((a shr (it * 8) and 0xFF) - (b shr (it * 8) and 0xFF)) <= CHANNEL_TOLERANCE }
