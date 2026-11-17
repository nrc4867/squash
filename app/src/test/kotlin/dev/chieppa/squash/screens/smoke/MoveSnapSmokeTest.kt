package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.edit.PANE_TAG
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.showPane
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.move.LockedHandleView
import dev.chieppa.squash.screens.move.MoveTool
import dev.chieppa.squash.screens.move.SocketHandle
import dev.chieppa.squash.screens.move.TEST_DRAG_GUIDE_LOOK
import dev.chieppa.squash.screens.move.moveHandles
import dev.chieppa.squash.screens.move.startPlace
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import dev.chieppa.squash.screens.sheet.sheetSelection
import dev.chieppa.stretch.gpu.GpuOptions
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val SNAP_WIDTH = 1440
private const val SNAP_HEIGHT = 900

/** How far off the torso's middle ring, in pixels, the drag is held while the image is taken. */
private const val OFF_RING = 4f

/** The shipped fennec's left front leg dragged near its torso's middle in Side, held there while the panes render its snap marker. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class MoveSnapSmokeTest {
    private val work = TestWork()

    @Test
    fun aSnappedSocketShowsTheRingItLandsOnWhileItDrags() = runComposeUiTest {
        val state = AppState(TemplateFolder.shipped(), work)
        val session = state.open(savedFennec())
        state.selectedPart.value = TORSO
        settle(work, state) { session.assembly.output.value?.part(FRONT_LEG_L) != null }
        val character = assertNotNull(session.assembly.output.value)
        val selection = assertNotNull(sheetSelection(character, TORSO, emptyMap()))
        val rects = sheetPaneRects(SNAP_WIDTH, SNAP_HEIGHT, 2)
        val input = panesInput(selection, SMOKE_LOOK, rects, SNAP_WIDTH, SNAP_HEIGHT)
        val side = assertNotNull(input.cameras[View.SIDE])
        val tool = MoveTool(state, SMOKE_LOOK, TEST_CURVE_LOOK, TEST_DRAG_GUIDE_LOOK)
        showPane(tool, side)
        val socket = moveHandles(character, null).filterIsInstance<SocketHandle>().first { it.child == FRONT_LEG_L }
        val start = assertNotNull(socket.startPlace(LockedHandleView(side)))
        val dot = side.toPixel(socket.position)
        val near = side.toPixel(assertNotNull(assertNotNull(character.part(TORSO)).pointOn(0.5f, start.v))) + Offset(OFF_RING, 0f)
        val pane = onNodeWithTag(PANE_TAG)
        pane.performMouseInput {
            moveTo(dot)
            press()
            (1..10).forEach { moveTo(dot + (near - dot) * (it / 10f)) }
        }
        waitForIdle()
        settle(work, state)
        val shown = assertNotNull(sheetSelection(assertNotNull(session.assembly.output.value), TORSO, emptyMap()))
        val overlays = input.cameras.mapValues { (view, camera) -> tool.overlay(view, camera) }
        val frame = sheetFrame(SheetInput(rects, SNAP_WIDTH, SNAP_HEIGHT, SMOKE_LOOK, shown, input.cameras, input.full, toolOverlays = overlays))
        val errors = CopyOnWriteArrayList<String>()
        val image = renderSnapshot(frame, SNAP_WIDTH, SNAP_HEIGHT, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        keepImage(image.pixels, image.width, "move-snap")
        pane.performMouseInput { release() }
        assertEquals(emptyList(), errors)
        assertNotEquals(0, image.countIn(rects[SheetPane.SIDE.ordinal], TEST_DRAG_GUIDE_LOOK.caught), "the snap marker shows in Side")
    }
}
