package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.build.ShapeSource
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.chosenPart
import dev.chieppa.squash.screens.draw.WorkspaceTools
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_R
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.restSurface
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.screens.surface.paint.rememberBrushTool
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1000
private const val HEIGHT = 860
private const val STRIP_HEIGHT = 60
private const val PANES_HEIGHT = HEIGHT - STRIP_HEIGHT

/** The places around a part a test looks for one that shows. */
private val AROUND = List(16) { it / 16f }

/** How many pixels a drag moves per step. */
private const val DRAG_STEP = 8f

/** The pixels between looks up the free pane for a part. */
private const val SCAN_STEP = 6

/** How far into a part a drag goes past the pixel it first shows at. */
private const val INTO_PART = 24f

/**
 * The Color stage's strip and panes on the fennec, with Brush armed or Select: a part chosen with Select holds the brush to it, the rest of the
 * character neither taking paint nor the choice, and Select picks without painting.
 */
@Tag("integration")
@OptIn(ExperimentalTestApi::class)
class PaintSelectFlowTest {
    private val work = TestWork()
    private val noSurface: SceneSurfaceContent = { _, _ -> }
    private var seen: SeenFree? = null

    private fun ComposeUiTest.colorStage(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.layout.value = SheetLayout.FULL_FIRST
        state.armTool(BuildTool.BRUSH)
        val session = assertNotNull(state.session.value)
        setStretchContent {
            CompositionLocalProvider(LocalSceneSurface provides noSurface) {
                Column(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                    Box(Modifier.fillMaxWidth().size(WIDTH.dp, STRIP_HEIGHT.dp)) { WorkspaceTools(state) }
                    val tool = armedTool(state)
                    val shown = remember(tool) { SeenFree(tool).also { seen = it } }
                    SheetPanes(state, Modifier.size(WIDTH.dp, PANES_HEIGHT.dp), tool = shown)
                }
            }
        }
        settle(session)
        settle(work) { seen?.view != null }
        return state
    }

    /** The tool the workspace hands the panes in Color: Brush, or Select with no tool armed. */
    @Composable
    private fun armedTool(state: AppState): PaneTool {
        val armed by state.tool.collectAsState()
        val brush = rememberBrushTool(state)
        val select = rememberSelectPartTool(state)
        return if (armed == BuildTool.BRUSH) brush else select
    }

    private fun ComposeUiTest.settle(session: CharacterSession) {
        waitForIdle()
        settle(work) { session.graph.settled.value && session.assembly.output.value != null && session.bodyColor.output.value != null }
        waitForIdle()
    }

    /** The pixels of the free pane showing [part] at the places of [us] and [vs], each with what the pick finds there. */
    private fun AppState.showing(part: PartId, us: List<Float>, vs: List<Float>): List<Pair<Offset, SurfaceHit>> {
        val view = assertNotNull(seen?.view)
        val session = assertNotNull(session.value)
        val character = assertNotNull(session.assembly.output.value)
        val placed = assertNotNull(character.part(part))
        val patch = placed.surface.patches.first { it.slices != null }
        return us.flatMap { u ->
            vs.mapNotNull { v ->
                val pixel = view.toPixel(placed.origin + placed.surface.shown(patch.positionAt(u, v))) ?: return@mapNotNull null
                if (pixel.x !in 0f..view.width.toFloat() || pixel.y !in 0f..view.height.toFloat()) return@mapNotNull null
                surfaceHit(character, pixel, view, session.restSurface)?.takeIf { it.part == part }?.let { pixel to it }
            }
        }
    }

    /** Whether the press at [pixel] finds [part] anywhere through the free pane, in front or behind. */
    private fun AppState.findsAt(pixel: Offset, part: PartId): Boolean {
        val session = assertNotNull(session.value)
        return surfaceHit(assertNotNull(session.assembly.output.value), pixel, assertNotNull(seen?.view), session.restSurface, only = part) != null
    }

    /** Straight up the free pane from [from], the first pixel showing [part], and some way on into it. */
    private fun AppState.upInto(part: PartId, from: Offset): Offset {
        val session = assertNotNull(session.value)
        val character = assertNotNull(session.assembly.output.value)
        val view = assertNotNull(seen?.view)
        val steps = generateSequence(from) { it - Offset(0f, SCAN_STEP.toFloat()) }.takeWhile { it.y > 0f }
        val first = assertNotNull(steps.firstOrNull { surfaceHit(character, it, view, session.restSurface)?.part == part }, "${part.value} shows above $from")
        return first - Offset(0f, INTO_PART)
    }

    private fun ComposeUiTest.drag(from: Offset, to: Offset) {
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(from)
            press()
            val steps = ((to - from).getDistance() / DRAG_STEP).toInt().coerceAtLeast(1)
            for (i in 1..steps) moveTo(from + (to - from) * (i.toFloat() / steps))
            release()
        }
    }

    private fun ComposeUiTest.press(at: Offset) = drag(at, at)

    private fun CharacterSession.strokesOn(part: PartId): List<BrushStroke> = document.value.character.resolved(assertNotNull(document.value.character.part(part))).surface.paint.strokes

    /** The parts the stroke [id] is cut onto. */
    private fun CharacterSession.cutOnto(id: BrushStrokeId): Set<PartId> = document.value.character.parts
        .flatMap { heldColor(it.id).output.value?.cuts.orEmpty() }
        .filter { (it.source as? ShapeSource.Stroke)?.id == id }
        .mapTo(hashSetOf()) { it.part }

    @Test
    fun aPartChosenWithSelectTakesTheBrushStrokeInAColorItsBaseDoesNotShow() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = colorStage()
        val session = assertNotNull(state.session.value)
        assertEquals(Stage.COLOR, state.stage.value)
        onNodeWithTag(ShellTags.tool(BuildTool.NONE)).performClick()
        waitForIdle()
        assertEquals(BuildTool.NONE, state.tool.value, "Select puts the brush down")
        val (at, _) = state.showing(TORSO, listOf(0.5f), AROUND).first()
        press(at)
        waitForIdle()
        assertEquals(TORSO, state.selectedPart.value)
        assertTrue(state.partChosen.value, "the press chooses it")

        onNodeWithTag(ShellTags.tool(BuildTool.BRUSH)).performClick()
        waitForIdle()
        assertEquals(TORSO, state.selectedPart.value, "arming the brush keeps the choice")
        assertTrue(state.partChosen.value)
        val before = session.strokesOn(TORSO).size
        drag(at, at + Offset(DRAG_STEP * 4f, 0f))
        settle(session)
        val stroke = session.strokesOn(TORSO).last()
        assertEquals(before + 1, session.strokesOn(TORSO).size, "the stroke lands on the chosen part")
        assertEquals(setOf(TORSO), stroke.points.map { it.part }.toSet())
        val base = session.document.value.character.resolved(assertNotNull(session.document.value.character.part(TORSO))).surface.baseSlot
        assertNotEquals(base, stroke.slot, "with no slot picked it paints one the part's base does not show")
        assertEquals(setOf(TORSO), session.cutOnto(stroke.id), "and is cut onto the part alone")
        runOnIdle { state.undo() }
        settle(session)
        assertEquals(before, session.strokesOn(TORSO).size, "one undo takes it")
        assertEquals(emptySet(), session.cutOnto(stroke.id))
    }

    @Test
    fun paintingNearAnotherPartNeitherChoosesItNorSpillsOntoIt() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = colorStage()
        val session = assertNotNull(state.session.value)
        runOnIdle { state.selectPart(TORSO) }
        waitForIdle()
        val legs = listOf(FRONT_LEG_L, FRONT_LEG_R)
        val (leg, start) = legs.firstNotNullOf { id -> state.showing(id, listOf(0.35f), AROUND).firstOrNull()?.let { id to it.first } }
        val legStrokes = legs.associateWith { session.strokesOn(it).size }
        val steps = session.history.steps.value.done.size

        val (alone, _) = assertNotNull(state.showing(leg, listOf(0.6f, 0.75f, 0.9f), AROUND).firstOrNull { !state.findsAt(it.first, TORSO) }, "a place on the leg with no torso behind it")
        drag(alone, alone + Offset(DRAG_STEP * 3f, 0f))
        settle(session)
        assertEquals(steps, session.history.steps.value.done.size, "a stroke on the leg alone lays nothing down")
        assertEquals(TORSO, state.selectedPart.value, "nor chooses the leg")

        drag(start, state.upInto(TORSO, start))
        settle(session)
        assertEquals(TORSO, state.selectedPart.value, "the choice stays on the torso")
        val stroke = session.strokesOn(TORSO).last()
        assertEquals(setOf(TORSO), stroke.points.map { it.part }.toSet(), "a drag from the leg into the torso anchors on the torso alone")
        assertEquals(legStrokes, legs.associateWith { session.strokesOn(it).size }, "the legs hold no stroke")
        assertEquals(setOf(TORSO), session.cutOnto(stroke.id), "and take none of its paint")
        runOnIdle { state.undo() }
        settle(session)
        assertEquals(steps, session.history.steps.value.done.size, "one undo takes it")
    }

    @Test
    fun selectPicksWithoutPaintingAndLettingTheChoiceGoFreesTheBrush() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = colorStage()
        val session = assertNotNull(state.session.value)
        assertFalse(state.partChosen.value, "opening chooses nothing")
        runOnIdle { state.putToolDown() }
        waitForIdle()
        val (leg, at) = listOf(FRONT_LEG_L, FRONT_LEG_R).firstNotNullOf { id -> state.showing(id, listOf(0.5f), AROUND).firstOrNull()?.let { id to it.first } }
        val document = session.document.value
        val steps = session.history.steps.value.done.size
        drag(at, at + Offset(DRAG_STEP * 4f, 0f))
        settle(session)
        assertEquals(leg, state.selectedPart.value, "Select picks the part")
        assertTrue(state.partChosen.value)
        assertEquals(document, session.document.value, "and paints nothing")
        assertEquals(steps, session.history.steps.value.done.size, "so there is nothing to undo")

        press(Offset(2f, 2f))
        waitForIdle()
        assertFalse(state.partChosen.value, "a press off the character lets the choice go")
        assertEquals(leg, state.selectedPart.value, "and keeps the part selected for the panes")

        runOnIdle { state.armTool(BuildTool.BRUSH) }
        waitForIdle()
        val (torso, _) = state.showing(TORSO, listOf(0.5f), AROUND).first()
        val before = session.strokesOn(TORSO).size
        drag(torso, torso + Offset(DRAG_STEP * 4f, 0f))
        settle(session)
        assertEquals(before + 1, session.strokesOn(TORSO).size, "with nothing chosen the brush paints whatever part is under it")
        assertNull(state.chosenPart())
        runOnIdle { state.undo() }
        settle(session)
        assertEquals(before, session.strokesOn(TORSO).size)
    }
}
