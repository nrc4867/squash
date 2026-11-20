package dev.chieppa.squash.screens.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.draw.StrokeParts.leftLeg
import dev.chieppa.squash.screens.draw.StrokeParts.torso
import dev.chieppa.squash.screens.shell.mirrorApplies
import dev.chieppa.squash.screens.shell.symmetryIn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** How far off the line of symmetry, in screen pixels, a stroke starts in the snapping tests: inside the guides' reach. */
private const val NEAR_LINE_PIXELS = 4f

/** Mirror on a part down the body's middle, its line of symmetry, and strokes that catch it. */
@OptIn(ExperimentalTestApi::class)
class MirrorSymmetryTest {
    @Test
    fun mirrorAppliesToAMiddlePartInTopAndFrontButNotSideAndToAPairedPartEverywhere() {
        val bench = StrokeBench()
        with(bench) {
            state.selectedPart.value = torso.id
            for ((view, mirrors) in listOf(View.SIDE to false, View.TOP to true, View.FRONT to true)) {
                state.activeView.value = view
                assertEquals(mirrors, state.mirrorApplies(), "torso in $view")
                assertEquals(mirrors, state.symmetryIn(view) != null, "torso's line of symmetry in $view")
            }
            state.selectedPart.value = leftLeg.id
            for (view in View.entries) {
                state.activeView.value = view
                assertTrue(state.mirrorApplies(), "a paired leg mirrors as a pair in $view")
                assertNull(state.symmetryIn(view), "a paired leg has no line of its own in $view")
            }
            assertTrue(state.mirror.value, "Mirror starts on")
        }
    }

    /** One side of the torso seen from above: from the tail end round the near flank to the head end, both ends on the axis. */
    private fun halfFromAbove(camera: dev.chieppa.squash.screens.sheet.LockedCamera, offAxis: Float = 0f): List<Offset> = List(60) { i ->
        val angle = PI * i / 59
        camera.toPixel(CurvePoint(-0.25f * cos(angle).toFloat(), 0.12f * sin(angle).toFloat() + offAxis))
    }

    private fun assertMirrored(curve: OutlineCurve) {
        val flat = curve.toPath().flatten()
        val points = flat.xs.indices.map { CurvePoint(flat.xs[it], flat.ys[it]) }
        for (p in points) {
            val image = points.minOf { q -> kotlin.math.hypot(q.x - p.x, q.y + p.y) }
            assertTrue(image < 1e-3f, "$p has a mirror image across the axis, nearest $image away")
        }
    }

    @Tag("integration")
    @Test
    fun withMirrorOnHalfATorsoDrawnFromAboveMakesTheWholeSymmetricOutline() = runComposeUiTest {
        val bench = StrokeBench()
        with(bench) {
            settled()
            state.selectedPart.value = torso.id
            val camera = camera(part(torso.id), View.TOP)
            showPanes(camera)
            shown = camera
            waitForIdle()
            stroke(halfFromAbove(camera))
            settled()
            val outline = outline(torso.id, View.TOP)
            assertTrue(outline.points.any { it.y < -0.1f }, "the far flank is drawn too")
            assertMirrored(outline)

            session.history.undo()
            settled()
            state.mirror.value = false
            stroke(halfFromAbove(camera))
            settled()
            assertFalse(outline(torso.id, View.TOP).points.any { it.y < -0.05f }, "Mirror off draws only the stroke")
        }
    }

    private fun ComposeUiTest.pressAlong(path: List<Offset>) {
        onNodeWithTag(BENCH_TAG).performMouseInput {
            moveTo(path.first())
            press()
            path.drop(1).forEach { moveTo(it) }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.letGo() {
        onNodeWithTag(BENCH_TAG).performMouseInput { release() }
        waitForIdle()
    }

    @Test
    fun aStrokeStartingNearTheLineCatchesItAndAltDrawsItFree() = runComposeUiTest {
        val bench = StrokeBench()
        with(bench) {
            settled()
            state.selectedPart.value = torso.id
            state.mirror.value = false
            val camera = camera(part(torso.id), View.TOP)
            showPanes(camera)
            shown = camera
            waitForIdle()
            val off = NEAR_LINE_PIXELS * camera.planePixel
            pressAlong(halfFromAbove(camera, off))
            val caught = assertNotNull(tool.liveStroke).points
            assertEquals(0f, caught.first().y, 1e-5f, "the start lands on the line")
            assertEquals(0f, caught.last().y, 1e-5f, "and so does the end")
            letGo()
            settled()
            session.history.undo()
            settled()

            val path = halfFromAbove(camera, off)
            var free: List<CurvePoint> = emptyList()
            val node = onNodeWithTag(BENCH_TAG)
            node.performKeyInput {
                withKeyDown(Key.AltLeft) {
                    node.performMouseInput {
                        moveTo(path.first())
                        press()
                        path.drop(1).forEach { moveTo(it) }
                    }
                    free = assertNotNull(tool.liveStroke).points
                    node.performMouseInput { release() }
                }
            }
            waitForIdle()
            assertTrue(abs(free.first().y - off) < off / 2f, "Alt held as it starts leaves the stroke where it was: ${free.first()}")
        }
    }
}
