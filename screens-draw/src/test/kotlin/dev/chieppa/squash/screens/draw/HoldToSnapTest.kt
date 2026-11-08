package dev.chieppa.squash.screens.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.sheet.LockedCamera
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NEAR_PIXELS = 3f

/** Holds a stroke in a pane on the test clock: nothing passes but what these tests advance. */
@OptIn(ExperimentalTestApi::class)
class HoldToSnapTest {
    private fun bench(view: View, block: ComposeUiTest.(StrokeBench, LockedCamera) -> Unit) = runComposeUiTest {
        val bench = StrokeBench()
        bench.state.selectedPart.value = StrokeParts.leftLeg.id
        with(bench) { settled() }
        val camera = bench.camera(bench.part(StrokeParts.leftLeg.id), view)
        with(bench) { show(camera) }
        block(bench, camera)
    }

    private fun ComposeUiTest.pressAlong(path: List<Offset>) {
        onNodeWithTag(BENCH_TAG).performMouseInput {
            moveTo(path.first())
            press()
            path.drop(1).forEach { moveTo(it) }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.rest(millis: Long = HOLD_SNAP_MILLIS + 1) {
        mainClock.advanceTimeBy(millis)
        waitForIdle()
    }

    private fun ComposeUiTest.moveTo(at: Offset) {
        onNodeWithTag(BENCH_TAG).performMouseInput { moveTo(at) }
        waitForIdle()
    }

    private fun ComposeUiTest.let_go() {
        onNodeWithTag(BENCH_TAG).performMouseInput { release() }
        waitForIdle()
    }

    /** A slightly wobbly ellipse in pane pixels, from its right back to its right. */
    private fun wobblyEllipse(center: Offset, a: Float, b: Float, count: Int = 90) = List(count) {
        val angle = 2.0 * PI * it / (count - 1)
        val wobble = if (it == 0 || it == count - 1) 0f else 1.2f * sin(it * 1.7f)
        center + Offset((a + wobble) * cos(angle).toFloat(), (b + wobble) * sin(angle).toFloat())
    }

    private fun wobblyLine(from: Offset, to: Offset, count: Int = 40) = List(count) {
        val t = it.toFloat() / (count - 1)
        from + (to - from) * t + Offset(0f, if (it == 0 || it == count - 1) 0f else 1.2f * sin(it * 1.7f))
    }

    private fun live(bench: StrokeBench) = assertNotNull(bench.tool.liveStroke).points

    private fun undoSteps(bench: StrokeBench) = bench.session.history.steps.value.done.size

    private fun List<CurvePoint>.spanX() = maxOf { it.x } - minOf { it.x }

    private fun List<CurvePoint>.spanY() = maxOf { it.y } - minOf { it.y }

    @Test
    fun aPauseMakesTheEllipseShownInPlaceAndNoSoonerThanTheWaitIsUp() = bench(View.FRONT) { bench, camera ->
        mainClock.autoAdvance = false
        val center = Offset(BENCH_WIDTH / 2f, BENCH_HEIGHT / 2f)
        val stroke = wobblyEllipse(center, 150f, 90f)
        pressAlong(stroke)
        val drawn = live(bench)
        rest(HOLD_SNAP_MILLIS - 50)
        assertEquals(drawn, live(bench), "the stroke stands until the wait is up")
        rest(100)
        val shape = live(bench)
        assertNotEquals(drawn, shape)
        assertEquals(shape.first(), shape.last(), "the ellipse is closed")
        val pixels = shape.map(camera::toPixel)
        val centered = pixels.map { it - center }
        val worst = centered.maxOf { abs(it.x * it.x / (150f * 150f) + it.y * it.y / (90f * 90f) - 1f) }
        assertTrue(worst < 0.1f, "the shape is the ellipse the stroke meant: off by $worst")
        let_go()
    }

    @Test
    fun aHeldEllipseGrowsWithThePointerAndReleaseCommitsWhatWasShownAsOneStep() = bench(View.FRONT) { bench, camera ->
        mainClock.autoAdvance = false
        val center = Offset(BENCH_WIDTH / 2f, BENCH_HEIGHT / 2f)
        val before = undoSteps(bench)
        pressAlong(wobblyEllipse(center, 100f, 60f))
        rest()
        val small = live(bench)
        moveTo(center + Offset(150f, 0f))
        val large = live(bench)
        assertEquals(1.5f, large.spanX() / small.spanX(), 0.05f, "the size follows the pointer")
        assertEquals(large.spanX() / large.spanY(), small.spanX() / small.spanY(), 0.02f, "and the shape keeps its proportions")
        let_go()
        val curve = bench.outline(StrokeParts.leftLeg.id, View.FRONT)
        assertEquals(before + 1, undoSteps(bench))
        assertMatches(curve, large, camera)
        assertNull(bench.tool.liveStroke)
    }

    @Test
    fun aHeldLinesEndFollowsThePointerThenTheEndsAreAStrokes() = bench(View.SIDE) { bench, camera ->
        mainClock.autoAdvance = false
        val from = camera.toPixel(CurvePoint(0.08f, 0.1f))
        val to = camera.toPixel(CurvePoint(0.3f, 0.02f))
        pressAlong(wobblyLine(from, to))
        rest()
        val elsewhere = camera.toPixel(CurvePoint(0.4f, -0.06f))
        moveTo(elsewhere)
        val shown = live(bench)
        assertTrue((camera.toPixel(shown.last()) - elsewhere).getDistance() < NEAR_PIXELS, "the end is at the pointer")
        assertTrue((camera.toPixel(shown.first()) - from).getDistance() < NEAR_PIXELS, "the start stays")
        let_go()
        val path = bench.outline(StrokeParts.leftLeg.id, View.SIDE).toPath().flatten()
        assertEquals(0f, path.xs.first(), "carried to the socket plane, as a stroke is")
        assertEquals(0f, path.xs.last())
        assertEquals(0.4f, path.xs.max(), 0.01f)
    }

    @Test
    fun anEllipseAcrossTheSocketPlaneIsCutThere() = bench(View.SIDE) { bench, camera ->
        mainClock.autoAdvance = false
        val center = camera.toPixel(CurvePoint(0.1f, 0f))
        pressAlong(wobblyEllipse(center, 120f, 50f))
        rest()
        let_go()
        val path = bench.outline(StrokeParts.leftLeg.id, View.SIDE).toPath().flatten()
        assertEquals(0f, path.xs.first())
        assertEquals(0f, path.xs.last())
        assertTrue(path.xs.all { it >= -1e-3f }, "nothing behind the plane")
    }

    /** A slightly wobbly leg's U in the plane: in along y = [half] from x = [from], round a cap reaching x = [far], back to x = [backTo]. */
    private fun wobblyU(camera: LockedCamera, far: Float, half: Float, from: Float, backTo: Float = from): List<Offset> {
        val turn = far - half
        val inward = List(30) { CurvePoint(from + (turn - from) * it / 30f, half) }
        val cap = List(30) {
            val angle = PI / 2.0 - PI * it / 30.0
            CurvePoint(turn + half * cos(angle).toFloat(), half * sin(angle).toFloat())
        }
        val back = List(31) { CurvePoint(turn + (backTo - turn) * it / 30f, -half) }
        return (inward + cap + back).mapIndexed { i, p -> camera.toPixel(p) + Offset(0f, if (i == 0 || i == 90) 0f else 1.2f * sin(i * 1.7f)) }
    }

    private fun halfOfSpanY(curve: OutlineCurve) = curve.toPath().flatten().ys.let { (it.max() - it.min()) / 2f }

    @Test
    fun aHeldOpenUAcrossThePlaneBecomesTheCleanShapeCutThereAsOneStep() = bench(View.SIDE) { bench, camera ->
        mainClock.autoAdvance = false
        val before = undoSteps(bench)
        pressAlong(wobblyU(camera, 0.45f, 0.1f, -0.05f))
        val drawn = live(bench)
        rest()
        val shape = live(bench)
        assertNotEquals(drawn, shape, "the U snapped")
        assertEquals(shape.first(), shape.last(), "to the whole shape, which the cut then opens")
        let_go()
        val curve = bench.outline(StrokeParts.leftLeg.id, View.SIDE)
        assertEquals(before + 1, undoSteps(bench))
        val path = curve.toPath().flatten()
        assertEquals(0f, path.xs.first())
        assertEquals(0f, path.xs.last())
        assertTrue(path.xs.all { it >= -1e-3f }, "nothing behind the plane")
        assertEquals(0.45f, path.xs.max(), NEAR_PIXELS * 2f * camera.planePixel, "it reaches as far as the U")
        assertEquals(0.1f, halfOfSpanY(curve), NEAR_PIXELS * 2f * camera.planePixel, "and is as wide")
        assertMatches(curve, shape.filter { it.x >= 0f }, camera)
    }

    @Test
    fun aHeldUGrowsWithThePointerAroundThePlane() = bench(View.SIDE) { bench, camera ->
        mainClock.autoAdvance = false
        val stroke = wobblyU(camera, 0.45f, 0.1f, -0.05f)
        pressAlong(stroke)
        rest()
        val small = live(bench)
        val centre = camera.toPixel(CurvePoint(0f, 0f))
        moveTo(centre + (stroke.last() - centre) * 1.5f)
        val large = live(bench)
        assertEquals(1.5f, large.spanY() / small.spanY(), 0.1f)
        let_go()
        val path = bench.outline(StrokeParts.leftLeg.id, View.SIDE).toPath().flatten()
        assertEquals(0f, path.xs.first())
        assertEquals(0f, path.xs.last())
    }

    @Test
    fun aHeldUReadsInTopToo() = bench(View.TOP) { bench, camera ->
        mainClock.autoAdvance = false
        pressAlong(wobblyU(camera, 0.45f, 0.1f, -0.05f))
        val drawn = live(bench)
        rest()
        assertNotEquals(drawn, live(bench))
        let_go()
        val path = bench.outline(StrokeParts.leftLeg.id, View.TOP).toPath().flatten()
        assertEquals(0f, path.xs.first())
        assertEquals(0f, path.xs.last())
    }

    @Test
    fun aHeldUOverADrawnOutlineRedrawsItAsOneStep() = bench(View.SIDE) { bench, camera ->
        val drawn = PlaneStroke(View.SIDE, 0.25f, 0f, 0.3f, 0.12f)
        with(bench) { stroke(drawn.points().map(camera::toPixel)) }
        with(bench) { settled() }
        val steps = undoSteps(bench)
        mainClock.autoAdvance = false
        pressAlong(wobblyU(camera, 0.6f, 0.08f, -0.05f))
        rest()
        let_go()
        assertEquals(steps + 1, undoSteps(bench))
        val path = bench.outline(StrokeParts.leftLeg.id, View.SIDE).toPath().flatten()
        assertEquals(0.6f, path.xs.max(), NEAR_PIXELS * 2f * camera.planePixel)
        assertEquals(0f, path.xs.first())
        assertEquals(0f, path.xs.last())
    }

    @Test
    fun aHeldUShortOfThePlaneStaysAsDrawn() = bench(View.SIDE) { bench, camera ->
        mainClock.autoAdvance = false
        pressAlong(wobblyU(camera, 0.45f, 0.1f, 0.12f))
        val drawn = live(bench)
        rest()
        assertEquals(drawn, live(bench))
        let_go()
    }

    @Test
    fun aHeldUWithCloseEndOffStaysAsDrawn() = bench(View.SIDE) { bench, camera ->
        mainClock.autoAdvance = false
        bench.state.curves.closeEnd = false
        pressAlong(wobblyU(camera, 0.45f, 0.1f, -0.05f))
        val drawn = live(bench)
        rest()
        assertEquals(drawn, live(bench))
        let_go()
    }

    @Test
    fun aHeldUInFrontStaysAsDrawn() = bench(View.FRONT) { bench, camera ->
        mainClock.autoAdvance = false
        pressAlong(wobblyU(camera, 0.45f, 0.1f, -0.05f))
        val drawn = live(bench)
        rest()
        assertEquals(drawn, live(bench))
        let_go()
    }

    @Test
    fun aRestingPointerThatJittersStillSnaps() = bench(View.FRONT) { bench, _ ->
        mainClock.autoAdvance = false
        val center = Offset(BENCH_WIDTH / 2f, BENCH_HEIGHT / 2f)
        val stroke = wobblyEllipse(center, 150f, 90f)
        pressAlong(stroke)
        val drawn = live(bench)
        repeat(3) {
            moveTo(stroke.last() + Offset(if (it % 2 == 0) 1f else -1f, 1f))
            rest(50)
        }
        rest(HOLD_SNAP_MILLIS)
        assertNotEquals(drawn.size, live(bench).size, "jitter inside the slop does not start the wait over")
        let_go()
    }

    @Test
    fun aStrokeThatIsNoShapeStaysAsDrawnAndGoesOn() = bench(View.SIDE) { bench, camera ->
        mainClock.autoAdvance = false
        val zigzag = List(40) { camera.toPixel(CurvePoint(0.05f + it * 0.012f, if (it / 5 % 2 == 0) 0.08f else -0.08f)) }
        pressAlong(zigzag)
        val drawn = live(bench)
        rest()
        assertEquals(drawn, live(bench), "stays freehand")
        val on = zigzag.last() + Offset(60f, 0f)
        moveTo(on)
        assertEquals(drawn.size + 1, live(bench).size, "the stroke goes on")
        let_go()
    }

    @Test
    fun withTheSwitchOffAPauseChangesNothing() = bench(View.FRONT) { bench, _ ->
        mainClock.autoAdvance = false
        bench.state.curves.holdSnap = false
        pressAlong(wobblyEllipse(Offset(BENCH_WIDTH / 2f, BENCH_HEIGHT / 2f), 150f, 90f))
        val drawn = live(bench)
        rest()
        assertEquals(drawn, live(bench))
        let_go()
    }

    @Test
    fun aHeldLineInAFrontStillBuilds() = bench(View.FRONT) { bench, _ ->
        mainClock.autoAdvance = false
        val center = Offset(BENCH_WIDTH / 2f, BENCH_HEIGHT / 2f)
        pressAlong(wobblyLine(center - Offset(120f, 80f), center + Offset(120f, 80f)))
        rest()
        let_go()
        mainClock.autoAdvance = true
        with(bench) { settled() }
        assertNull(bench.session.mesh(StrokeParts.leftLeg.id).failure.value)
    }

    @Test
    fun aHeldLineOverADrawnOutlineRedrawsOnlyItsSpanAsOneStep() = bench(View.SIDE) { bench, camera ->
        val drawn = PlaneStroke(View.SIDE, 0.25f, 0f, 0.3f, 0.12f)
        with(bench) { stroke(drawn.points().map(camera::toPixel)) }
        with(bench) { settled() }
        val before = bench.outline(StrokeParts.leftLeg.id, View.SIDE)
        val steps = undoSteps(bench)
        mainClock.autoAdvance = false
        pressAlong(wobblyLine(camera.toPixel(CurvePoint(0.1f, 0.13f)), camera.toPixel(CurvePoint(0.4f, 0.13f))))
        rest()
        let_go()
        val after = bench.outline(StrokeParts.leftLeg.id, View.SIDE)
        assertNotEquals(before, after)
        assertEquals(steps + 1, undoSteps(bench))
        assertEquals(before.points.first(), after.points.first(), "outside the span stays exact")
    }

    private fun assertMatches(curve: OutlineCurve, shown: List<CurvePoint>, camera: LockedCamera) {
        val flat = curve.toPath().flatten()
        val pixels = flat.xs.indices.map { camera.toPixel(CurvePoint(flat.xs[it], flat.ys[it])) }
        val worst = shown.map(camera::toPixel).maxOf { point -> distanceToPaths(point, listOf(pixels)) }
        assertTrue(worst < NEAR_PIXELS, "release strays $worst pixels from what was shown")
    }
}
