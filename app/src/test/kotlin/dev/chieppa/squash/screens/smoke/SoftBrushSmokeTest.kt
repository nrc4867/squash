package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.BIN_THREE_QUARTER
import dev.chieppa.squash.screens.fennec.CREAM
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.EAR_R
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.stretch.gpu.GpuOptions
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val WHOLE_MARGIN = 0.08f
private const val CLOSE_MARGIN = 0.06f
private const val ZOOM_DISTANCE = 0.12f
private const val ZOOM_NEAR = 0.002f
private const val ZOOM_SIZE = 900

/** The most a channel may move between neighbouring pixels of a fade under close zoom, where it spreads over hundreds of pixels. */
private const val MOST_STEP_IN_A_CLOSE_FADE = 2

/** A close view of one place on a part; [smooth] when nothing but a fade lies in it. */
private class Zoom(val name: String, val part: PartId, val u: Float, val v: Float, val smooth: Boolean)

private val BLUSH = SlotName("blush")
private val DUSK = SlotName("dusk")

private fun stroke(part: PartId, id: String, slot: SlotName, radius: Float, softness: Float, vararg uv: Pair<Float, Float>): DocumentCommand {
    val strokeId = BrushStrokeId(id)
    return SetBrushStroke(part, strokeId, BrushStroke(strokeId, uv.map { (u, v) -> Anchor(part, u = u, v = v) }, slot, radius, softness))
}

/** A cheek blush each side of the head, a fur to cream blend along the belly, a dusk saddle over the back's seam with a hard stripe across it, and fur tips that fade at the tail's and ears' poles. */
private fun softPaint(): List<DocumentCommand> = listOf(
    SetPaletteSlot(BLUSH, SlotColor(0.90f, 0.36f, 0.34f)),
    SetPaletteSlot(DUSK, SlotColor(0.30f, 0.18f, 0.14f)),
    stroke(HEAD, "blush-near", BLUSH, 0.16f, 1f, 0.46f to 0.20f),
    stroke(HEAD, "blush-far", BLUSH, 0.16f, 1f, 0.46f to 0.80f),
    stroke(TORSO, "belly", CREAM, 0.24f, 0.8f, 0.12f to 0.50f, 0.40f to 0.50f, 0.70f to 0.50f),
    stroke(TORSO, "saddle", DUSK, 0.16f, 0.9f, 0.25f to 0.0f, 0.45f to 0.0f, 0.60f to 0.0f),
    stroke(TORSO, "stripe", CREAM, 0.012f, 0f, 0.40f to 0.90f, 0.40f to 0.0f, 0.40f to 0.10f),
    stroke(TAIL, "tip", DUSK, 0.30f, 1f, 0.98f to 0.0f),
    stroke(EAR_L, "tip", DUSK, 0.30f, 1f, 0.97f to 0.0f),
)

/** A soft brush on the fennec through commands, the real graph and scene, left for a person to judge, and its fade measured in pixels. */
@Tag("slow")
class SoftBrushSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val work = TestWork()

    private fun session(): CharacterSession {
        val folder = TemplateFolder.shipped()
        val session = CharacterSession(savedFennec(), TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)
        softPaint().forEach(session::send)
        work.settle(session)
        return session
    }

    private fun keep(name: String, frame: SceneFrame, width: Int = WIDTH, height: Int = HEIGHT): IntArray {
        val image = renderSnapshot(frame, width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertNotBlank(image.pixels, name)
        return image.pixels
    }

    @Test
    fun theFennecCarriesSoftStrokesFlatAndTwoTone() {
        val session = session()
        val rect = PixelRect(0, 0, WIDTH, HEIGHT)
        val parts = assertNotNull(session.assembly.output.value).parts.filter { it.id != null }
        val head = parts.filter { it.id == HEAD || it.id == EAR_L || it.id == EAR_R }
        for ((viewName, toViewer) in listOf("three-quarter" to probeAzimuth(45f), "side" to probeAzimuth(90f), "belly" to Vec3(0.25f, -0.55f, 1f), "back" to Vec3(0.15f, 1f, 0.4f))) {
            val whole = framedCamera(parts, toViewer, WIDTH, HEIGHT, WHOLE_MARGIN)
            val close = framedCamera(head, toViewer, WIDTH, HEIGHT, CLOSE_MARGIN)
            session.showColorAt(close.pixelsPerUnit)
            work.settle(session)
            val state = session.probeState(work)
            for ((modeName, mode) in listOf("flat" to Shading.FLAT, "two-tone" to Shading.TWO_TONE)) {
                val view = characterView(rect, whole.camera, state, PROBE_LOOK, ObjectShading(mode), whole.pixelsPerUnit) { BIN_THREE_QUARTER }
                keep("soft-$modeName-$viewName", SceneFrame(PROBE_GROUND, listOf(view)))
            }
            val view = characterView(rect, close.camera, state, PROBE_LOOK, ObjectShading(Shading.FLAT), close.pixelsPerUnit) { BIN_THREE_QUARTER }
            keep("soft-head-flat-$viewName", SceneFrame(PROBE_GROUND, listOf(view)))
        }
        work.close()
        assertEquals(emptyList(), errors)
    }

    @Test
    fun aCloseZoomOfAFadeHasNoStepsNoLineAndNoSpecks() {
        val session = session()
        val assembled = assertNotNull(session.assembly.output.value)
        session.showColorAt(pixelsPerUnit(ZOOM_DISTANCE))
        work.settle(session)
        val rect = PixelRect(0, 0, ZOOM_SIZE, ZOOM_SIZE)
        val zooms = listOf(
            Zoom("belly", TORSO, 0.4f, 0.34f, smooth = true),
            Zoom("belly-edge", TORSO, 0.4f, 0.24f, smooth = true),
            Zoom("saddle", TORSO, 0.52f, 0.09f, smooth = true),
            Zoom("stripe", TORSO, 0.40f, 0.05f, smooth = false),
            Zoom("blush", HEAD, 0.46f, 0.20f, smooth = true),
            Zoom("seam", TORSO, 0.52f, 0.0f, smooth = true),
            Zoom("tail-tip", TAIL, 0.93f, 0.25f, smooth = false),
        )
        for (zoom in zooms) {
            val part = assertNotNull(assembled.part(zoom.part))
            for ((modeName, mode) in listOf("flat" to Shading.FLAT, "two-tone" to Shading.TWO_TONE)) {
                val camera = cameraOn(part, zoom.u, zoom.v, ZOOM_DISTANCE, near = ZOOM_NEAR, lift = 0f)
                val draw = seamsDraw(session, part, ObjectShading(mode), camera.toCameraFrom(part.origin))
                val pixels = keep("soft-zoom-${zoom.name}-$modeName", SceneFrame(PROBE_GROUND, listOf(seamsView(rect, camera, listOf(draw)))), ZOOM_SIZE, ZOOM_SIZE)
                if (mode == Shading.FLAT && zoom.smooth) assertFadeIsSmooth(pixels, ZOOM_SIZE, zoom.name)
            }
        }
        work.close()
        assertEquals(emptyList(), errors)
    }

    /** No channel jumps between neighbours, along rows and columns, and the image spreads over many values: a stepped or hard-edged fade would fail. */
    private fun assertFadeIsSmooth(pixels: IntArray, width: Int, name: String) {
        var worst = 0
        val height = pixels.size / width
        for (y in 0 until height) {
            for (x in 1 until width) worst = maxOf(worst, channelStep(pixels[y * width + x], pixels[y * width + x - 1]))
        }
        for (x in 0 until width) {
            for (y in 1 until height) worst = maxOf(worst, channelStep(pixels[y * width + x], pixels[(y - 1) * width + x]))
        }
        val values = pixels.asIterable().distinct().size
        println("FADE $name: worst step $worst across $values colors")
        assertTrue(worst <= MOST_STEP_IN_A_CLOSE_FADE, "$name steps by $worst between neighbours")
        assertTrue(values >= MOST_STEP_IN_A_CLOSE_FADE * 4, "$name holds only $values colors, so the view missed its fade")
    }

    private fun channelStep(a: Int, b: Int) = (0..2).maxOf { abs((a shr (it * 8) and 0xFF) - (b shr (it * 8) and 0xFF)) }
}
