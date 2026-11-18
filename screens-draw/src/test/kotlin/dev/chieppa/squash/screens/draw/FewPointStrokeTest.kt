package dev.chieppa.squash.screens.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.anchors
import dev.chieppa.squash.document.isSmooth
import dev.chieppa.squash.geom.DEFAULT_STROKE_FIT
import dev.chieppa.squash.geom.FAITHFUL_FIT
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.fitTolerance
import dev.chieppa.squash.geom.longestSpan
import dev.chieppa.squash.screens.draw.StrokeParts.leftEar
import dev.chieppa.squash.screens.draw.StrokeParts.leftLeg
import dev.chieppa.squash.screens.draw.StrokeParts.tail
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.sheet.LockedCamera
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** The fewest and most points a limb's outline drawn at the default Fit lands on. */
private val LIMB_POINTS = 4..8

/** How far past the fit's tolerance the curve may lie from the raw stroke, in screen pixels: the hand's wobble the smoothing takes out. */
private const val WOBBLE_SLACK_PIXELS = 4f

/** How far past the fit's tolerance an ellipse may read, measured round its radii on the long one's scale. */
private const val ELLIPSE_SLACK_PIXELS = 2f

/** Strokes drawn through the real Draw outline tool fit to few curve points, as the Fit setting asks, and the snapping modifiers. */
@Tag("integration")
@OptIn(ExperimentalTestApi::class)
class FewPointStrokeTest {
    private class Drawn(val anchors: Int, val stored: Int, val worstPixels: Float, val meanPixels: Float, val fitPixels: Float?)

    /** How far each point of [stroke] on the part's side of the socket plane lies from [outline] on screen. */
    private fun distances(outline: OutlineCurve, stroke: List<CurvePoint>, camera: LockedCamera): List<Float> {
        val flat = outline.toPath().flatten(64).let { line -> line.xs.indices.map { camera.toPixel(CurvePoint(line.xs[it], line.ys[it])) } }
        return stroke.filter { it.x >= 0f }.map { distanceToPaths(camera.toPixel(it), listOf(flat)) }
    }

    @Test
    fun aLegAnEarAndATailDrawnByHandFitToFewPointsAndFaithfulToMany() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            val cases = listOf(leftLeg to LEG_STROKE, leftEar to EAR_STROKE, tail to TAIL_STROKE)
            showPanes(camera(part(leftLeg.id), View.SIDE))
            for ((instance, stroke) in cases) {
                state.selectedPart.value = instance.id
                val camera = camera(part(instance.id), stroke.view)
                val points = stroke.points()
                val drawn = listOf(FAITHFUL_FIT, DEFAULT_STROKE_FIT).associateWith { fit ->
                    state.curves.fit = fit
                    val steps = session.history.steps.value.done.size
                    strokeIn(camera, points)
                    assertEquals(steps + 1, session.history.steps.value.done.size, "${stroke.name}: one stroke, one step")
                    val outline = outline(instance.id, stroke.view)
                    val off = distances(outline, points, camera)
                    println("few-points-data ${stroke.name} $fit ${camera.planePixel} stroke=${points.joinToString(";") { "${it.x},${it.y}" }} curve=${outline.points.joinToString(";") { "${it.x},${it.y}" }}")
                    Drawn(outline.anchors.size, outline.points.size, off.max(), off.average().toFloat(), state.curves.fitError(outline)).also {
                        session.history.undo()
                        settled()
                    }
                }
                val faithful = drawn.getValue(FAITHFUL_FIT)
                val few = drawn.getValue(DEFAULT_STROKE_FIT)
                val length = points.zipWithNext().sumOf { (a, b) -> kotlin.math.hypot(b.x - a.x, b.y - a.y).toDouble() }.toFloat() / camera.planePixel
                println(
                    "few-points ${stroke.name}: ${points.size} samples, ${length.toInt()} px long; " +
                        "faithful ${faithful.anchors} anchors (${faithful.stored} stored), off the stroke worst ${faithful.worstPixels.f()} px mean ${faithful.meanPixels.f()} px, fit ${faithful.fitPixels?.f()} px; " +
                        "default ${few.anchors} anchors (${few.stored} stored), off the stroke worst ${few.worstPixels.f()} px mean ${few.meanPixels.f()} px, fit ${few.fitPixels?.f()} px",
                )
                assertTrue(few.anchors in LIMB_POINTS, "${stroke.name}: ${few.anchors} points at the default fit")
                assertTrue(faithful.anchors > few.anchors, "${stroke.name}: faithful ${faithful.anchors}, default ${few.anchors}")
                val span = longestSpan(points.filter { it.x >= 0f }.map { Vec2(it.x, it.y) })
                val allowed = fitTolerance(DEFAULT_STROKE_FIT, camera.planePixel, span) / camera.planePixel + WOBBLE_SLACK_PIXELS
                assertTrue(few.worstPixels <= allowed, "${stroke.name}: the curve lies ${few.worstPixels} px off the stroke, past $allowed")
            }
        }
    }

    @Test
    fun movingTheFitTowardSmoothNeverAddsPoints() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = tail.id
            val camera = camera(part(tail.id), View.SIDE)
            showPanes(camera)
            val counts = listOf(1f, 0.7f, DEFAULT_STROKE_FIT, 0f).map { fit ->
                state.curves.fit = fit
                strokeIn(camera, TAIL_STROKE.points())
                outline(tail.id, View.SIDE).anchors.size.also {
                    session.history.undo()
                    settled()
                }
            }
            assertTrue(counts.zipWithNext().all { (a, b) -> b <= a }, "points from faithful to smooth: $counts")
            assertTrue(counts.first() > counts.last(), "the setting changes the count: $counts")
        }
    }

    @Test
    fun aDrawnCurveJoinsSmoothlyAtEveryPointButTheSocketEnds() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = leftLeg.id
            val camera = camera(part(leftLeg.id), View.SIDE)
            showPanes(camera)
            strokeIn(camera, LEG_STROKE.points())
            val outline = outline(leftLeg.id, View.SIDE)
            val inner = outline.anchors.drop(1).dropLast(1)
            assertTrue(inner.isNotEmpty() && inner.all(outline::isSmooth), "every inner point turns smoothly: ${outline.points}")
        }
    }

    private fun Float.f() = "%.2f".format(this)

    /** A drag through [path] in pane pixels with [keys] held from before the press until after the release. */
    private fun androidx.compose.ui.test.ComposeUiTest.strokeHolding(keys: List<Key>, path: List<Offset>) {
        val node = onNodeWithTag(BENCH_TAG)
        fun hold(rest: List<Key>) {
            if (rest.isEmpty()) {
                node.performMouseInput {
                    moveTo(path.first())
                    press()
                    path.drop(1).forEach { moveTo(it) }
                    release()
                }
                return
            }
            node.performKeyInput { withKeyDown(rest.first()) { hold(rest.drop(1)) } }
        }
        hold(keys)
        waitForIdle()
    }

    @Test
    fun ctrlSnapsAStrokeToAStraightLineAndCtrlShiftToAnEllipse() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = tail.id
            state.curves.snapGuides = false
            val camera = camera(part(tail.id), View.FRONT)
            showPanes(camera)
            val wobbly = List(40) { i ->
                val t = i / 39f
                camera.toPixel(CurvePoint(-0.08f + 0.16f * t, -0.05f + 0.1f * t + 0.012f * kotlin.math.sin(t * 9f)))
            }
            strokeHolding(listOf(Key.CtrlLeft, Key.ShiftLeft), wobbly)
            settled()
            val ellipse = outline(tail.id, View.FRONT)
            val from = camera.toPlane(wobbly.first())
            val to = camera.toPlane(wobbly.last())
            val center = CurvePoint((from.x + to.x) / 2f, (from.y + to.y) / 2f)
            val rx = kotlin.math.abs(to.x - from.x) / 2f
            val ry = kotlin.math.abs(to.y - from.y) / 2f
            val flat = ellipse.toPath().flatten(32)
            val worst = flat.xs.indices.maxOf { i ->
                val dx = (flat.xs[i] - center.x) / rx
                val dy = (flat.ys[i] - center.y) / ry
                kotlin.math.abs(kotlin.math.sqrt(dx * dx + dy * dy) - 1f)
            }
            val offPixels = worst * maxOf(rx, ry) / camera.planePixel
            val allowed = fitTolerance(DEFAULT_STROKE_FIT, camera.planePixel, 2f * maxOf(rx, ry)) / camera.planePixel + ELLIPSE_SLACK_PIXELS
            assertTrue(offPixels <= allowed, "the curve is the ellipse in the stroke's box, off by $offPixels px, past $allowed")
            assertTrue(ellipse.anchors.size in 2..8, "an ellipse takes a few points: ${ellipse.anchors.size}")

            session.history.undo()
            settled()
            state.selectedPart.value = leftLeg.id
            val side = camera(part(leftLeg.id), View.SIDE)
            val bent = List(40) { i ->
                val t = i / 39f
                side.toPixel(CurvePoint(0.3f * t, 0.05f + 0.03f * kotlin.math.sin(t * 6f)))
            }
            state.curves.closeEnd = false
            shown = side
            waitForIdle()
            strokeHolding(listOf(Key.CtrlLeft), bent)
            settled()
            val line = outline(leftLeg.id, View.SIDE)
            val a = side.toPlane(bent.first())
            val b = side.toPlane(bent.last())
            val path = line.toPath().flatten(16)
            val drawnPart = path.xs.indices.filter { path.xs[it] > 0.02f }
            val off = drawnPart.maxOf { i ->
                val t = ((path.xs[i] - a.x) * (b.x - a.x) + (path.ys[i] - a.y) * (b.y - a.y)) / ((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
                kotlin.math.hypot(path.xs[i] - (a.x + (b.x - a.x) * t), path.ys[i] - (a.y + (b.y - a.y) * t))
            } / side.planePixel
            assertTrue(off < 1.5f, "the drawn edge lies along the straight line from press to release, off by $off px")
        }
    }

    @Test
    fun theSameStrokeSmallOrLargeKeepsTheSamePoints() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = tail.id
            val camera = camera(part(tail.id), View.SIDE)
            showPanes(camera)
            val counts = listOf(0.3f, 0.6f).map { length ->
                strokeIn(camera, TAIL_STROKE.scaled(length).points())
                outline(tail.id, View.SIDE).anchors.size.also {
                    session.history.undo()
                    settled()
                }
            }
            assertTrue(kotlin.math.abs(counts[0] - counts[1]) <= 2, "a tail half the size keeps its points: $counts")
        }
    }

    @Test
    fun whatReleaseWouldStoreShowsOverTheStrokeAsItGrows() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = leftLeg.id
            val camera = camera(part(leftLeg.id), View.SIDE)
            showPanes(camera)
            val path = LEG_STROKE.points().map(camera::toPixel)
            onNodeWithTag(BENCH_TAG).performMouseInput {
                moveTo(path.first())
                press()
                path.drop(1).forEach { moveTo(it) }
            }
            waitForIdle()
            val live = tool.overlay(View.SIDE, camera).dots.filter { it.color == TEST_CURVE_LOOK.ring && it.radius == TEST_CURVE_LOOK.handleRadius }
            onNodeWithTag(BENCH_TAG).performMouseInput { release() }
            waitForIdle()
            settled()
            val stored = outline(leftLeg.id, View.SIDE)
            val anchors = stored.anchors.map { camera.toPixel(stored.points[it]) }
            assertEquals(anchors.size, live.size, "one dot a point of what release stores")
            for (anchor in anchors) assertTrue(live.any { (it.center - anchor).getDistance() < 0.5f }, "a point at $anchor showed before release")
        }
    }

    @Test
    fun aLoopThatOvershootsItsStartHasNoClusterOfPointsWhereItCloses() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = StrokeParts.torso.id
            val camera = camera(part(StrokeParts.torso.id), View.SIDE)
            showPanes(camera)
            val loop = List(220) { i ->
                val angle = 2.0 * Math.PI * i / 199 + 2.4
                val wobble = 0.004f * kotlin.math.sin(i * 0.9f)
                CurvePoint(0.05f + 0.3f * kotlin.math.cos(angle).toFloat() + wobble, 0.15f * kotlin.math.sin(angle).toFloat() - wobble)
            }
            strokeIn(camera, loop)
            val outline = outline(StrokeParts.torso.id, View.SIDE)
            val anchors = outline.anchors.map { camera.toPixel(outline.points[it]) }
            val closest = (anchors + anchors.first()).zipWithNext().minOf { (a, b) -> (a - b).getDistance() }
            println("few-points loop: ${anchors.size} points, nearest two ${closest.f()} px apart")
            assertTrue(closest > 20f, "no two points bunch together: nearest $closest px apart in ${anchors.size}")
        }
    }
}
