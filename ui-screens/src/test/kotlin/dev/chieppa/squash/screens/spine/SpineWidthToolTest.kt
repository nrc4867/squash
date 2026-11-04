package dev.chieppa.squash.screens.spine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.closed
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.screens.draw.BENCH_SCALE
import dev.chieppa.squash.screens.draw.BENCH_TAG
import dev.chieppa.squash.screens.draw.StrokeBench
import dev.chieppa.squash.screens.draw.StrokeParts
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.TEST_STROKE_LOOK
import dev.chieppa.squash.screens.edit.edges
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.outlineSamples
import dev.chieppa.squash.screens.sheet.paneDisagreements
import dev.chieppa.squash.screens.sheet.sheetPartOf
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.squash.screens.single.viewThumbnails
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIXEL = 1f / BENCH_SCALE
private const val SLACK_PIXELS = 4f

private val TOP_TO_GUIDE = edges(0f to 0.1f, 0.4f to 0.1f, 0.4f to -0.1f, 0f to -0.1f)

internal val TEST_SPINE_LOOK = SpineLook(TEST_STROKE_LOOK, TEST_CURVE_LOOK, Color.Blue, 3f, 9f, 3f, 9f, 3f, 2f, 7f, 7f)

/** Spine and width through the pointer, on a real character with its graph. */
@OptIn(ExperimentalTestApi::class)
class SpineWidthToolTest {
    private val centre: List<CurvePoint> = List(60) { i ->
        val t = i / 59f
        CurvePoint(0.5f * t, 0.1f * sin(t * 2f))
    }

    private fun withTool(id: PartId, block: ComposeUiTest.(StrokeBench, SpineWidthTool, LockedCamera) -> Unit) = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = id
            val tool = SpineWidthTool(state, TEST_SPINE_LOOK)
            working = tool
            val camera = camera(part(id), View.SIDE)
            showPanes(camera)
            block(bench, tool, camera)
        }
        bench.close()
    }

    private fun ComposeUiTest.pointer(bench: StrokeBench, path: List<Offset>) = with(bench) { stroke(path) }

    private fun heldEnd(bench: StrokeBench, camera: LockedCamera): Offset {
        val spine = bench.state.spine
        val across = assertNotNull(spine.preview).shape.across(spine.handleAt)
        return camera.toPixel(CurvePoint(across.second.x, across.second.y))
    }

    private fun outlineOf(bench: StrokeBench, id: PartId, view: View): OutlineCurve? =
        bench.session.document.value.character.resolved(id)?.outlines?.get(view)

    private fun steps(bench: StrokeBench) = bench.session.history.steps.value.done.size

    /** A drawn Top out to x = 0.4, whose end is a guide in Side, and a centre stroke reaching [past] pixels beyond it. */
    private fun ComposeUiTest.strokeTowardsTopsEnd(bench: StrokeBench, camera: LockedCamera, past: Float) {
        bench.session.send(SetOutline(StrokeParts.tail.id, View.TOP, TOP_TO_GUIDE))
        with(bench) { settled() }
        val reach = 0.4f + past * camera.planePixel
        pointer(bench, List(61) { camera.toPixel(CurvePoint(reach * it / 60f, 0.02f * sin(it / 20f))) })
    }

    private fun farEnd(bench: StrokeBench) = assertNotNull(bench.state.spine.preview).shape.line.points.last()

    @Test
    fun theSpinesFarEndCatchesAGuideAndWritesThere() = withTool(StrokeParts.tail.id) { bench, tool, camera ->
        bench.state.curves.stabilizer = 0f
        strokeTowardsTopsEnd(bench, camera, 5f)
        assertEquals(0.4f, farEnd(bench).x, 1e-3f, "on the guide")
        assertTrue(bench.state.spine.caughtGuides.isNotEmpty())
        assertTrue(tool.overlay(View.SIDE, camera).lines.any { it.color == TEST_STROKE_LOOK.caught }, "the guide lights")
        val end = heldEnd(bench, camera)
        pointer(bench, listOf(end, end + Offset(4f, 4f)))
        with(bench) { settled() }
        val side = assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.SIDE))
        assertEquals(0.4f, outlineSamples(side).maxOf { it.x }, 2f * camera.planePixel, "written on the guide")
    }

    @Test
    fun snapToGuidesOffLeavesTheSpineWhereItWasDrawn() = withTool(StrokeParts.tail.id) { bench, tool, camera ->
        bench.state.curves.stabilizer = 0f
        bench.state.curves.snapGuides = false
        strokeTowardsTopsEnd(bench, camera, 5f)
        assertEquals(0.4f + 5f * camera.planePixel, farEnd(bench).x, 1e-3f)
        assertTrue(tool.overlay(View.SIDE, camera).lines.none { it.color == TEST_STROKE_LOOK.caught })
    }

    @Test
    fun aGrowingSpineTrailsThePointerOnAStringThatReleaseTakesUp() = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = StrokeParts.tail.id
            state.curves.stabilizer = 1f
            state.curves.snapGuides = false
            val tool = SpineWidthTool(state, TEST_SPINE_LOOK)
            working = tool
            val camera = camera(part(StrokeParts.tail.id), View.SIDE)
            showPanes(camera)
            val path = centre.map(camera::toPixel)
            onNodeWithTag(BENCH_TAG).performMouseInput {
                moveTo(path.first())
                press()
                path.drop(1).forEach { moveTo(it) }
            }
            waitForIdle()
            val thin = tool.overlay(View.SIDE, camera).lines.filter { it.color == TEST_STROKE_LOOK.string && it.width == TEST_STROKE_LOOK.stringWidth }
            assertTrue(thin.isNotEmpty(), "the string shows")
            assertEquals(24f, (thin.last().to - thin.first().from).getDistance(), 3f, "as long as the slider says")
            assertTrue((thin.last().to - path.last()).getDistance() < 1f, "and ends at the pointer")
            onNodeWithTag(BENCH_TAG).performMouseInput { release() }
            waitForIdle()
            assertTrue(tool.overlay(View.SIDE, camera).lines.none { it.color == TEST_STROKE_LOOK.string && it.width == TEST_STROKE_LOOK.stringWidth }, "release takes it up")
            assertEquals(centre.last().x, farEnd(bench).x, 2f * camera.planePixel, "the stroke finishes at the pointer")
        }
        bench.close()
    }

    @Test
    fun theWidthDragIsNotStabilized() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        bench.state.curves.stabilizer = 1f
        pointer(bench, centre.map(camera::toPixel))
        val before = assertNotNull(bench.state.spine.widthPixels)
        val end = heldEnd(bench, camera)
        onNodeWithTag(BENCH_TAG).performMouseInput {
            moveTo(end)
            press()
            moveTo(end + Offset(0f, 6f))
        }
        waitForIdle()
        assertNotEquals(before, bench.state.spine.widthPixels, "six pixels moves it, well inside a full string")
        onNodeWithTag(BENCH_TAG).performMouseInput { release() }
        waitForIdle()
    }

    @Test
    fun aMouseStrokeWaitsForItsWidthAndItsDragWritesBothOutlinesAsOneStep() = withTool(StrokeParts.tail.id) { bench, tool, camera ->
        val before = steps(bench)
        pointer(bench, centre.map(camera::toPixel))
        assertEquals(before, steps(bench), "nothing is written until the width is set")
        assertEquals(View.SIDE, tool.drawingIn)
        assertNotNull(tool.liveStroke)
        assertEquals(listOf(View.TOP), tool.liveAlongside.map { it.view })

        val end = heldEnd(bench, camera)
        pointer(bench, listOf(end, end + Offset(10f, 10f), end + Offset(20f, 20f)))
        with(bench) { settled() }

        assertEquals(before + 1, steps(bench), "one undo step")
        assertTrue(bench.session.history.steps.value.done.last() is SetOutlines)
        assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.SIDE))
        assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.TOP))
        assertNull(outlineOf(bench, StrokeParts.tail.id, View.FRONT))
        assertFalse(bench.state.spine.active, "the spine is not kept")
        assertNull(tool.drawingIn)

        bench.session.history.undo()
        assertNull(outlineOf(bench, StrokeParts.tail.id, View.SIDE))
        assertNull(outlineOf(bench, StrokeParts.tail.id, View.TOP))
        assertEquals(before, steps(bench))
    }

    @Test
    fun theReleaseIsWhatThePreviewShowedInBothViews() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        pointer(bench, centre.map(camera::toPixel))
        val end = heldEnd(bench, camera)
        onNodeWithTag(BENCH_TAG).performMouseInput {
            moveTo(end)
            press()
            moveTo(end + Offset(15f, 15f))
        }
        waitForIdle()
        val preview = assertNotNull(bench.state.spine.preview)
        val side = preview.shape.side
        val top = preview.shape.top
        onNodeWithTag(BENCH_TAG).performMouseInput { release() }
        waitForIdle()
        fun strays(line: List<Vec2>, curve: OutlineCurve): Float {
            val flat = outlineSamples(curve)
            return line.maxOf { p -> flat.minOf { abs(it.x - p.x) + abs(it.y - p.y) } }
        }
        val allowed = SLACK_PIXELS * PIXEL
        assertTrue(strays(side, assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.SIDE))) < 2f * allowed, "Side")
        assertTrue(strays(top, assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.TOP))) < 2f * allowed, "Top")
    }

    @Test
    fun aDragSetsTheWidthAtTheHandleByHowFarThePointerCameOut() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        pointer(bench, centre.map(camera::toPixel))
        val spine = bench.state.spine
        val start = assertNotNull(spine.widthPixels)
        val end = heldEnd(bench, camera)
        val across = assertNotNull(spine.preview).shape.across(spine.handleAt)
        val out = camera.toPixel(CurvePoint(across.second.x, across.second.y)) - camera.toPixel(CurvePoint(across.first.x, across.first.y))
        val unit = out / out.getDistance()
        onNodeWithTag(BENCH_TAG).performMouseInput {
            moveTo(end)
            press()
            moveTo(end + unit * 30f)
        }
        waitForIdle()
        assertEquals(start + 60f, assertNotNull(spine.widthPixels), 8f, "both sides of the spine grow")
        onNodeWithTag(BENCH_TAG).performMouseInput { release() }
        waitForIdle()
    }

    @Test
    fun aClickOnTheShapeTakesTheWidthAsItStands() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        val before = steps(bench)
        pointer(bench, centre.map(camera::toPixel))
        val width = assertNotNull(bench.state.spine.preview).shape.halfWidths.max()
        val on = camera.toPixel(centre[30])
        onNodeWithTag(BENCH_TAG).performMouseInput {
            moveTo(on)
            press()
            release()
        }
        waitForIdle()
        assertEquals(before + 1, steps(bench))
        val top = outlineSamples(assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.TOP)))
        assertEquals(width, top.maxOf { it.y }, SLACK_PIXELS * PIXEL)
    }

    @Test
    fun aPressClearOfTheShapeStartsOver() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        val before = steps(bench)
        pointer(bench, centre.map(camera::toPixel))
        val first = assertNotNull(bench.state.spine.preview).shape.line.points.last()
        val far = centre.map { CurvePoint(it.x, it.y - 0.4f) }
        pointer(bench, far.map(camera::toPixel))
        assertEquals(before, steps(bench))
        val second = assertNotNull(bench.state.spine.preview).shape.line.points.last()
        assertTrue(abs(second.y - first.y) > 0.3f, "the new stroke replaced the old: $first, $second")
    }

    @Test
    fun onlySideTakesAStroke() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        val top = bench.camera(bench.part(StrokeParts.tail.id), View.TOP)
        bench.shown = top
        waitForIdle()
        pointer(bench, centre.map(top::toPixel))
        assertFalse(bench.state.spine.active)
        bench.shown = camera
        waitForIdle()
        pointer(bench, centre.map(camera::toPixel))
        assertTrue(bench.state.spine.active)
    }

    @Test
    fun aStrokeTooShortLeavesNothingInHand() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        val at = camera.toPixel(centre[10])
        pointer(bench, listOf(at, at + Offset(1f, 0f)))
        assertFalse(bench.state.spine.active)
        assertNull(bench.state.curves.drawing)
    }

    @Test
    fun droppingTheStrokeWritesNothing() = withTool(StrokeParts.tail.id) { bench, tool, camera ->
        val before = steps(bench)
        pointer(bench, centre.map(camera::toPixel))
        tool.discard()
        waitForIdle()
        assertFalse(bench.state.spine.active)
        assertEquals(before, steps(bench))
        assertNull(tool.liveStroke)
    }

    private fun writeOn(bench: StrokeBench, compose: ComposeUiTest, camera: LockedCamera) {
        with(compose) {
            pointer(bench, centre.map(camera::toPixel))
            val end = heldEnd(bench, camera)
            pointer(bench, listOf(end, end + Offset(8f, 8f)))
        }
    }

    @Test
    fun withMirrorOnItWritesThePair() = withTool(StrokeParts.leftLeg.id) { bench, _, camera ->
        writeOn(bench, this, camera)
        val side = assertNotNull(outlineOf(bench, StrokeParts.leftLeg.id, View.SIDE))
        assertEquals(side, outlineOf(bench, StrokeParts.rightLeg.id, View.SIDE))
        assertEquals(outlineOf(bench, StrokeParts.leftLeg.id, View.TOP), outlineOf(bench, StrokeParts.rightLeg.id, View.TOP))
    }

    @Test
    fun withMirrorOffItWritesThisSideAlone() = withTool(StrokeParts.leftLeg.id) { bench, _, camera ->
        bench.state.mirror.value = false
        writeOn(bench, this, camera)
        assertNotNull(outlineOf(bench, StrokeParts.leftLeg.id, View.SIDE))
        assertNotNull(outlineOf(bench, StrokeParts.leftLeg.id, View.TOP))
        assertNull(outlineOf(bench, StrokeParts.rightLeg.id, View.SIDE))
        assertNull(outlineOf(bench, StrokeParts.rightLeg.id, View.TOP))
    }

    @Test
    fun aClosedPartWritesClosedOutlines() = withTool(StrokeParts.torso.id) { bench, _, camera ->
        writeOn(bench, this, camera)
        assertTrue(assertNotNull(outlineOf(bench, StrokeParts.torso.id, View.SIDE)).closed)
        assertTrue(assertNotNull(outlineOf(bench, StrokeParts.torso.id, View.TOP)).closed)
    }

    @Test
    fun whatItWritesIsOrdinaryCurveForOversketch() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        writeOn(bench, this, camera)
        with(bench) { settled() }
        val written = assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.SIDE))
        val first = written.toPath().segments.first().p0
        assertEquals(0f, first.x, "it opens on the socket plane")
        bench.working = bench.tool
        waitForIdle()
        val flat = outlineSamples(written)
        val over = flat.subList(flat.size / 8, flat.size / 3).map { camera.toPixel(CurvePoint(it.x, it.y + 12f * PIXEL)) }
        pointer(bench, over)
        with(bench) { settled() }
        val redrawn = assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.SIDE))
        assertNotEquals(written, redrawn, "oversketch took it")
        assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.TOP), "Top stays")
    }

    @Test
    fun whatItWritesIsOrdinaryCurveForEditing() = withTool(StrokeParts.tail.id) { bench, _, camera ->
        writeOn(bench, this, camera)
        val written = assertNotNull(outlineOf(bench, StrokeParts.tail.id, View.SIDE))
        val moved = OutlineCurve(written.points.map { CurvePoint(it.x, it.y + 0.01f) })
        bench.session.send(SetOutline(StrokeParts.tail.id, View.SIDE, moved))
        assertEquals(moved, outlineOf(bench, StrokeParts.tail.id, View.SIDE))
        bench.session.history.undo()
        assertEquals(written, outlineOf(bench, StrokeParts.tail.id, View.SIDE))
    }

    @Test
    fun theViewsItReplacesHoldThePreviewToNothingAndTheThumbnailsShowIt() = withTool(StrokeParts.tail.id) { bench, tool, camera ->
        val short = OutlineCurve(listOf(CurvePoint(0f, 0.01f), CurvePoint(0.01f, 0.01f), CurvePoint(0.01f, -0.01f), CurvePoint(0f, -0.01f)))
        bench.session.send(SetOutline(StrokeParts.tail.id, View.TOP, short))
        pointer(bench, centre.map(camera::toPixel))
        val part = bench.part(StrokeParts.tail.id)
        val selection = SheetSelection(part, mapOf(View.TOP to short))
        assertEquals(emptyList(), paneDisagreements(selection, mapOf(View.SIDE to camera), tool))
        val thumbs = viewThumbnails(selection, View.SIDE, tool.liveStroke, false, 128, 88, tool.liveAlongside).associateBy { it.view }
        assertEquals(1, thumbs.getValue(View.SIDE).preview.size)
        assertEquals(1, thumbs.getValue(View.SIDE).lines.size, "the spine")
        assertEquals(1, thumbs.getValue(View.TOP).preview.size)
        assertTrue(thumbs.getValue(View.FRONT).preview.isEmpty())
    }

    @Test
    fun aLiveStrokeThatIsNoPreviewStillDrawsAsALine() {
        val part = sheetPartOf(shipped.parts.first { it.id.value == "leg" })
        val live = LiveStroke(View.SIDE, listOf(CurvePoint(0f, 0f), CurvePoint(0.1f, 0f)))
        val thumb = viewThumbnails(SheetSelection(part, emptyMap()), View.SIDE, live, false, 128, 88).first { it.view == View.SIDE }
        assertEquals(1, thumb.lines.size)
        assertTrue(thumb.preview.isEmpty())
    }
}
