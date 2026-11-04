package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.GrainField
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 60_000L
private const val PANES_WIDTH = 1000
private const val PANES_HEIGHT = 800
private const val DRAG_STEP = 8f
private const val OFF_THE_CHARACTER = 3f
private const val SEVERAL_ROOTS = 5

private val shipped = TemplateFolder.shipped()
private val ALONG = List(9) { 0.1f + it * 0.1f }
private val AROUND = List(16) { it / 16f }

/** A tool that shows a test the free pane it was handed, so a test can find what shows where. */
private class FurSeenFree(private val tool: PaneTool) : PaneTool by tool {
    var view: FullView? = null

    override fun fullOverlay(view: FullView): ViewportOverlay {
        this.view = view
        return tool.fullOverlay(view)
    }
}

/** Fur stroke and Comb driven with the pointer through the panes as the app composes them, in the Sheet and in Full first's free pane. */
@OptIn(ExperimentalTestApi::class)
class FurPointerFlowTest {
    private val noSurface: SceneSurfaceContent = { _, _ -> }
    private var seen: FurSeenFree? = null

    private fun opened(layout: SheetLayout, selected: PartId = TORSO) = AppState(shipped).also {
        it.open(savedFennec())
        it.layout.value = layout
        it.selectedPart.value = selected
    }

    private fun ComposeUiTest.show(state: AppState, makeTool: @Composable () -> PaneTool) {
        setStretchContent {
            CompositionLocalProvider(LocalSceneSurface provides noSurface) {
                Box(Modifier.size(PANES_WIDTH.dp, PANES_HEIGHT.dp)) {
                    val tool = makeTool()
                    val shown = remember(tool) { FurSeenFree(tool).also { seen = it } }
                    SheetPanes(state, Modifier.size(PANES_WIDTH.dp, PANES_HEIGHT.dp), tool = shown)
                }
            }
        }
        settle(assertNotNull(state.session.value))
        waitUntil(timeoutMillis = SETTLE_MILLIS) { seen?.view != null }
    }

    private fun ComposeUiTest.settle(session: CharacterSession) {
        waitForIdle()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && session.assembly.output.value != null }
        waitForIdle()
    }

    /** The pixel of the free pane showing [part] nearest the middle of it, as the pick finds it, and the one furthest from there. */
    private fun AppState.across(part: PartId): Pair<Offset, Offset> {
        val view = assertNotNull(seen?.view)
        val character = assertNotNull(assertNotNull(session.value).assembly.output.value)
        val placed = assertNotNull(character.part(part))
        val patch = placed.surface.patches.first { it.slices != null }
        val pixels = ALONG.flatMap { u ->
            AROUND.mapNotNull { v ->
                val pixel = view.toPixel(placed.origin + placed.surface.shown(patch.positionAt(u, v))) ?: return@mapNotNull null
                if (pixel.x !in 0f..view.width.toFloat() || pixel.y !in 0f..view.height.toFloat()) return@mapNotNull null
                surfaceHit(character, pixel, view)?.takeIf { it.part == part }?.let { pixel }
            }
        }
        assertTrue(pixels.isNotEmpty(), "${part.value} shows in the free pane")
        val middle = pixels.reduce { sum, pixel -> sum + pixel } / pixels.size.toFloat()
        val start = pixels.minBy { (it - middle).getDistance() }
        return start to pixels.maxBy { (it - start).getDistance() }
    }

    private fun ComposeUiTest.drag(pane: SheetPane, from: Offset, to: Offset) {
        onNodeWithTag(sheetPaneTag(pane)).performMouseInput {
            moveTo(from)
            press()
            val steps = ((to - from).getDistance() / DRAG_STEP).toInt().coerceAtLeast(1)
            for (i in 1..steps) moveTo(from + (to - from) * (i.toFloat() / steps))
            release()
        }
        waitForIdle()
    }

    private fun AppState.work(part: PartId): SurfaceWork = assertNotNull(assertNotNull(session.value).document.value.character.resolved(part)).surface

    private fun List<Mark>.fur() = filter { it.kind == MarkKind.FUR }

    private fun ComposeUiTest.assertADragLaysRootsAsOneStep(state: AppState, pane: SheetPane, from: Offset, to: Offset) {
        val session = assertNotNull(state.session.value)
        val before = state.work(TORSO).marks
        val steps = session.history.steps.value.done.size
        drag(pane, from, to)
        val added = state.work(TORSO).marks - before.toSet()
        assertTrue(added.size >= SEVERAL_ROOTS, "the drag laid rows of strokes: ${added.size}")
        assertTrue(added.all { it.kind == MarkKind.FUR && it.points.single().part == TORSO })
        assertEquals(steps + 1, session.history.steps.value.done.size, "one undo step")
        session.history.undo()
        assertEquals(before, state.work(TORSO).marks)
    }

    @Test
    fun aFurStrokeDragInFullFirstsFreePaneLands() = runDesktopComposeUiTest(PANES_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.FULL_FIRST)
        show(state) { rememberFurStrokeTool(state) }
        val (from, to) = state.across(TORSO)
        assertADragLaysRootsAsOneStep(state, SheetPane.FULL, from, to)
    }

    @Test
    fun aFurStrokeDragInTheSheetsFullLands() = runDesktopComposeUiTest(PANES_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.SHEET)
        show(state) { rememberFurStrokeTool(state) }
        val (from, to) = state.across(TORSO)
        assertADragLaysRootsAsOneStep(state, SheetPane.FULL, from, to)
    }

    @Test
    fun aPressOffTheCharacterLaysNothing() = runDesktopComposeUiTest(PANES_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.FULL_FIRST)
        show(state) { rememberFurStrokeTool(state) }
        val session = assertNotNull(state.session.value)
        val steps = session.history.steps.value.done.size
        val corner = Offset(OFF_THE_CHARACTER, OFF_THE_CHARACTER)
        drag(SheetPane.FULL, corner, corner + Offset(0f, OFF_THE_CHARACTER * 2f))
        assertEquals(steps, session.history.steps.value.done.size)
    }

    @Test
    fun aCombDragStoresAnchorVectorsThatTurnTheGrainAsOneStep() = runDesktopComposeUiTest(PANES_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.FULL_FIRST, TAIL)
        show(state) { rememberCombTool(state) }
        val session = assertNotNull(state.session.value)
        val (from, to) = state.across(TAIL)
        val placed = assertNotNull(session.assembly.output.value?.part(TAIL))
        val before = state.work(TAIL).grain
        val steps = session.history.steps.value.done.size
        drag(SheetPane.FULL, from, to)
        val combs = assertNotNull(state.work(TAIL).grain).combs
        assertTrue(combs.isNotEmpty(), "the drag left combs")
        assertTrue(combs.all { it.du != 0f || it.dv != 0f })
        assertEquals(steps + 1, session.history.steps.value.done.size, "one undo step")
        val first = combs.first()
        val patch = placed.surface.patches.first { it.name == first.patch }
        assertNotEquals(GrainField(placed.template.grain, before).directionAt(patch, first.u, first.v), GrainField(placed.template.grain, state.work(TAIL).grain).directionAt(patch, first.u, first.v))
        session.history.undo()
        assertEquals(before, state.work(TAIL).grain)
    }
}
