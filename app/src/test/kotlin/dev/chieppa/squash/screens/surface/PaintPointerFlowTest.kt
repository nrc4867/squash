package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ShadingMode
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.fennec.CREAM
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_R
import dev.chieppa.squash.screens.fennec.FUR
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.sheet.shadingPreviewTag
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.surface.paint.ColorDrawerContent
import dev.chieppa.squash.screens.surface.paint.FullSight
import dev.chieppa.squash.screens.surface.paint.PaintTags
import dev.chieppa.squash.screens.surface.paint.SurfaceHit
import dev.chieppa.squash.screens.surface.paint.rememberBrushTool
import dev.chieppa.squash.screens.surface.paint.rememberFillTool
import dev.chieppa.squash.screens.surface.paint.surfaceHit
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
private const val DRAWER_WIDTH = 300

/** The pixels between looks up the free pane for a part. */
private const val SCAN_STEP = 6

/** How far into a part a drag goes past the pixel it first shows at. */
private const val INTO_PART = 24f

/** The places around a part a test looks for one that shows. */
private val AROUND = List(16) { it / 16f }

/** How many pixels a drag moves per step. */
private const val DRAG_STEP = 8f

private val shipped = TemplateFolder.shipped()

/** A tool that shows a test the free pane it was handed, so a test can find what shows where. */
private class SeenFree(private val tool: PaneTool) : PaneTool by tool {
    var view: FullView? = null

    override fun fullOverlay(view: FullView): ViewportOverlay {
        this.view = view
        return tool.fullOverlay(view)
    }
}

/** The paint tools and the Color drawer on the fennec, driven with the pointer through the panes as the app composes them. */
@OptIn(ExperimentalTestApi::class)
class PaintPointerFlowTest {
    private val noSurface: SceneSurfaceContent = { _, _ -> }
    private var seen: SeenFree? = null

    private fun opened(layout: SheetLayout) = AppState(shipped).also {
        it.open(savedFennec())
        it.layout.value = layout
    }

    private fun ComposeUiTest.show(state: AppState, makeTool: @Composable () -> PaneTool) {
        val session = assertNotNull(state.session.value)
        setStretchContent {
            CompositionLocalProvider(LocalSceneSurface provides noSurface) {
                Row(Modifier.size((PANES_WIDTH + DRAWER_WIDTH).dp, PANES_HEIGHT.dp)) {
                    val tool = makeTool()
                    val shown = remember(tool) { SeenFree(tool).also { seen = it } }
                    Column(Modifier.width(DRAWER_WIDTH.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { ColorDrawerContent(state, session) }
                    Box(Modifier.size(PANES_WIDTH.dp, PANES_HEIGHT.dp)) { SheetPanes(state, Modifier.size(PANES_WIDTH.dp, PANES_HEIGHT.dp), tool = shown) }
                }
            }
        }
        settle(session)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { seen?.view != null }
    }

    private fun ComposeUiTest.settle(session: CharacterSession) {
        waitForIdle()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && session.assembly.output.value != null && session.bodyColor.output.value != null }
        waitForIdle()
    }

    /** The pixels of the free pane showing [part] at the places of [us] and [vs], each with what the pick finds there. */
    private fun AppState.showing(part: PartId, us: List<Float>, vs: List<Float>): List<Pair<Offset, SurfaceHit>> {
        val view = assertNotNull(seen?.view)
        val character = assertNotNull(assertNotNull(session.value).assembly.output.value)
        val placed = assertNotNull(character.part(part))
        val patch = placed.surface.patches.first { it.slices != null }
        val sight = FullSight(view)
        return us.flatMap { u ->
            vs.mapNotNull { v ->
                val pixel = view.toPixel(placed.origin + placed.surface.shown(patch.positionAt(u, v))) ?: return@mapNotNull null
                val inside = pixel.x in 0f..view.width.toFloat() && pixel.y in 0f..view.height.toFloat()
                if (!inside) return@mapNotNull null
                surfaceHit(character, pixel, sight)?.takeIf { it.part == part }?.let { pixel to it }
            }
        }
    }

    /** Straight up the free pane from [from], the first pixel showing [part], and some way on into it. */
    private fun AppState.upInto(part: PartId, from: Offset): Offset {
        val view = assertNotNull(seen?.view)
        val character = assertNotNull(assertNotNull(session.value).assembly.output.value)
        val sight = FullSight(view)
        val steps = generateSequence(from) { it - Offset(0f, SCAN_STEP.toFloat()) }.takeWhile { it.y > 0f }
        val first = assertNotNull(steps.firstOrNull { surfaceHit(character, it, sight)?.part == part }, "${part.value} shows above $from")
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

    private fun ComposeUiTest.press(at: Offset) {
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(at)
            press()
            release()
        }
    }

    private fun CharacterSession.strokesOn(part: PartId): List<BrushStroke> = assertNotNull(document.value.character.resolved(part)).surface.paint.strokes

    @Test
    fun aBrushStrokeLandsFromFullFirstsFreePane() = runDesktopComposeUiTest(PANES_WIDTH + DRAWER_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.FULL_FIRST)
        val session = assertNotNull(state.session.value)
        show(state) { rememberBrushTool(state) }
        val (start, hit) = state.showing(TORSO, listOf(0.5f), AROUND).first()
        val before = session.strokesOn(TORSO).size
        drag(start, start + Offset(DRAG_STEP * 4f, 0f))
        settle(session)
        val strokes = session.strokesOn(TORSO)
        assertEquals(before + 1, strokes.size, "one stroke, held by the part it began on")
        val stroke = strokes.last()
        assertEquals(hit.u, stroke.points.first().u, 0.001f, "it starts under the pointer")
        assertTrue(stroke.points.size > 2, "and takes anchors along the drag: ${stroke.points.size}")
        assertTrue(assertNotNull(session.heldColor(TORSO).output.value).cuts.any { it.part == TORSO }, "it is cut onto the surface")
        runOnIdle { state.undo() }
        settle(session)
        assertEquals(before, session.strokesOn(TORSO).size, "one undo takes all of it")
    }

    @Test
    fun aDragFromALegUpOntoTheTorsoLandsOnBothAndMirrors() = runDesktopComposeUiTest(PANES_WIDTH + DRAWER_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.FULL_FIRST)
        val session = assertNotNull(state.session.value)
        state.paint.softness = 0.8f
        show(state) { rememberBrushTool(state) }
        val (legPart, place) = listOf(FRONT_LEG_L, FRONT_LEG_R).firstNotNullOf { id -> state.showing(id, listOf(0.35f), AROUND).firstOrNull()?.let { id to it } }
        val start = place.first
        val end = state.upInto(TORSO, start)
        drag(start, end)
        settle(session)
        val stroke = session.strokesOn(legPart).last()
        assertEquals(setOf(legPart, TORSO), stroke.points.map { it.part }.toSet(), "the stroke names both")
        assertTrue(stroke.softness > 0f)
        val color = assertNotNull(session.bodyColor.output.value)
        assertTrue(color.parts.getValue(TORSO).layers.size > 0, "and arrives on the parent")
        val twin = if (legPart == FRONT_LEG_L) FRONT_LEG_R else FRONT_LEG_L
        assertEquals(stroke.id, session.strokesOn(twin).last().id, "the other leg mirrors it")
    }

    @Test
    fun theAreaSettingFillsAnAreaThroughThePointerAndThePartSettingTheBase() = runDesktopComposeUiTest(PANES_WIDTH + DRAWER_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.SHEET)
        val session = assertNotNull(state.session.value)
        state.tool.value = BuildTool.FILL
        state.paint.slot = CREAM
        show(state) { rememberFillTool(state) }
        val (saddle, _) = state.showing(TORSO, listOf(0.5f), listOf(0f, 0.03f, 0.97f, 0.06f, 0.94f)).first()
        press(saddle)
        settle(session)
        assertEquals(CREAM, assertNotNull(session.document.value.character.resolved(TORSO)).surface.baseSlot, "Part reach fills the part")
        runOnIdle { state.undo() }
        settle(session)
        assertEquals(FUR, assertNotNull(session.document.value.character.resolved(TORSO)).surface.baseSlot)
        onNodeWithText("Area").performClick()
        waitForIdle()
        press(saddle)
        settle(session)
        val after = assertNotNull(session.document.value.character.resolved(TORSO)).surface
        assertEquals(FUR, after.baseSlot, "Area reach leaves the base")
        assertEquals(CREAM, after.areaFills["saddle"], "and fills the area under the press")
    }

    @Test
    fun aDragOnThePickerRecolorsItsSlotInOneStep() = runDesktopComposeUiTest(PANES_WIDTH + DRAWER_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.SHEET)
        val session = assertNotNull(state.session.value)
        state.paint.slot = FUR
        show(state) { rememberFillTool(state) }
        val before = session.document.value
        val color = assertNotNull(before.character.palette.color(FUR))
        onNodeWithTag(PaintTags.PICKER).performMouseInput {
            moveTo(Offset(40f, 20f))
            press()
            moveTo(Offset(80f, 30f))
            moveTo(Offset(120f, 40f))
            release()
        }
        settle(session)
        assertNotEquals(color, session.document.value.character.palette.color(FUR), "the slot takes the color")
        val body = assertNotNull(session.bodyColor.output.value)
        assertEquals(FUR, body.parts.getValue(TORSO).baseSlot, "the parts keep naming the slot")
        runOnIdle { state.undo() }
        settle(session)
        assertEquals(before, session.document.value, "the whole drag is one step")
    }

    @Test
    fun theInkToggleInksABorderAndTheShadingControlsWriteTheCharacter() = runDesktopComposeUiTest(PANES_WIDTH + DRAWER_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.SHEET)
        val session = assertNotNull(state.session.value)
        show(state) { rememberBrushTool(state) }
        fun chest() = assertNotNull(session.document.value.character.resolved(TORSO)).surface.regions.first { it.id.value == "chest" }
        assertEquals(false, chest().ink)
        onNodeWithTag(PaintTags.regionInk("chest")).performScrollTo().performMouseInput { click() }
        waitForIdle()
        assertEquals(true, chest().ink, "the toggle inks it")

        onNodeWithTag(shadingPreviewTag(Shading.TWO_TONE)).performClick()
        waitForIdle()
        assertEquals(ShadingMode.TWO_TONE, session.document.value.character.shading.mode, "the tile writes the character")
        onNodeWithTag(PaintTags.FAR_PARTS).performScrollTo().performMouseInput { click() }
        waitForIdle()
        assertEquals(false, session.document.value.character.shading.farSideDarker, "and so does the far parts toggle")
        runOnIdle { state.undo() }
        waitForIdle()
        assertEquals(true, session.document.value.character.shading.farSideDarker, "each is one step")
    }

    @Test
    fun anAddedSlotIsCurrentAndRemovingItGoesBack() = runDesktopComposeUiTest(PANES_WIDTH + DRAWER_WIDTH, PANES_HEIGHT) {
        val state = opened(SheetLayout.SHEET)
        val session = assertNotNull(state.session.value)
        show(state) { rememberFillTool(state) }
        val slots = session.document.value.character.palette.slots.size
        onNodeWithTag(PaintTags.ADD_SLOT).performMouseInput { click() }
        waitForIdle()
        assertEquals(slots + 1, session.document.value.character.palette.slots.size)
        assertEquals(SlotName("new"), state.paint.slot)
        onNodeWithTag(PaintTags.slot("cream")).performMouseInput { click() }
        waitForIdle()
        assertEquals(CREAM, state.paint.slot, "a swatch makes its slot current")
        state.paint.slot = SlotName("new")
        waitForIdle()
        onNodeWithTag(PaintTags.REMOVE_SLOT).performMouseInput { click() }
        waitForIdle()
        assertEquals(slots, session.document.value.character.palette.slots.size)
    }
}
