package dev.chieppa.squash.screens.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.PartDrawerKind
import dev.chieppa.squash.screens.shell.partDrawerKind
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SmoothToolTest {
    private val work = TestWork()

    @AfterTest
    fun stopWork() = work.close()

    private fun tool(state: AppState) = SmoothTool(state, TEST_CURVE_LOOK)

    private fun along(camera: LockedCamera, from: CurvePoint, to: CurvePoint): List<Offset> = List(24) { i ->
        val t = i / 23f
        camera.toPixel(CurvePoint(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t))
    }

    private fun rippleOf(curve: OutlineCurve): Float = curve.toPath().segments.flatMap { it.sample(12) }
        .filter { it.x in 0.7f..1.3f && it.y > 0f }.maxOf { abs(it.y - 0.5f) }

    @Test
    fun aDragOverTheOutlineSmoothsItAsOneUndoStep() = runComposeUiTest {
        val bench = CurveToolBench(RIPPLED)
        val before = assertNotNull(bench.outline())
        val steps = bench.steps()
        showPane(tool(bench.state), bench.camera())
        mouseDrag(along(bench.camera(), CurvePoint(0.5f, 0.5f), CurvePoint(1.5f, 0.5f)))
        val after = assertNotNull(bench.outline())
        assertTrue(rippleOf(after) < rippleOf(before) * 0.75f, "ripple ${rippleOf(before)} to ${rippleOf(after)}")
        assertEquals(before.points.first(), after.points.first(), "the ends stay on the socket plane")
        assertEquals(before.points.last(), after.points.last())
        assertEquals(steps + 1, bench.steps())
        assertNotNull(bench.state.curves.refit)
        bench.session.history.undo()
        assertEquals(before, bench.outline())
    }

    @Test
    fun aDragFarFromTheOutlineLeavesNoStep() = runComposeUiTest {
        val bench = CurveToolBench(RIPPLED)
        val before = bench.outline()
        val steps = bench.steps()
        showPane(tool(bench.state), bench.camera())
        mouseDrag(along(bench.camera(), CurvePoint(0.5f, 1.8f), CurvePoint(1.5f, 1.8f)))
        assertEquals(before, bench.outline())
        assertEquals(steps, bench.steps())
    }

    @Test
    fun aDragOnAViewWithNoOutlineDoesNothing() = runComposeUiTest {
        val bench = CurveToolBench(null)
        val steps = bench.steps()
        showPane(tool(bench.state), bench.camera())
        mouseDrag(along(bench.camera(), CurvePoint(0.5f, 0.5f), CurvePoint(1.5f, 0.5f)))
        assertEquals(null, bench.outline())
        assertEquals(steps, bench.steps())
    }

    @Test
    fun noStrengthLeavesNoStep() = runComposeUiTest {
        val bench = CurveToolBench(RIPPLED)
        val before = bench.outline()
        val steps = bench.steps()
        bench.state.curves.brushStrength = 0f
        showPane(tool(bench.state), bench.camera())
        mouseDrag(along(bench.camera(), CurvePoint(0.5f, 0.5f), CurvePoint(1.5f, 0.5f)))
        assertEquals(before, bench.outline())
        assertEquals(steps, bench.steps())
    }

    @Test
    fun aSmallBrushBesideTheOutlineMissesIt() = runComposeUiTest {
        val bench = CurveToolBench(RIPPLED)
        val before = bench.outline()
        bench.state.curves.brushSize = MIN_BRUSH_SIZE
        showPane(tool(bench.state), bench.camera())
        mouseDrag(along(bench.camera(), CurvePoint(0.5f, 0.8f), CurvePoint(1.5f, 0.8f)))
        assertEquals(before, bench.outline())
    }

    @Test
    fun aLargeBrushBesideTheOutlineReachesIt() = runComposeUiTest {
        val bench = CurveToolBench(RIPPLED)
        val before = bench.outline()
        bench.state.curves.brushSize = MAX_BRUSH_SIZE
        showPane(tool(bench.state), bench.camera())
        mouseDrag(along(bench.camera(), CurvePoint(0.5f, 0.8f), CurvePoint(1.5f, 0.8f)))
        assertNotEquals(before, bench.outline())
    }

    @Test
    fun aBrushInPixelsReachesNoFurtherInThePlaneAtAZoom() = runComposeUiTest {
        val bench = CurveToolBench(RIPPLED)
        val before = assertNotNull(bench.outline())
        val peak = before.points[18]
        val zoomed = LockedCamera(View.SIDE, PartAxes.of(Vec3.X), SheetFit(Vec3(peak.x, peak.y, 0f), SCALE * 4f), PANE_WIDTH, PANE_HEIGHT)
        bench.state.curves.brushSize = 40f
        showPane(tool(bench.state), zoomed)
        mouseDrag(listOf(zoomed.toPixel(peak), zoomed.toPixel(CurvePoint(peak.x + 0.01f, peak.y))))
        val after = assertNotNull(bench.outline())
        assertNotEquals(before, after)
        val reach = 20f * zoomed.planePixel + 0.01f
        val old = before.toPath().segments.flatMap { it.sample(12) }
        for (point in after.toPath().segments.flatMap { it.sample(12) }.filter { hypot(it.x - peak.x, it.y - peak.y) > reach }) {
            assertTrue(old.minOf { hypot(it.x - point.x, it.y - point.y) } < 0.01f, "$point lies outside a 40 px brush at 400%")
        }
    }

    @Test
    fun withMirrorOffOnlyThisSideChanges() = runComposeUiTest {
        val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))
        val state = AppState(folder, work)
        val session = CharacterSession(
            SquashDocument(Character(CharacterId("c"), "Fennec", BodyPlanRef(BodyPlanId("p"), 1))),
            TemplateLibrary(folder.parts),
            work.scope,
            work.dispatcher,
        )
        val ref = folder.parts.first { it.id.value == "leg" }.ref
        val left = PartId("left")
        val right = PartId("right")
        session.send(
            AddParts(
                listOf(
                    PartInstance(left, ref, pair = PairId("legs"), side = Side.LEFT),
                    PartInstance(right, ref, pair = PairId("legs"), side = Side.RIGHT),
                ),
            ),
        )
        session.send(SetOutline(left, View.SIDE, RIPPLED))
        state.session.value = session
        state.selectedPart.value = right
        state.mirror.value = false
        val camera = LockedCamera(View.SIDE, PartAxes.of(Vec3.X), SheetFit(Vec3(1f, 0f, 0f), SCALE), PANE_WIDTH, PANE_HEIGHT)
        showPane(tool(state), camera)
        mouseDrag(along(camera, CurvePoint(0.5f, 0.5f), CurvePoint(1.5f, 0.5f)))
        val character = session.document.value.character
        assertEquals(RIPPLED, character.resolved(left)?.outlines?.get(View.SIDE), "the other side keeps the pair's")
        assertNotEquals(RIPPLED, character.resolved(right)?.outlines?.get(View.SIDE))
    }

    @Test
    fun theRingAndTheStretchShowUnderTheHoveringPointer() = runComposeUiTest {
        val bench = CurveToolBench(RIPPLED)
        val brushing = tool(bench.state)
        val camera = bench.camera()
        showPane(brushing, camera)
        assertTrue(brushing.overlay(View.SIDE, camera).isEmpty, "nothing until the pointer comes")
        val over = camera.toPixel(CurvePoint(1f, 0.5f))
        onNodeWithTag(PANE_TAG).performMouseInput { moveTo(over) }
        waitForIdle()
        val shown = brushing.overlay(View.SIDE, camera)
        val radius = bench.state.curves.brushSize / 2f
        val ring = shown.lines.filter { it.color == TEST_CURVE_LOOK.ring }
        assertTrue(ring.isNotEmpty())
        for (edge in ring) assertEquals(radius, (edge.from - over).getDistance(), 0.5f)
        val stretch = shown.lines.filter { it.color == TEST_CURVE_LOOK.affected }
        assertTrue(stretch.isNotEmpty(), "the stretch under the ring is marked")
        for (edge in stretch) assertTrue((edge.from - over).getDistance() <= radius + 1f)
        onNodeWithTag(PANE_TAG).performMouseInput { moveTo(camera.toPixel(CurvePoint(1f, 1.8f))) }
        waitForIdle()
        assertTrue(brushing.overlay(View.SIDE, camera).lines.none { it.color == TEST_CURVE_LOOK.affected }, "nothing under the ring")
        assertFalse(brushing.holdsFit)
    }

    @Test
    fun theDrawerFollowsTheTool() {
        assertEquals(PartDrawerKind.SMOOTH, partDrawerKind(BuildTool.SMOOTH, hasPart = true, drawn = true))
        assertEquals(PartDrawerKind.SMOOTH, partDrawerKind(BuildTool.SMOOTH, hasPart = true, drawn = false))
        assertEquals(PartDrawerKind.PART, partDrawerKind(BuildTool.SMOOTH, hasPart = false, drawn = false))
    }
}
