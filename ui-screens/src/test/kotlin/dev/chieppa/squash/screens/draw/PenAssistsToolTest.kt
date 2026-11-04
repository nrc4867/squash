package dev.chieppa.squash.screens.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.closed
import dev.chieppa.squash.screens.edit.CurveToolBench
import dev.chieppa.squash.screens.edit.LEG_SIDE
import dev.chieppa.squash.screens.edit.PANE_TAG
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.TEST_STROKE_LOOK
import dev.chieppa.squash.screens.edit.edges
import dev.chieppa.squash.screens.edit.mouseDrag
import dev.chieppa.squash.screens.edit.showPane
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneLine
import dev.chieppa.squash.screens.sheet.outlineSamples
import dev.chieppa.stretch.frame.ViewportOverlay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val NEAR_PIXELS = 3f

@OptIn(ExperimentalTestApi::class)
class PenAssistsToolTest {
    private val look = TEST_STROKE_LOOK

    private fun tool(bench: CurveToolBench) = DrawOutlineTool(bench.state, look, TEST_CURVE_LOOK)

    private fun line(camera: LockedCamera, vararg points: Pair<Float, Float>, step: Float = 0.02f): List<Offset> =
        points.toList().zipWithNext().flatMap { (a, b) ->
            val count = (kotlin.math.hypot(b.first - a.first, b.second - a.second) / step).toInt().coerceAtLeast(1)
            List(count) { i ->
                val t = i.toFloat() / count
                camera.toPixel(CurvePoint(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t))
            }
        } + camera.toPixel(CurvePoint(points.last().first, points.last().second))

    private fun ComposeUiTest.pressThrough(path: List<Offset>) {
        onNodeWithTag(PANE_TAG).performMouseInput {
            moveTo(path.first())
            press()
            path.drop(1).forEach { moveTo(it) }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.letGo() {
        onNodeWithTag(PANE_TAG).performMouseInput { release() }
        waitForIdle()
    }

    private fun ViewportOverlay.of(color: androidx.compose.ui.graphics.Color) = lines.filter { it.color == color }

    private fun distance(p: Offset, line: PaneLine): Float {
        val run = line.end - line.start
        val t = (((p.x - line.start.x) * run.x + (p.y - line.start.y) * run.y) / (run.x * run.x + run.y * run.y)).coerceIn(0f, 1f)
        return (p - (line.start + run * t)).getDistance()
    }

    private fun assertSymmetric(outline: OutlineCurve, across: (CurvePoint) -> CurvePoint) {
        val points = outline.points
        for (i in points.indices) assertEquals(across(points[i]), points[points.lastIndex - i], "point $i")
    }

    @Test
    fun halfOutlineMakesOneSymmetricOutlineAndOneUndoStep() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        bench.state.curves.halfOutline = true
        bench.state.curves.snapGuides = false
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val before = bench.steps()
        val path = List(60) {
            val angle = PI / 2.0 * it / 59
            camera.toPixel(CurvePoint(1f * sin(angle).toFloat(), 0.8f * cos(angle).toFloat()))
        }
        pressThrough(path)
        val live = tool.overlay(View.SIDE, camera)
        val low = camera.toPixel(CurvePoint(0.5f, -0.6f))
        assertTrue(live.of(look.connector).any { it.from.y > low.y - 40f }, "the image shows live below the line")
        letGo()
        assertEquals(before + 1, bench.steps())
        assertSymmetric(assertNotNull(bench.outline(View.SIDE))) { CurvePoint(it.x, 0f - it.y) }
    }

    @Test
    fun halfOutlineInFrontMirrorsAcrossTheMiddleAndCloses() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        bench.state.curves.halfOutline = true
        bench.state.curves.snapGuides = false
        val tool = tool(bench)
        val camera = bench.camera(View.FRONT)
        showPane(tool, camera)
        val path = List(60) {
            val angle = PI * it / 59
            camera.toPixel(CurvePoint(0.7f * sin(angle).toFloat(), 0.7f * cos(angle).toFloat()))
        }
        mouseDrag(path)
        val outline = assertNotNull(bench.outline(View.FRONT))
        assertTrue(outline.closed)
        assertSymmetric(outline) { CurvePoint(0f - it.x, it.y) }
    }

    @Test
    fun halfOutlineRedrawsASpanOnBothSidesInOneStep() = runComposeUiTest {
        val bench = CurveToolBench(side = LEG_SIDE)
        bench.state.curves.halfOutline = true
        bench.state.curves.snapGuides = false
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val before = bench.steps()
        val path = List(40) {
            val t = it / 39f
            camera.toPixel(CurvePoint(0.2f + 0.9f * t, 0.5f + 0.12f * sin(PI.toFloat() * t)))
        }
        mouseDrag(path)
        assertEquals(before + 1, bench.steps())
        val flat = outlineSamples(assertNotNull(bench.outline(View.SIDE)))
        assertTrue(flat.maxOf { it.y } > 0.58f && flat.minOf { it.y } < -0.58f, "${flat.maxOf { it.y }} and ${flat.minOf { it.y }}")
    }

    @Test
    fun aStrokesWidestPointCatchesTheOtherViewsGuideVisiblyAndReleasesOnIt() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        bench.state.curves.stabilizer = 0f
        val front = edges(-0.6f to 0.5f, 0.6f to 0.5f, 0.6f to -0.5f, -0.6f to -0.5f, -0.6f to 0.5f)
        bench.session.send(SetOutline(bench.id, View.FRONT, front))
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val reach = 0.5f + 5f * camera.planePixel
        val path = List(61) {
            val t = it / 60f
            camera.toPixel(CurvePoint(0.1f + 1.4f * t, reach * sin(PI.toFloat() * t) * 0.999f))
        }
        pressThrough(path)
        val live = tool.overlay(View.SIDE, camera)
        assertTrue(live.of(look.caught).isNotEmpty(), "the guide lights")
        letGo()
        val flat = outlineSamples(assertNotNull(bench.outline(View.SIDE)))
        assertEquals(0.5f, flat.maxOf { it.y }, 1.5f * camera.planePixel, "released on the guide")
    }

    @Test
    fun snapToGuidesOffCatchesNothing() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        bench.state.curves.snapGuides = false
        bench.state.curves.stabilizer = 0f
        bench.session.send(SetOutline(bench.id, View.FRONT, edges(-0.6f to 0.5f, 0.6f to 0.5f, 0.6f to -0.5f, -0.6f to -0.5f, -0.6f to 0.5f)))
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val height = 0.5f + 5f * camera.planePixel
        val path = List(61) {
            val t = it / 60f
            camera.toPixel(CurvePoint(0.1f + 1.4f * t, height * sin(PI.toFloat() * t)))
        }
        pressThrough(path)
        assertTrue(tool.overlay(View.SIDE, camera).of(look.caught).isEmpty())
        letGo()
        assertTrue(outlineSamples(assertNotNull(bench.outline(View.SIDE))).maxOf { it.y } > 0.5f + 3f * camera.planePixel)
    }

    @Test
    fun aStrokeInATiltedPaneCatchesItsGuideToo() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) { settled() }
        val id = StrokeParts.tail.id
        bench.state.selectedPart.value = id
        bench.state.curves.stabilizer = 0f
        bench.session.send(SetOutline(id, View.TOP, edges(0f to 0.1f, 0.4f to 0.1f, 0.4f to -0.1f, 0f to -0.1f)))
        with(bench) { settled() }
        val part = bench.part(id)
        val camera = bench.camera(part, View.SIDE)
        val guides = strokeSnapLines(camera, mapOf(View.TOP to assertNotNull(bench.state.editedOutline(View.TOP))), null)
        val end = guides.minBy { distance(camera.toPixel(CurvePoint(0.4f, 0f)), it) }
        assertTrue(abs(end.end.x - end.start.x) > 1f && abs(end.end.y - end.start.y) > 1f, "the pane is tilted, and its guide with it: $end")
        val past = 5f * camera.planePixel
        val path = List(81) {
            val t = it / 80f
            camera.toPixel(CurvePoint((0.4f + past) * sin(PI.toFloat() * t), 0.08f * cos(PI.toFloat() * t)))
        }
        with(bench) { show(camera) }
        with(bench) { stroke(path) }
        val tip = camera.toPixel(outlineSamples(bench.outline(id, View.SIDE)).maxBy { it.x })
        assertTrue(distance(tip, end) <= 2f, "the far end lands on the tilted guide: ${distance(tip, end)}")
    }

    @Test
    fun theBuildsReadingShowsOnTheStrokeWithAHooksInnerRunUnread() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        bench.state.curves.snapGuides = false
        bench.state.curves.stabilizer = 0f
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        pressThrough(line(camera, 0f to 0.8f, 1.4f to 0.8f, 0.8f to 0.3f, 1.8f to 0.3f, 1.8f to -0.8f, 0f to -0.8f))
        val overlay = tool.overlay(View.SIDE, camera)
        val shade = overlay.of(look.read)
        assertTrue(shade.size > 50, "the shading, ${shade.size} strips")
        val inner = camera.toPixel(CurvePoint(1.1f, 0.55f))
        assertTrue(overlay.of(look.dropped).any { (it.from - inner).getDistance() < 12f }, "the hook's inner run shows unread")
        assertFalse(overlay.of(look.ink).any { (it.from - inner).getDistance() < 3f }, "and not as ink")
        assertTrue(shade.all { camera.toPlane(it.from).x >= -2f * camera.planePixel }, "nothing past the plane shades")
        assertTrue(shade.minOf { minOf(it.from.y, it.to.y) } < camera.toPixel(CurvePoint(0f, 0.7f)).y, "up to the top crossing")
        letGo()
    }

    @Test
    fun theLineTrailsThePointerTheTipStaysOnItAndReleaseFinishesThere() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        bench.state.curves.snapGuides = false
        bench.state.curves.stabilizer = 1f
        bench.state.curves.closeEnd = false
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val path = line(camera, 0.2f to 0.5f, 1.5f to 0.5f)
        pressThrough(path)
        val overlay = tool.overlay(View.SIDE, camera)
        assertTrue(overlay.dots.any { it.color == look.tip && (it.center - path.last()).getDistance() < NEAR_PIXELS }, "the raw tip")
        val inkEnd = overlay.of(look.ink).maxOf { maxOf(camera.toPlane(it.from).x, camera.toPlane(it.to).x) }
        val tipAt = camera.toPlane(path.last()).x
        assertTrue((tipAt - inkEnd) / camera.planePixel > 15f, "the line trails by the string: ${(tipAt - inkEnd) / camera.planePixel}")
        assertEquals(1, overlay.of(look.string).size, "the string to the tip")
        letGo()
        val flat = outlineSamples(assertNotNull(bench.outline(View.SIDE)))
        assertEquals(camera.toPlane(path.last()).x, flat.maxOf { it.x }, 2f * camera.planePixel, "release finishes at the pointer")
    }

    @Test
    fun altDuringAStrokeForcesACornerAtThePointer() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        bench.state.curves.snapGuides = false
        bench.state.curves.smooth = 0f
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val arc = List(120) {
            val angle = PI * it / 119 - PI / 2.0
            camera.toPixel(CurvePoint(0.8f + 0.7f * cos(angle).toFloat(), 0.7f * sin(angle).toFloat()))
        }
        val node = onNodeWithTag(PANE_TAG)
        node.performMouseInput {
            moveTo(arc.first())
            press()
            arc.take(60).drop(1).forEach { moveTo(it) }
        }
        val at = camera.toPlane(arc[60])
        node.performKeyInput {
            withKeyDown(Key.AltLeft) {
                node.performMouseInput { moveTo(arc[60]) }
            }
        }
        node.performMouseInput {
            arc.drop(61).forEach { moveTo(it) }
            release()
        }
        waitForIdle()
        val path = assertNotNull(bench.outline(View.SIDE)).toPath()
        val anchors = path.segments.drop(1).map { it.p0 }
        assertTrue(anchors.any { kotlin.math.hypot(it.x - at.x, it.y - at.y) < 2f * camera.planePixel }, "an anchor at the pointer: $anchors near $at")
    }
}
