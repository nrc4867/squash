package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.gpu.CameraView
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue
import dev.chieppa.stretch.gpu.Vec3 as GpuVec3

/** One display frame at 120 Hz. */
private const val FRAME_NANOS = 8_333_333L
private const val WARMUP_ROUNDS = 3_000
private const val MEASURED_ROUNDS = 3_000
private const val NANOS_PER_MICRO = 1_000.0

/** Four seconds of a pen reporting at 240 Hz. */
private const val STROKE_SAMPLES = 960
private const val STORED_SEGMENTS = 24

/** Puts a number on the highlight at stroke rate, on a stroke longer than most drawn in one go. */
class DisagreeCostTest {
    private fun loop(radius: Float, count: Int) = List(count) {
        val angle = 2.0 * PI * it / (count - 1)
        CurvePoint(1f + radius * cos(angle).toFloat(), radius * 0.6f * sin(angle).toFloat())
    }

    private fun stored(radius: Float): OutlineCurve {
        return OutlineCurve(loop(radius, 3 * STORED_SEGMENTS + 1))
    }

    private inline fun nanosOfOneFrame(block: () -> Unit): LongArray {
        repeat(WARMUP_ROUNDS) { block() }
        return LongArray(MEASURED_ROUNDS) {
            val start = System.nanoTime()
            block()
            System.nanoTime() - start
        }
    }

    private fun report(name: String, nanos: LongArray) {
        nanos.sort()
        val median = nanos[nanos.size / 2] / NANOS_PER_MICRO
        val tail = nanos[nanos.size * 99 / 100] / NANOS_PER_MICRO
        println("$name: median $median us, p99 $tail us, over $STROKE_SAMPLES samples")
        assertTrue(nanos[nanos.size / 2] < FRAME_NANOS, "$name takes more than a frame")
    }

    @Test
    fun theHighlightFitsInsideAFrame() {
        val outlines = mapOf(View.TOP to stored(0.8f), View.FRONT to stored(0.9f), View.SIDE to stored(0.7f))
        val live = LiveStroke(View.TOP, loop(1f, STROKE_SAMPLES))
        val spans = nanosOfOneFrame { viewsDisagree(outlines, live, 0.01f) }
        report("viewsDisagree", spans)

        val part = legPart()
        val rects = sheetPaneRects(1400, 800, 2)
        val fit = fitSheet(part.axes, part.extents, rects)
        val cameras = SheetPane.entries.mapNotNull { pane ->
            pane.view?.let { it to LockedCamera(it, part.axes, fit, rects[pane.ordinal].width, rects[pane.ordinal].height) }
        }.toMap()
        val look = OutlineLook(Color.Gray, 4f, Color.Red, 4f, 8f)
        val tool = object : PaneTool {
            override fun pointerInput(view: View, camera: LockedCamera) = Modifier
            override val liveStroke get() = live
            override fun overlay(view: View, camera: LockedCamera) =
                if (view == live.view) buildOverlay { polyline(live.points, camera, Color.Black, 7f) } else ViewportOverlay.Empty
        }
        val selection = SheetSelection(part, outlines)
        report("paneOutlineOverlays", nanosOfOneFrame { paneOutlineOverlays(selection, cameras, tool, look) })

        val sheetLook = SheetLook(
            Color.White, Color.Black, Color.Gray, Color.DarkGray, Color.Yellow, Color.Red, Color.LightGray,
            PartMaterial(Color.Green), 2f, 8f, 3f, 10f, 8f, 2f, 3f, 8f, 6f, 4f, 3f,
        )
        val full = CameraView(eye = part.center.toGpu() + GpuVec3(3f, 2f, -4f), target = part.center.toGpu())
        report(
            "sheetFrame",
            nanosOfOneFrame {
                val overlays = paneOutlineOverlays(selection, cameras, tool, look)
                sheetFrame(SheetInput(rects, 1400, 800, sheetLook, selection, cameras, full, View.TOP, overlays))
            },
        )
    }

    private fun legPart(): SheetPart {
        val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))
        val leg = folder.parts.first { it.id.value == "leg" }
        return checkNotNull(sheetPart(PartId("p"), leg, PartBuilder.mesh("p", leg, PartBuilder.build(leg))))
    }
}
