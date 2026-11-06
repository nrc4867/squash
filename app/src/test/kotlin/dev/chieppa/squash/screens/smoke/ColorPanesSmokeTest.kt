package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.screens.edit.FillReach
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CharacterShading
import dev.chieppa.squash.document.SetCharacterShading
import dev.chieppa.squash.document.ShadingMode
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.PaneColor
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.single.SingleTags
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.rememberPaneColor
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.surface.paint.PaintTags
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val SETTLE_MILLIS = 60_000L

/** How far a stroke runs across a pane, as a share of its size. */
private const val STROKE_REACH = 0.08f
private const val PRESS_RINGS = 5
private const val PRESS_STEPS = 20f

private object NoColorFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The example fennec opened in the app whole, its panes in color, and the paint tools used on them with the pointer. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class ColorPanesSmokeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errors = CopyOnWriteArrayList<String>()

    private var selection: SheetSelection? = null
    private var look: SheetLook? = null
    private var color: PaneColor? = null

    @AfterTest
    fun finish() {
        scope.cancel()
        assertEquals(emptyList(), errors)
    }

    private fun ComposeUiTest.openFennec(): AppState {
        val state = AppState(TemplateFolder.shipped(), scope)
        setStretchContent {
            SquashApp(state, NoColorFiles)
            val shown = rememberSheetSelection(state)
            val drawing = rememberSheetLook()
            val painted = rememberPaneColor(state)
            SideEffect {
                selection = shown
                look = drawing
                color = painted
            }
        }
        runOnIdle {
            state.open(savedFennec())
            state.screen.value = Screen.WORKSPACE
        }
        settle(state)
        return state
    }

    private fun ComposeUiTest.settle(state: AppState) {
        val session = assertNotNull(state.session.value)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value }
        waitForIdle()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && selection != null && color != null }
        waitForIdle()
    }

    private fun ComposeUiTest.paneRects(): List<PixelRect> {
        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        return bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
    }

    /** The window as a person sees it, with the panes' real frame under its floating corners. */
    private fun ComposeUiTest.keepWorkspace(name: String, whole: Boolean, shading: Shading = Shading.FLAT): IntArray {
        val rects = paneRects()
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val paneBounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val originX = paneBounds.minOf { it.left }
        val originY = paneBounds.minOf { it.top }
        val shown = assertNotNull(color).let { PaneColor(it.body, it.palette, it.shading.copy(mode = shading), it.borders) }
        val base = panesInput(assertNotNull(selection), assertNotNull(look), rects, width, height, SheetHints(whole = whole), color = shown)
        val image = renderSnapshot(sheetFrame(base), width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        val workspace = onNodeWithTag(ShellTags.WORKSPACE)
        val window = workspace.captureToImage().argbPixels()
        val windowWidth = workspace.fetchSemanticsNode().size.width
        window.paste(windowWidth, image.pixels, image.width, originX.roundToInt(), originY.roundToInt())
        keepImage(window, windowWidth, name)
        assertNotBlank(image.pixels, name)
        return image.pixels
    }

    /**
     * Where a press in a pane of [width] might land on the character, nearest [middle] first: the free camera frames the whole fennec, whose
     * middle can fall between its parts.
     */
    private fun pressPlaces(middle: Offset, width: Float): List<Offset> {
        val ways = listOf(Offset(0f, 1f), Offset(0f, -1f), Offset(1f, 0f), Offset(-1f, 0f), Offset(1f, 1f), Offset(-1f, 1f), Offset(1f, -1f), Offset(-1f, -1f))
        return listOf(middle) + (1..PRESS_RINGS).flatMap { ring -> ways.map { middle + it * (ring * width / PRESS_STEPS) } }
    }

    private fun slotColor(name: String): Color {
        val slot = assertNotNull(assertNotNull(color).palette.color(SlotName(name)))
        return Color(slot.red, slot.green, slot.blue)
    }

    @Test
    fun theExampleFennecOpensInColorInEveryPane() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        openFennec()
        val pixels = keepWorkspace("color-fennec-opened", whole = false)
        keepWorkspace("color-fennec-opened-two-tone", whole = false, Shading.TWO_TONE)
        val rects = paneRects()
        val image = dev.chieppa.stretch.gpu.GpuImage(rects.maxOf { it.right }, rects.maxOf { it.bottom }, pixels)
        val full = rects[SheetPane.FULL.ordinal]
        assertTrue(image.countIn(full, slotColor("fur")) > full.width * full.height / 100, "Full shows the fur slot")
        assertTrue(image.countIn(full, slotColor("cream")) > 0, "and the cream regions over it")
        val side = rects[SheetPane.SIDE.ordinal]
        assertTrue(image.countIn(side, slotColor("fur")) > 0, "the selected part shows its slot in a locked pane")
    }

    /** The frame the panes themselves build, flat and then with the stored choice two-tone, in Full and in the locked panes alike. */
    @Test
    fun theFramePanesBuildFollowsTheCharactersShading() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = AppState(TemplateFolder.shipped(), scope)
        var frame: SceneFrame? = null
        val recorder: SceneSurfaceContent = { shown, _ -> SideEffect { if (shown != null) frame = shown } }
        setStretchContent { CompositionLocalProvider(LocalSceneSurface provides recorder) { SheetPanes(state, Modifier.fillMaxSize()) } }
        val session = runOnIdle { state.open(savedFennec()) }
        fun partShadings() = assertNotNull(frame).views.flatMap { view -> view.draws.filter { it.layers.isNotEmpty() } }.map { it.material.shading }.toSet()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && partShadings().isNotEmpty() }
        waitForIdle()
        assertEquals(setOf(Shading.FLAT), partShadings())
        keepFrame(assertNotNull(frame), "color-fennec-real-frame-flat")
        session.send(SetCharacterShading(CharacterShading(ShadingMode.TWO_TONE)))
        waitUntil(timeoutMillis = SETTLE_MILLIS) { partShadings() == setOf(Shading.TWO_TONE) }
        waitForIdle()
        assertEquals(4, assertNotNull(frame).views.size, "every pane shows it")
        keepFrame(assertNotNull(frame), "color-fennec-real-frame-two-tone")
    }

    private fun keepFrame(frame: SceneFrame, name: String) {
        val image = renderSnapshot(frame, WIDTH, HEIGHT, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertNotBlank(image.pixels, name)
    }

    @Test
    fun thePaintToolsWorkOnTheSurfaceWithThePointer() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = openFennec()
        val session = assertNotNull(state.session.value)
        val full = onNodeWithTag(sheetPaneTag(SheetPane.FULL))
        val size = full.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)

        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Two) }
        waitForIdle()
        onNodeWithTag(ShellTags.tool(BuildTool.BRUSH)).performClick()
        waitForIdle()
        onNodeWithTag(PaintTags.slot("cream")).performMouseInput { click() }
        waitForIdle()
        assertEquals(SlotName("cream"), state.paint.slot, "a swatch in the drawer takes the pointer in the real frame")
        val slotCount = session.document.value.character.palette.slots.size
        onNodeWithTag(PaintTags.ADD_SLOT).performMouseInput { click() }
        waitForIdle()
        assertEquals(slotCount + 1, session.document.value.character.palette.slots.size, "and so does the add button")
        runOnIdle { state.undo() }
        waitForIdle()
        state.paint.slot = SlotName("iris")
        state.paint.softness = 0.8f
        state.paint.brushRadius = 0.12f
        assertEquals(BuildTool.BRUSH, state.tool.value, "Brush is still armed after the palette clicks")
        fun strokes() = session.document.value.character.parts.sumOf { session.document.value.character.resolved(it).surface.paint.strokes.size }
        val strokesBefore = strokes()
        var onBody: Offset? = null
        for (at in pressPlaces(middle, size.width.toFloat())) {
            full.performMouseInput {
                moveTo(at)
                press()
                for (i in 1..12) moveTo(at + Offset(size.width * STROKE_REACH * i / 12f, 0f))
                release()
            }
            settle(state)
            if (strokes() > strokesBefore) {
                onBody = at
                break
            }
        }
        val body = assertNotNull(onBody, "a drag across the fennec in Full left a stroke")
        assertEquals(strokesBefore + 1, strokes(), "one stroke, where the drag crossed the body")
        keepWorkspace("color-brush-drawer", whole = true)

        onNodeWithTag(ShellTags.tool(BuildTool.FILL)).performClick()
        waitForIdle()
        state.paint.slot = SlotName("nose")
        val before = session.document.value
        full.performMouseInput {
            moveTo(body)
            press()
            release()
        }
        settle(state)
        assertNotEquals(before, session.document.value, "the press filled a section")
        keepWorkspace("color-fill", whole = true, Shading.TWO_TONE)
        runOnIdle { state.undo() }
        settle(state)
        assertEquals(before, session.document.value, "a fill is one undo step")

        val inPane = onNodeWithTag(sheetPaneTag(SheetPane.SIDE))
        val pane = inPane.fetchSemanticsNode().size
        val mid = Offset(pane.width / 2f, pane.height / 2f)
        val base = { session.document.value.character.part(assertNotNull(state.selectedPart.value))?.let { session.document.value.character.resolved(it).surface.baseSlot } }
        state.paint.fillReach = FillReach.WHOLE_PART
        inPane.performMouseInput {
            moveTo(mid)
            press()
            release()
        }
        settle(state)
        assertEquals(SlotName("nose"), base(), "a locked pane fills its part too, the whole part with Whole part")
        runOnIdle { state.undo() }
        settle(state)
        state.paint.fillReach = FillReach.UNDER_POINTER

        onNodeWithTag(ShellTags.tool(BuildTool.REGION_BORDER)).performClick()
        waitForIdle()
        val regions = { session.document.value.character.parts.sumOf { session.document.value.character.resolved(it).surface.regions.size } }
        val regionsBefore = regions()
        full.performMouseInput {
            moveTo(body)
            press()
            for (i in 1..40) {
                val angle = 2.0 * Math.PI * i / 40
                moveTo(body + Offset(30f * kotlin.math.cos(angle).toFloat(), 22f * kotlin.math.sin(angle).toFloat()) - Offset(30f, 0f))
            }
            release()
        }
        settle(state)
        assertTrue(regions() > regionsBefore, "the drag closed a region")
        assertNull(state.paint.selectedRegion, "and none is picked, so the next drag draws another")
        keepWorkspace("color-region-border", whole = true)

        onNodeWithTag(ShellTags.tool(BuildTool.PICK_COLOR)).performClick()
        waitForIdle()
        state.paint.slot = SlotName("cream")
        full.performMouseInput {
            moveTo(body + Offset(0f, 2f))
            press()
            release()
        }
        waitForIdle()
        assertNotEquals(SlotName("cream"), state.paint.slot, "Pick color took what shows under the press")
    }

    @Test
    fun theSingleLayoutKeepsTheToolsInTheStripAndNoPreviewsOnTheView() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = openFennec()
        runOnIdle {
            state.layout.value = SheetLayout.SINGLE
            state.tool.value = BuildTool.REGION_BORDER
        }
        waitForIdle()
        val tool = onNodeWithTag(ShellTags.tool(BuildTool.FILL)).fetchSemanticsNode().boundsInRoot
        val view = onNodeWithTag(SingleTags.VIEWPORT).fetchSemanticsNode().boundsInRoot
        assertTrue(tool.bottom <= view.top, "the tools sit above the view")
        onNodeWithTag(SingleTags.THUMBNAILS).assertExists()
    }
}
