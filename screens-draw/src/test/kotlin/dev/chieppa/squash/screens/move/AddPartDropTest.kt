package dev.chieppa.squash.screens.move

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.edit.PANE_TAG
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.library.TemplateDrag
import dev.chieppa.squash.screens.openSocketsNow
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneViewport
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import dev.chieppa.stretch.frame.DragAndDropHost
import dev.chieppa.stretch.frame.LocalDragAndDrop
import dev.chieppa.stretch.frame.OverlayDot
import dev.chieppa.stretch.frame.dragSource
import dev.chieppa.stretch.test.setStretchContent
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SOURCE_TAG = "source"
private const val LIFTED_TAG = "lifted"

/** How far from a dot the pointer is let go or rests, inside the grab reach. */
private val OFF_DOT = Offset(7f, 4f)

@OptIn(ExperimentalTestApi::class)
class AddPartDropTest {
    private val work = TestWork()

    private val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))

    /**
     * The fennec bench with Add part on its Side pane, a tile of [template] to drag, or of the tail socket's first template, and the
     * dots it shows.
     */
    private inner class Drop(test: ComposeUiTest, template: String? = null) {
        val bench = LegBench(folder, work)
        val camera: LockedCamera
        val view: LockedHandleView
        val add = AddPartTool(bench.state, SMOKE_LOOK, TEST_CURVE_LOOK, TEST_DRAG_GUIDE_LOOK)
        val tail: SocketHandle
        val tailSocket: OpenSocket
        val refusing: SocketHandle
        val drag: TemplateDrag
        val leftLeg: SocketHandle

        init {
            bench.settleIn(test)
            camera = bench.camera()
            view = LockedHandleView(camera)
            val sockets = bench.state.openSocketsNow()
            val dots = moveHandles(bench.character, null).filterIsInstance<SocketHandle>().filter { it.child == null }
            tail = dots.first { it.socket == "tail" }
            tailSocket = assertNotNull(socketOf(tail, sockets))
            drag = TemplateDrag(template?.let(::TemplateId) ?: tailSocket.takes.first())
            leftLeg = moveHandles(bench.character, null).filterIsInstance<SocketHandle>().first { it.child == bench.leg }
            refusing = assertNotNull(
                dots.firstOrNull { dot -> socketOf(dot, sockets)?.let { drag.template !in it.takes } == true && inPane(dot) },
                "an open socket that does not take the tail's template",
            )
        }

        fun pixel(dot: SocketHandle): Offset = view.toPixel(dot.position)

        private fun inPane(dot: SocketHandle) = pixel(dot).let { it.x in 0f..camera.width.toFloat() && it.y in 0f..camera.height.toFloat() }

        /** The tool's dots in the Side pane at [dot]'s pixel. */
        fun dotsAt(dot: SocketHandle): List<OverlayDot> =
            add.overlay(View.SIDE, camera).dots.filter { (it.center - pixel(dot)).getDistance() < 0.5f }
    }

    /** A source tile above the pane, both in one drag and drop host. */
    private fun ComposeUiTest.show(drop: Drop) {
        setStretchContent {
            DragAndDropHost {
                drop.add.drop.drags = LocalDragAndDrop.current
                with(density) {
                    Column {
                        Box(Modifier.size(40.dp).testTag(SOURCE_TAG).dragSource({ drop.drag }) { Box(Modifier.size(40.dp).testTag(LIFTED_TAG)) })
                        Box(Modifier.size(drop.camera.width.toDp(), drop.camera.height.toDp())) {
                            PaneViewport(drop.camera.axes.bodyViewOf(drop.camera.view), null, drop.add.pointerInput(drop.camera.view, drop.camera).testTag(PANE_TAG))
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.centreOf(tag: String): Offset {
        val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return with(density) { Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx()) }
    }

    /** [pixel] of the pane, in the root's pixels. */
    private fun ComposeUiTest.inRoot(pixel: Offset): Offset {
        val pane = onNodeWithTag(PANE_TAG).getUnclippedBoundsInRoot()
        return with(density) { Offset(pane.left.toPx(), pane.top.toPx()) } + pixel
    }

    /** The window's own root; the pane's corners float in popups with roots of their own. */
    private fun ComposeUiTest.windowRoot() = onAllNodes(isRoot()).onFirst()

    /** Presses the source tile and drags it to [pixel] of the pane, without letting go. */
    private fun ComposeUiTest.dragTo(pixel: Offset) {
        val start = centreOf(SOURCE_TAG)
        val to = inRoot(pixel)
        windowRoot().performMouseInput {
            moveTo(start)
            press()
            moveTo(start + Offset(0f, 20f))
            moveTo(start + Offset(0f, 60f))
            moveTo(to)
        }
    }

    @Test
    fun whileATileDragsTheDotsThatTakeItAreLitAndTheRestFaintUntilItEnds() = runComposeUiTest {
        val drop = Drop(this)
        show(drop)
        dragTo(Offset(4f, 4f))
        val lit = drop.dotsAt(drop.tail)
        assertTrue(lit.any { it.radius == SMOKE_LOOK.pickedDotRadius && it.color == SMOKE_LOOK.active }, "the tail's dot is large and accent: $lit")
        val faint = drop.dotsAt(drop.refusing)
        assertTrue(faint.isNotEmpty() && faint.all { it.color.alpha < 1f && it.radius <= SMOKE_LOOK.dotRadius + SMOKE_LOOK.dotEdge }, "a refusing dot is faint: $faint")
        windowRoot().performMouseInput { release() }
        waitForIdle()
        assertEquals(SMOKE_LOOK.dotFill, drop.dotsAt(drop.tail).last().color, "the tail's dot is back to plain")
        assertEquals(SMOKE_LOOK.dotRadius, drop.dotsAt(drop.tail).last().radius)
        assertTrue(drop.dotsAt(drop.refusing).all { it.color.alpha == 1f }, "and the refusing one is no longer faint")
    }

    @Test
    fun nearADotThatTakesItTheTileSnapsBesideTheRingedDot() = runComposeUiTest {
        val drop = Drop(this)
        show(drop)
        dragTo(drop.pixel(drop.tail) + OFF_DOT)
        val snap = assertNotNull(drop.add.drop.snapAt(drop.tail, drop.view, SMOKE_LOOK))
        val lifted = onNodeWithTag(LIFTED_TAG).getUnclippedBoundsInRoot()
        val corner = with(density) { Offset(lifted.left.toPx(), lifted.bottom.toPx()) }
        val wanted = inRoot(snap.at)
        assertTrue((corner - wanted).getDistance() < 1.5f, "the tile's corner $corner sits at $wanted")
        val ring = drop.add.overlay(View.SIDE, drop.camera).lines.filter { it.color == SMOKE_LOOK.ring }
        assertTrue(ring.isNotEmpty(), "the snapped dot wears a ring")
        windowRoot().performMouseInput { release() }
    }

    @Test
    fun nearADotThatRefusesItTheTileFollowsThePointer() = runComposeUiTest {
        val drop = Drop(this)
        show(drop)
        val pointer = drop.pixel(drop.refusing) + OFF_DOT
        dragTo(pointer)
        val centre = centreOf(LIFTED_TAG)
        assertTrue((centre - inRoot(pointer)).getDistance() < 1.5f, "the tile stays under the pointer")
        assertTrue(drop.add.overlay(View.SIDE, drop.camera).lines.none { it.color == SMOKE_LOOK.ring }, "and no dot is ringed")
        windowRoot().performMouseInput { release() }
    }

    @Test
    fun aDropWhileSnappedAddsOnThatSocketThoughThePointerIsOffItsDot() = runComposeUiTest {
        val drop = Drop(this)
        show(drop)
        val steps = drop.bench.steps()
        dragTo(drop.pixel(drop.tail) + OFF_DOT)
        windowRoot().performMouseInput { release() }
        waitForIdle()
        assertEquals(steps + 1, drop.bench.steps(), "adding is one step")
        val added = drop.bench.session.document.value.character.parts.filter { it.socket == SocketRef(drop.tailSocket.parent, drop.tailSocket.name) }
        assertTrue(added.isNotEmpty(), "a part sits on the tail socket")
        onNodeWithTag(LIFTED_TAG).assertDoesNotExist()
    }

    @Test
    fun aLimbDraggedOverAFilledLegSocketLightsItsDotAndADropThereSwapsThePairInOneStep() = runComposeUiTest {
        val drop = Drop(this, "leg-bird")
        show(drop)
        val steps = drop.bench.steps()
        dragTo(drop.pixel(drop.leftLeg) + OFF_DOT)
        val lit = drop.dotsAt(drop.leftLeg)
        assertTrue(lit.any { it.radius == SMOKE_LOOK.pickedDotRadius && it.color == SMOKE_LOOK.active }, "the filled leg socket's dot is large and accent: $lit")
        windowRoot().performMouseInput { release() }
        waitForIdle()
        assertEquals(steps + 1, drop.bench.steps(), "replacing is one step")
        val legs = drop.bench.session.document.value.character.parts.filter { it.socket?.socket == "front-leg" }
        assertEquals(listOf("leg-bird", "leg-bird"), legs.map { it.template.id.value })
        assertTrue(drop.bench.state.selectedPart.value in legs.map { it.id }, "the swapped leg is selected")
        drop.bench.session.history.undo()
        assertEquals(listOf("leg", "leg"), drop.bench.session.document.value.character.parts.filter { it.socket?.socket == "front-leg" }.map { it.template.id.value })
    }

    @Test
    fun aTemplateNoFilledSocketCanTakeLightsNoDotOnThem() = runComposeUiTest {
        val drop = Drop(this)
        show(drop)
        dragTo(Offset(4f, 4f))
        val there = drop.dotsAt(drop.leftLeg)
        assertTrue(there.none { it.color == SMOKE_LOOK.active }, "a tail cannot stand in for a leg, so no dot there is lit: $there")
        windowRoot().performMouseInput { release() }
    }
}
