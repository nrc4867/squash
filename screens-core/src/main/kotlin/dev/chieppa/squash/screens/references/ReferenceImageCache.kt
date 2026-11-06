package dev.chieppa.squash.screens.references

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.chieppa.squash.document.ImageBytes
import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.ReferenceImage
import dev.chieppa.squash.render.ImagePixels
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import kotlin.math.max
import kotlin.math.roundToInt

/** The longest side, in pixels, a trace image keeps for the panes; a larger one is scaled down to it. */
private const val MAX_TRACE_PIXELS = 1024

private const val BYTE_MASK = 0xFF
private const val GREEN_SHIFT = 8
private const val BLUE_SHIFT = 16
private const val ALPHA_SHIFT = 24

/** A reference image decoded: as Compose draws it, its full size, and as the panes draw a trace of it. */
class DecodedImage(val bitmap: ImageBitmap, val width: Int, val height: Int, val trace: ImagePixels, private val full: Bitmap) {
    /** The color at [u] across and [v] down, each 0 to 1. */
    fun colorAt(u: Float, v: Float): Color {
        val x = (u * width).toInt().coerceIn(0, width - 1)
        val y = (v * height).toInt().coerceIn(0, height - 1)
        return Color(full.getColor(x, y))
    }
}

/** [bytes] decoded, or null when they are no image this platform reads. */
fun decodeImage(bytes: ByteArray): DecodedImage? {
    val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    val full = Bitmap().apply { allocPixels(ImageInfo(image.width, image.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)) }
    if (!image.readPixels(full, 0, 0)) return null
    val scale = minOf(1f, MAX_TRACE_PIXELS.toFloat() / max(image.width, image.height))
    val traceWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
    val traceHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
    val small = Bitmap().apply { allocPixels(ImageInfo(traceWidth, traceHeight, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)) }
    image.scalePixels(small.peekPixels()!!, FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), false)
    val bytesOut = requireNotNull(small.readPixels()) { "No pixels in a decoded image" }
    val rgba = IntArray(traceWidth * traceHeight) { i ->
        val at = i * 4
        (bytesOut[at].toInt() and BYTE_MASK) or
            ((bytesOut[at + 1].toInt() and BYTE_MASK) shl GREEN_SHIFT) or
            ((bytesOut[at + 2].toInt() and BYTE_MASK) shl BLUE_SHIFT) or
            ((bytesOut[at + 3].toInt() and BYTE_MASK) shl ALPHA_SHIFT)
    }
    return DecodedImage(image.toComposeImageBitmap(), image.width, image.height, ImagePixels(traceWidth, traceHeight, rgba), full)
}

/** Reference images decoded once each and kept while their bytes stay the same. */
class ReferenceImageCache {
    private val held = HashMap<ImageId, Pair<ImageBytes, DecodedImage?>>()

    fun of(image: ReferenceImage): DecodedImage? {
        val kept = held[image.id]
        if (kept != null && kept.first === image.data) return kept.second
        return decodeImage(image.data.bytes).also { held[image.id] = image.data to it }
    }
}
