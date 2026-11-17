package dev.chieppa.squash.screens.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.screens.sheet.PaneViewport
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.input.key.Key
import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.anchors
import dev.chieppa.squash.document.isSmooth
import dev.chieppa.squash.document.sections
import dev.chieppa.squash.geom.DEFAULT_STROKE_FIT
import dev.chieppa.squash.geom.FAITHFUL_FIT
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.cleanStroke
import dev.chieppa.squash.geom.fitShareOfSpan
import dev.chieppa.squash.geom.longestSpan
import dev.chieppa.squash.screens.draw.LEG_STROKE
import dev.chieppa.stretch.test.setStretchContent
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How far from the line of symmetry, in screen pixels, a dragged anchor is let go: inside its reach. */
private const val NEAR_LINE = 3f

/** Two curves that trace the same shape lie this close when each is drawn as straight edges: a fifth of a pixel. */
private const val EXACT = 0.2f / SCALE

/** The bench's leg drawn as long as its pane shows the template's. */
private const val LEG_LENGTH = 2f

/** Edit curves, Smooth, Erase and Simplify on outlines drawn with few points. */
@OptIn(ExperimentalTestApi::class)
class FewPointEditTest {
    private val pixel = 1f / SCALE

    /** The bench's leg stroke fitted at [fit], as a release in its pane makes it. */
    private fun drawnLeg(fit: Float): OutlineCurve {
        val samples = LEG_STROKE.scaled(LEG_LENGTH).points().map { Vec2(it.x, it.y) }
        return assertNotNull(cleanStroke(samples, StrokeEnds.CutAtPlane(), pixel, StrokeOptions(fit = fit))).path.toOutline()
    }

    private fun worstGap(from: OutlineCurve, to: OutlineCurve): Float {
        val a = from.toPath().flatten(32)
        val b = to.toPath().flatten(32).let { flat -> flat.xs.indices.map { Vec2(flat.xs[it], flat.ys[it]) } }
        return a.xs.indices.maxOf { i: Int ->
            val p = Vec2(a.xs[i], a.ys[i])
            b.zipWithNext().minOf { (s, e): Pair<Vec2, Vec2> ->
                val run = e - s
                val t = (((p - s) dotWith run) / (run dotWith run)).coerceIn(0f, 1f)
                val off = p - (s + run * t)
                hypot(off.x, off.y)
            }
        }
    }

    private infix fun Vec2.dotWith(o: Vec2) = x * o.x + y * o.y

    @Test
    fun editCurvesShowsOnlyTheFewPointsAndAnAnchorDragBendsTheCurveSmoothly() = runComposeUiTest {
        val few = drawnLeg(DEFAULT_STROKE_FIT)
        val bench = CurveToolBench(few)
        val tool = EditCurvesTool(bench.state, TEST_CURVE_LOOK)
        val camera = bench.camera()
        showPane(tool, camera)
        val shown = tool.overlay(View.SIDE, camera)
        assertEquals(few.anchors.size, shown.rects.size, "one square a point: ${few.anchors.size}")
        assertEquals(2 * few.sections, shown.dots.size, "two handles a section")

        val middle = few.anchors[few.anchors.size / 2]
        val from = camera.toPixel(few.points[middle])
        val before = bench.steps()
        mouseDrag(List(12) { from + Offset(0f, -2f * it) })
        assertEquals(before + 1, bench.steps())
        val bent = assertNotNull(bench.outline())
        assertEquals(few.points.size, bent.points.size, "a drag adds no points")
        assertTrue(bent.isSmooth(middle), "the dragged point still turns smoothly")
        assertEquals(few.points[middle].y + 22f * pixel, bent.points[middle].y, 1e-3f)
    }

    @Test
    fun smoothAndEraseStillWorkOnAFewPointCurve() = runComposeUiTest {
        val few = drawnLeg(DEFAULT_STROKE_FIT)
        val bench = CurveToolBench(few)
        val smooth = SmoothTool(bench.state, TEST_CURVE_LOOK)
        bench.state.curves.brushSize = 120f
        bench.state.curves.brushStrength = 1f
        val camera = bench.camera()
        showPane(smooth, camera)
        val across = few.anchors.drop(1).dropLast(1).map { camera.toPixel(few.points[it]) }
        mouseDrag(across)
        val smoothed = assertNotNull(bench.outline())
        assertNotEquals(few, smoothed, "the brush changed the curve")
        assertTrue(smoothed.anchors.size <= few.anchors.size + 2, "the brush's refit keeps few points: ${few.anchors.size} to ${smoothed.anchors.size}")

    }

    @Test
    fun eraseStraightensOneSectionOfAFewPointCurve() = runComposeUiTest {
        val few = drawnLeg(DEFAULT_STROKE_FIT)
        val bench = CurveToolBench(few)
        val camera = bench.camera()
        showPane(EraseTool(bench.state, TEST_STROKE_LOOK, TEST_CURVE_LOOK), camera)
        val section = few.sections / 2
        val on = few.toPath().segments[section].pointAt(0.5f).let { camera.toPixel(CurvePoint(it.x, it.y)) }
        mouseDrag(listOf(on + Offset(0f, -8f), on, on + Offset(0f, 8f)))
        val after = assertNotNull(bench.outline())
        assertNotEquals(few, after)
        assertEquals(few.sections, after.sections, "the section becomes one straight section")
        assertEquals(few.points.first(), after.points.first())
        assertEquals(few.points.last(), after.points.last())
    }

    @Test
    fun simplifyRefitsThePickedOutlineThenTheWholePartAsOneStepEach() = runComposeUiTest {
        val dense = drawnLeg(FAITHFUL_FIT)
        val bench = CurveToolBench(dense)
        bench.session.send(SetOutline(bench.id, View.TOP, dense))
        bench.state.activeView.value = View.SIDE
        setStretchContent {
            val document by bench.session.document.collectAsState()
            val part = assertNotNull(document.character.part(bench.id))
            Column { OutlineCurvesSections(bench.state, bench.session, part, document.character.resolved(part).outlines) }
        }
        waitForIdle()
        val before = bench.steps()
        onNodeWithTag(CurveDrawerTags.SIMPLIFY_OUTLINE).performClick()
        waitForIdle()
        assertEquals(before + 1, bench.steps())
        val side = assertNotNull(bench.outline(View.SIDE))
        assertEquals(dense, bench.outline(View.TOP), "only the picked outline")
        assertTrue(side.anchors.size < dense.anchors.size, "${dense.anchors.size} points to ${side.anchors.size}")
        val allowed = fitShareOfSpan(DEFAULT_STROKE_FIT) * longestSpan(dense.toPath())
        val gap = worstGap(dense, side)
        println("few-points simplify: ${dense.anchors.size} anchors (${dense.points.size} stored) to ${side.anchors.size} (${side.points.size}), worst ${gap / allowed} of the tolerance")
        assertTrue(gap <= allowed * 1.05f, "the simpler outline lies within $allowed of the old, at worst $gap")

        onNodeWithTag(CurveDrawerTags.SIMPLIFY_OUTLINE).performClick()
        waitForIdle()
        assertEquals(before + 1, bench.steps(), "a second Simplify drops nothing")
        assertEquals(side, bench.outline(View.SIDE))
        assertEquals(dense.points.first(), side.points.first(), "the ends stay")
        assertEquals(dense.points.last(), side.points.last(), "the ends stay")

        onNodeWithTag(CurveDrawerTags.SIMPLIFY_PART).performClick()
        waitForIdle()
        assertEquals(before + 2, bench.steps())
        assertEquals(side, bench.outline(View.SIDE), "an outline already simplified stays as it is")
        assertTrue(assertNotNull(bench.outline(View.TOP)).anchors.size < dense.anchors.size)

        bench.session.history.undo()
        assertEquals(dense, bench.outline(View.TOP), "Simplify part undoes as one step")
        assertEquals(side, bench.outline(View.SIDE), "and the outline Simplify outline made comes back")
    }

    @Test
    fun altOnTheCurveAddsAPointThatKeepsItsShapeAndDeleteTakesOneOut() = runComposeUiTest {
        val bench = CurveToolBench(drawnLeg(DEFAULT_STROKE_FIT))
        val start = assertNotNull(bench.outline())
        val tool = EditCurvesTool(bench.state, TEST_CURVE_LOOK)
        val camera = bench.camera()
        setStretchContent {
            val document by bench.session.document.collectAsState()
            val part = assertNotNull(document.character.part(bench.id))
            Column {
                with(LocalDensity.current) {
                    Box(Modifier.size(camera.width.toDp(), camera.height.toDp())) {
                        PaneViewport(camera.axes.bodyViewOf(camera.view), null, tool.pointerInput(camera.view, camera).testTag(PANE_TAG))
                    }
                }
                OutlineCurvesSections(bench.state, bench.session, part, document.character.resolved(part).outlines)
            }
        }
        waitForIdle()
        val section = start.sections / 2
        val on = start.toPath().segments[section].pointAt(0.4f).let { camera.toPixel(CurvePoint(it.x, it.y)) }
        val steps = bench.steps()
        onNodeWithTag(PANE_TAG).performKeyInput {
            withKeyDown(Key.AltLeft) { onNodeWithTag(PANE_TAG).performMouseInput { click(on) } }
        }
        waitForIdle()
        assertEquals(steps + 1, bench.steps())
        val added = assertNotNull(bench.outline())
        assertEquals(start.points.size + 3, added.points.size, "one anchor and its two handles")
        assertTrue(worstGap(start, added) < EXACT && worstGap(added, start) < EXACT, "the shape stays exactly")
        val selected = assertNotNull(bench.state.curves.selection)
        assertEquals((section + 1) * 3, selected.index, "the new point is selected")

        onNodeWithTag(CurveDrawerTags.DELETE_POINT).performClick()
        waitForIdle()
        assertEquals(steps + 2, bench.steps())
        val deleted = assertNotNull(bench.outline())
        assertEquals(start.points.size, deleted.points.size)
        assertTrue(worstGap(start, deleted) < 5f * EXACT, "taking out a point that changed nothing gives back the shape")
    }

    @Test
    fun aDraggedAnchorCatchesTheLineOfSymmetryUnlessAltIsHeld() = runComposeUiTest {
        val square = edges(-0.5f to 0.5f, 0.5f to 0.5f, 0.5f to -0.5f, -0.5f to -0.5f, -0.5f to 0.5f)
        val bench = CurveToolBench(side = null)
        bench.session.send(SetOutline(bench.id, View.FRONT, square))
        bench.state.activeView.value = View.FRONT
        val tool = EditCurvesTool(bench.state, TEST_CURVE_LOOK)
        val camera = bench.camera(View.FRONT)
        showPane(tool, camera)
        assertTrue(tool.overlay(View.FRONT, camera).lines.any { it.color == TEST_CURVE_LOOK.symmetry }, "the line of symmetry shows")
        val anchor = 3
        val from = camera.toPixel(square.points[anchor])
        val near = camera.toPixel(CurvePoint(NEAR_LINE * pixel, 0.5f))
        mouseDrag(List(10) { from + (near - from) * (it / 9f) })
        assertEquals(0f, assertNotNull(bench.outline(View.FRONT)).points[anchor].x, "the anchor lands on the line")

        bench.session.history.undo()
        onNodeWithTag(PANE_TAG).performKeyInput {
            withKeyDown(Key.AltLeft) { onNodeWithTag(PANE_TAG).performMouseInput {
                moveTo(from)
                press()
                List(10) { from + (near - from) * (it / 9f) }.forEach { moveTo(it) }
                release()
            } }
        }
        waitForIdle()
        val free = assertNotNull(bench.outline(View.FRONT)).points[anchor].x
        assertEquals(NEAR_LINE * pixel, free, pixel / 2f, "Alt drags it free of the line")
    }
}
