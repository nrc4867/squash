package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.PANE_TAG
import dev.chieppa.squash.screens.edit.RIPPLED
import dev.chieppa.squash.screens.edit.SmoothTool
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.mouseDrag
import dev.chieppa.squash.screens.edit.showPane
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import dev.chieppa.squash.screens.sheet.sheetSelection
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.stretch.gpu.GpuOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Tag
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

private const val SMOOTH_WIDTH = 1440
private const val SMOOTH_HEIGHT = 900
private const val SETTLE_MILLIS = 20_000L

/** A dog's front leg smoothed along its rippled top, through the real build and scene, with the brush over Side. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class SmoothSmokeTest {
    @Test
    fun theBrushShowsOverTheRealPaneAndItsDragRebuildsThePart() = runComposeUiTest {
        val folder = TemplateFolder.shipped()
        val state = AppState(folder, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val plan = folder.plans.first()
        val new = newCharacter(plan, folder, "Fennec")
        val session = state.open(new.document)
        new.commands.forEach(session::send)
        val socket = openSockets(session.document.value, plan, folder).first { it.name == "front-leg" }
        val parts = placeOnSocket(socket, assertNotNull(folder.template(socket.takes.first())), session.document.value)
        session.send(AddParts(parts))
        val leg = parts.first { it.side == Side.LEFT }.id
        state.selectedPart.value = leg
        session.send(SetOutline(leg, View.SIDE, RIPPLED))
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && session.assembly.output.value?.part(leg) != null }
        val rects = sheetPaneRects(SMOOTH_WIDTH, SMOOTH_HEIGHT, 2)
        val character = assertNotNull(session.assembly.output.value)
        val selection = assertNotNull(sheetSelection(character, leg, mapOf(View.SIDE to RIPPLED)))
        val input = panesInput(selection, SMOKE_LOOK, rects, SMOOTH_WIDTH, SMOOTH_HEIGHT)
        val camera = input.cameras.getValue(View.SIDE)
        val tool = SmoothTool(state, TEST_CURVE_LOOK)
        state.curves.brushSize = 120f
        state.curves.brushStrength = 1f
        showPane(tool, camera)

        val from = camera.toPixel(CurvePoint(0.6f, 0.55f))
        val to = camera.toPixel(CurvePoint(1.4f, 0.55f))
        val steps = session.history.steps.value.done.size
        mouseDrag(List(24) { from + (to - from) * (it / 23f) })
        assertEquals(steps + 1, session.history.steps.value.done.size)
        val smoothed = assertNotNull(session.document.value.character.resolved(leg)?.outlines?.get(View.SIDE))
        assertNotEquals(RIPPLED, smoothed)

        onNodeWithTag(PANE_TAG).performMouseInput { moveTo((from + to) / 2f) }
        waitForIdle()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value }
        val built = assertNotNull(session.assembly.output.value)
        val after = assertNotNull(sheetSelection(built, leg, mapOf(View.SIDE to smoothed)))
        val shown = panesInput(after, SMOKE_LOOK, rects, SMOOTH_WIDTH, SMOOTH_HEIGHT)
        val frame = sheetFrame(
            SheetInput(
                rects, SMOOTH_WIDTH, SMOOTH_HEIGHT, SMOKE_LOOK, after, shown.cameras, assertNotNull(shown.full),
                toolOverlays = mapOf(View.SIDE to tool.overlay(View.SIDE, camera)),
            ),
        )
        val errors = CopyOnWriteArrayList<String>()
        val image = renderSnapshot(frame, SMOOTH_WIDTH, SMOOTH_HEIGHT, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        keepImage(image.pixels, image.width, "smooth-sheet")
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, "the panes")
        assertNotEquals(0, rects.sumOf { image.countIn(it, TEST_CURVE_LOOK.ring) }, "the ring shows")
        assertNotEquals(0, rects.sumOf { image.countIn(it, TEST_CURVE_LOOK.affected) }, "and the stretch under it")
    }
}
