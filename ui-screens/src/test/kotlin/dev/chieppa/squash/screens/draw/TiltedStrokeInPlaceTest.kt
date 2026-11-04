package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.draw.StrokeParts.leftEar
import dev.chieppa.squash.screens.draw.StrokeParts.rightEar
import dev.chieppa.squash.screens.draw.StrokeParts.tail
import dev.chieppa.squash.screens.edit.EditCurvesTool
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.sheet.outlineSamples
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A stroke, an oversketch and an edit through the pointer in every view of the parts whose rest axis is tilted from the body's. */
@OptIn(ExperimentalTestApi::class)
class TiltedStrokeInPlaceTest {
    /** Side, then Top, then Front, each drawn through its own pane: every stroke stays under the mesh once all three are in. */
    private fun drawsEveryView(id: PartInstance, side: PlaneStroke, top: PlaneStroke, front: PlaneStroke, frontIsWholeSection: Boolean) = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = id.id
            val before = part(id.id)
            val cameras = View.entries.associateWith { camera(before, it) }
            showPanes(cameras.getValue(View.SIDE))
            for (stroke in listOf(side, top, front)) {
                strokeIn(cameras.getValue(stroke.view), stroke.points())
                outline(id.id, stroke.view)
            }
            assertEquals(before.origin, part(id.id).origin, "the stroke's plane held still")
            assertSilhouetteUnder(id.id, side)
            assertSilhouetteUnder(id.id, top)
            assertSilhouetteUnder(id.id, front, whole = frontIsWholeSection)
        }
        bench.close()
    }

    private val earSide = PlaneStroke(View.SIDE, 0.16f, 0f, 0.2f, 0.07f)
    private val earTop = PlaneStroke(View.TOP, 0.16f, 0f, 0.2f, 0.09f)
    private val earFront = PlaneStroke(View.FRONT, 0f, 0f, 0.09f, 0.07f)
    private val tailSide = PlaneStroke(View.SIDE, 0.25f, 0f, 0.3f, 0.06f)
    private val tailTop = PlaneStroke(View.TOP, 0.25f, 0f, 0.3f, 0.08f)
    private val tailFront = PlaneStroke(View.FRONT, 0f, 0f, 0.08f, 0.06f)

    @Test
    fun theLeftEarBuildsUnderAStrokeInEveryView() = drawsEveryView(leftEar, earSide, earTop, earFront, frontIsWholeSection = true)

    @Test
    fun theRightEarBuildsUnderAStrokeInEveryView() = drawsEveryView(rightEar, earSide, earTop, earFront, frontIsWholeSection = true)

    @Test
    fun theTailBuildsUnderAStrokeInEveryViewItsFrontShapingTheCapWithinThem() =
        drawsEveryView(tail, tailSide, tailTop, tailFront, frontIsWholeSection = false)

    private fun oneStrokeLands(id: PartInstance, stroke: PlaneStroke) = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = id.id
            val camera = camera(part(id.id), stroke.view)
            showPanes(camera)
            strokeIn(camera, stroke.points())
            assertSilhouetteUnder(id.id, stroke)
        }
        bench.close()
    }

    @Test
    fun aStrokeInAnEarsSideAloneLands() = oneStrokeLands(leftEar, earSide)

    @Test
    fun aStrokeInAnEarsTopAloneLands() = oneStrokeLands(leftEar, earTop)

    @Test
    fun aStrokeInATailsSideAloneLands() = oneStrokeLands(tail, tailSide)

    @Test
    fun aStrokeInATailsTopAloneLands() = oneStrokeLands(tail, tailTop)

    @Test
    fun aBentStrokeBendsTheTailUnderIt() = oneStrokeLands(tail, PlaneStroke(View.SIDE, 0.25f, 0f, 0.3f, 0.05f, bend = 0.6f))

    @Test
    fun aBentStrokeBendsTheEarUnderIt() = oneStrokeLands(rightEar, PlaneStroke(View.SIDE, 0.16f, 0f, 0.2f, 0.05f, bend = -0.8f))

    /**
     * How far the mesh's upper edge moves when an edit lifts the upper run by [lift]: Side moves the axis with it, Top widens
     * the part about its axis, and a Front drawn alone leaves the sizes to the template, so null.
     */
    private fun meshLift(view: View, lift: Float) = when (view) {
        View.SIDE -> lift
        View.TOP -> lift / 2f
        View.FRONT -> null
    }

    /** An oversketch across the upper run of a stroke already drawn: the run moves to it in one step, and the curve and the mesh follow it. */
    private fun oversketchLands(id: PartInstance, base: PlaneStroke) = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = id.id
            val camera = camera(part(id.id), base.view)
            showPanes(camera)
            strokeIn(camera, base.points())
            val first = outline(id.id, base.view)
            val steps = session.history.steps.value.done.size
            val from = base.centerX - base.x * 0.3f
            val to = base.centerX + base.x * 0.5f
            val bow = List(40) { i ->
                val t = i / 39f
                val x = from + (to - from) * t
                val onCurve = base.y * sqrt(1f - ((x - base.centerX) / base.x).let { it * it })
                CurvePoint(x, onCurve) to base.y * 0.5f * sin(PI.toFloat() * t)
            }
            strokeIn(camera, bow.map { (on, lift) -> CurvePoint(on.x, on.y + lift) })
            assertEquals(steps + 1, session.history.steps.value.done.size, "one undo step")
            val redrawn = outline(id.id, base.view)
            assertTrue(redrawn != first, "the outline took the stroke")
            val curve = listOf(outlineSamples(redrawn).map(camera::toPixel))
            val silhouette = part(id.id).silhouette(base.view).map { path -> path.map(camera::toPixel) }
            for ((on, lift) in bow.drop(6).dropLast(6)) {
                val atPointer = distanceToPaths(camera.toPixel(CurvePoint(on.x, on.y + lift)), curve)
                assertTrue(atPointer < BENCH_SLACK_PIXELS, "${id.id} ${base.view}: the curve strays $atPointer pixels from the pointer at $on")
                val moved = meshLift(base.view, lift) ?: continue
                val atMesh = distanceToPaths(camera.toPixel(CurvePoint(on.x, on.y + moved)), silhouette)
                assertTrue(atMesh < BENCH_SLACK_PIXELS, "${id.id} ${base.view}: the mesh strays $atMesh pixels at $on")
            }
        }
        bench.close()
    }

    @Test
    fun anOversketchInAnEarsSideLandsUnderIt() = oversketchLands(leftEar, earSide)

    @Test
    fun anOversketchInAnEarsTopLandsUnderIt() = oversketchLands(rightEar, earTop)

    @Test
    fun anOversketchInATailsSideLandsUnderIt() = oversketchLands(tail, tailSide)

    @Test
    fun anOversketchInATailsTopLandsUnderIt() = oversketchLands(tail, tailTop)

    @Test
    fun anOversketchInAnEarsFrontLandsUnderThePointer() = oversketchLands(leftEar, earFront)

    @Test
    fun anOversketchInATailsFrontLandsUnderThePointer() = oversketchLands(tail, tailFront)

    /** Dragging the outline's widest anchor: it follows the pointer on screen, keeps its pixel distance in the plane, and the mesh follows. */
    private fun editLands(id: PartInstance, base: PlaneStroke) = runComposeUiTest {
        val bench = StrokeBench(tilted = true)
        with(bench) {
            settled()
            state.selectedPart.value = id.id
            val camera = camera(part(id.id), base.view)
            showPanes(camera)
            strokeIn(camera, base.points())
            working = EditCurvesTool(state, TEST_CURVE_LOOK)
            waitForIdle()
            val first = outline(id.id, base.view)
            val index = first.points.indices.filter { it % 3 == 0 }.maxBy { first.points[it].y }
            val from = camera.toPixel(first.points[index])
            val lift = 0.03f
            val to = camera.toPixel(CurvePoint(first.points[index].x, first.points[index].y + lift))
            stroke(listOf(from, (from + to) / 2f, to))
            settled()
            val moved = outline(id.id, base.view).points[index]
            val landed = camera.toPixel(moved)
            assertTrue((landed - to).getDistance() < 1f, "${id.id} ${base.view}: the anchor is at $landed, the pointer at $to")
            assertEquals(first.points[index].x, moved.x, 1e-3f)
            val part = part(id.id)
            val silhouette = part.silhouette(base.view).map { path -> path.map(camera::toPixel) }
            meshLift(base.view, lift)?.let { rise ->
                val expected = camera.toPixel(CurvePoint(first.points[index].x, first.points[index].y + rise))
                val distance = distanceToPaths(expected, silhouette)
                assertTrue(distance < BENCH_SLACK_PIXELS, "${id.id} ${base.view}: the mesh is $distance pixels from where the moved anchor sets it")
            }
        }
        bench.close()
    }

    @Test
    fun editingAnEarsSideMovesTheCurveAndMeshWithThePointer() = editLands(rightEar, earSide)

    @Test
    fun editingAnEarsTopMovesTheCurveAndMeshWithThePointer() = editLands(leftEar, earTop)

    @Test
    fun editingATailsSideMovesTheCurveAndMeshWithThePointer() = editLands(tail, tailSide)

    @Test
    fun editingATailsTopMovesTheCurveAndMeshWithThePointer() = editLands(tail, tailTop)

    @Test
    fun editingAnEarsFrontMovesTheCurveWithThePointer() = editLands(rightEar, earFront)

    @Test
    fun editingATailsFrontMovesTheCurveWithThePointer() = editLands(tail, tailFront)
}
