package dev.chieppa.squash.screens.references

import dev.chieppa.squash.document.BoardPlace
import dev.chieppa.squash.document.ImageBytes
import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.ReferenceImage
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CLOSE = 1e-4f

class TracePlacementTest {
    private val image = ReferenceImage(ImageId("sheet"), "Side sheet", ImageBytes(byteArrayOf()), 200, 100, BoardPlace(0f, 0f, 100f))
    private val box = BodyBox(Vec3(-1f, 0f, -0.25f), Vec3(1f, 1f, 0.25f))

    private fun assertClose(expected: Float, actual: Float) = assertTrue(kotlin.math.abs(expected - actual) < CLOSE, "$expected, was $actual")

    @Test
    fun aFittedTraceStandsAsTallAsTheCharacterOnItsLowestPoint() {
        val trace = fittedTrace(image, View.SIDE, box)
        assertClose(1f / 100f, trace.units)
        val corners = traceCorners(trace, View.SIDE, image.width, image.height).map { planePoint(it, View.SIDE) }
        assertClose(0f, corners.minOf { it.y })
        assertClose(1f, corners.maxOf { it.y })
        assertClose(2f, corners.maxOf { it.x } - corners.minOf { it.x })
    }

    @Test
    fun cornersRunTopLeftToBottomLeftAndFlipMirrorsThem() {
        val trace = fittedTrace(image, View.FRONT, box)
        val plain = traceCorners(trace, View.FRONT, image.width, image.height).map { planePoint(it, View.FRONT) }
        assertTrue(plain[0].x < plain[1].x && plain[0].y > plain[3].y)
        val flipped = traceCorners(trace.copy(flip = true), View.FRONT, image.width, image.height).map { planePoint(it, View.FRONT) }
        assertTrue(flipped[0].x > flipped[1].x)
    }

    @Test
    fun aQuarterTurnStandsAWideImageUpright() {
        val trace = fittedTrace(image, View.SIDE, box).copy(rotation = 90f)
        val corners = traceCorners(trace, View.SIDE, image.width, image.height).map { planePoint(it, View.SIDE) }
        assertClose(2f, corners.maxOf { it.y } - corners.minOf { it.y })
    }

    @Test
    fun aCameraSeesTheViewItLooksAlongMostSquarely() {
        assertEquals(View.SIDE, viewFacing(BodyView.SIDE.toViewer))
        assertEquals(View.SIDE, viewFacing(BodyView.SIDE.toViewer * -1f + Vec3(0.2f, 0.1f, 0f)))
        assertEquals(View.TOP, viewFacing(Vec3(0.1f, 1f, 0.2f)))
    }

    @Test
    fun anImageDecodesAndItsColorsRead() {
        val surface = Surface.makeRasterN32Premul(8, 4)
        surface.canvas.clear(Color.WHITE)
        surface.canvas.drawRect(Rect(0f, 0f, 4f, 4f), Paint().apply { color = Color.RED })
        val png = assertNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes
        val decoded = assertNotNull(decodeImage(png))
        assertEquals(8, decoded.width)
        assertEquals(4, decoded.height)
        assertEquals(1f, decoded.colorAt(0.1f, 0.5f).red)
        assertEquals(0f, decoded.colorAt(0.1f, 0.5f).green)
        assertEquals(1f, decoded.colorAt(0.9f, 0.5f).green)
        assertEquals(8 * 4, decoded.trace.rgba.size)
    }
}
