package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.stretch.gpu.GpuImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertTrue

/** Where each run leaves what it rendered. */
private const val IMAGES = "build/smoke-images"

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

/** Leaves an image in the smoke images, for a person to look at. */
internal fun keepImage(pixels: IntArray, width: Int, name: String) {
    val folder = File(IMAGES).apply { mkdirs() }
    val height = pixels.size / width
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    out.setRGB(0, 0, width, height, pixels, 0, width)
    ImageIO.write(out, "png", File(folder, "$name.png"))
}

fun ImageBitmap.argbPixels(): IntArray {
    val map = toPixelMap()
    return IntArray(width * height) { map[it % width, it / width].toArgb() }
}

/** This color at [opacity] over [below], as a blend in the surface's colors does it. */
fun Color.over(below: Color, opacity: Float) = Color(
    red * opacity + below.red * (1f - opacity),
    green * opacity + below.green * (1f - opacity),
    blue * opacity + below.blue * (1f - opacity),
)

/** The pixels of [rect] that are [color], in the pane's own coordinates. */
fun GpuImage.pointsIn(rect: PixelRect, color: Color): List<Offset> {
    val wanted = color.toArgb()
    return buildList {
        for (y in rect.y until rect.bottom) for (x in rect.x until rect.right) if (close(this@pointsIn[x, y], wanted)) add(Offset((x - rect.x).toFloat(), (y - rect.y).toFloat()))
    }
}

/** How many of [rect]'s pixels are [color]. */
fun GpuImage.countIn(rect: PixelRect, color: Color): Int {
    val wanted = color.toArgb()
    return (rect.y until rect.bottom).sumOf { y -> (rect.x until rect.right).count { x -> close(this[x, y], wanted) } }
}

private fun close(a: Int, b: Int) = (0..2).all { abs((a shr (it * 8) and 0xFF) - (b shr (it * 8) and 0xFF)) <= CHANNEL_TOLERANCE }

/** How many of these pixels are [color]. */
fun IntArray.countOf(color: Color): Int {
    val wanted = color.toArgb()
    return count { close(it, wanted) }
}

/** [piece], [pieceWidth] pixels wide, drawn over this image, [width] pixels wide, with its corner at ([x], [y]). */
fun IntArray.paste(width: Int, piece: IntArray, pieceWidth: Int, x: Int, y: Int) {
    for (at in piece.indices) {
        val left = x + at % pieceWidth
        val top = y + at / pieceWidth
        val into = top * width + left
        if (left in 0 until width && into in indices) this[into] = piece[at]
    }
}
