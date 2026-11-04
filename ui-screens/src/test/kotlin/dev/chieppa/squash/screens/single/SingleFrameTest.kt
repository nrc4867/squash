package dev.chieppa.squash.screens.single

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.sheetPartOf
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.frame.OverlayLine
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.Vec3 as GpuVec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH = 1000
private const val HEIGHT = 800
private const val SCALE = 300f

class SingleFrameTest {
    private val look = SheetLook(
        Color.White, Color.Black, Color.Gray, Color.DarkGray, Color.Yellow, Color.Red, Color.LightGray,
        PartMaterial(Color.Green, Color(0xFF008000), Color.Black), 1f, 2f, 1f, 2f, 2f, 2f, 1f, 2f, 2f, 3f, 3f,
    )
    private val leg = sheetPartOf(shipped.parts.first { it.id.value == "leg" })
    private val near = sheetPartOf(shipped.parts.first { it.id.value == "torso" }, origin = Vec3(0f, 0f, -1f), id = "near")
    private val far = sheetPartOf(shipped.parts.first { it.id.value == "tail" }, origin = Vec3(0f, 0f, 5f), id = "far")
    private val outline = OutlineCurve(listOf(CurvePoint(0f, 0f), CurvePoint(0f, 1f), CurvePoint(1f, 1f), CurvePoint(1f, 0f)))
    private val locked = LockedCamera(View.SIDE, leg.axes, SheetFit(leg.center, SCALE, 10f), WIDTH, HEIGHT)
    private val free = CameraView(eye = leg.center.toGpu() + GpuVec3(0f, 1f, -8f), target = leg.center.toGpu())

    private fun selection(outlines: Map<View, OutlineCurve> = emptyMap()) = SheetSelection(leg, outlines, listOf(far, near), leg.box.union(near.box).union(far.box))

    private fun frame(
        selection: SheetSelection? = selection(),
        locked: LockedCamera? = null,
        hints: SheetHints = SheetHints(),
        overlay: ViewportOverlay = ViewportOverlay.Empty,
    ): SceneFrame = singleFrame(SingleInput(WIDTH, HEIGHT, look, selection, locked, free, overlay, hints))

    @Test
    fun itFillsTheWholeViewWithOneCamera() {
        for (camera in listOf(locked, null)) {
            assertEquals(PixelRect(0, 0, WIDTH, HEIGHT), frame(locked = camera).views.single().viewport)
        }
    }

    @Test
    fun withNoPartItIsEmpty() {
        assertTrue(frame(selection = null).views.isEmpty())
    }

    @Test
    fun snappedTheSelectionFillsOnlyOnceItsViewIsDrawn() {
        val undrawn = frame(locked = locked).views.single().draws
        assertTrue(undrawn.none { it.mesh === leg.mesh })
        assertTrue(frame(locked = locked).overlay.lines.isNotEmpty(), "the template's outline takes its place")
        val drawn = frame(selection(mapOf(View.SIDE to outline)), locked).views.single().draws
        assertEquals(DepthLayer.FRONT, drawn.single { it.mesh === leg.mesh }.layer)
    }

    @Test
    fun theGhostShowsAtItsStrengthSnappedAndFree() {
        for (camera in listOf(locked, null)) {
            val draws = frame(locked = camera, hints = SheetHints(ghost = 0.25f)).views.single().draws
            assertEquals(0.25f, draws.single { it.mesh === far.mesh }.opacity, "behind $camera")
            assertEquals(0.25f, draws.single { it.mesh === near.mesh }.opacity)
            val none = frame(locked = camera, hints = SheetHints(ghost = 0f)).views.single().draws
            assertTrue(none.none { it.mesh === far.mesh || it.mesh === near.mesh }, "ghost off")
        }
    }

    @Test
    fun freeTheSelectionFillsOverTheGhostsFurthestFirst() {
        val draws = frame().views.single().draws
        val selected = assertNotNull(draws.firstOrNull { it.mesh === leg.mesh })
        assertEquals(1f, selected.opacity)
        assertEquals(DepthLayer.FRONT, selected.layer)
        assertTrue(draws.indexOfFirst { it.mesh === far.mesh } < draws.indexOfFirst { it.mesh === near.mesh }, "the eye sits on the near side")
    }

    @Test
    fun theToolsOverlayShowsOnlyWhileSnappedAndKeepsToTheView() {
        val line = OverlayLine(Offset(10f, 10f), Offset(WIDTH * 2f, 10f), Color.Blue, 1f)
        val overlay = ViewportOverlay(emptyList(), listOf(line), emptyList())
        val snapped = frame(locked = locked, overlay = overlay).overlay.lines.single { it.color == Color.Blue }
        assertEquals(WIDTH.toFloat(), snapped.to.x)
        assertTrue(frame(overlay = overlay).overlay.lines.none { it.color == Color.Blue })
    }

    @Test
    fun snappedNoPaneIsMarkedActive() {
        val frameLines = frame(selection(mapOf(View.SIDE to outline)), locked).overlay.lines
        assertTrue(frameLines.none { it.color == look.active }, "one view needs no accent frame")
    }
}
