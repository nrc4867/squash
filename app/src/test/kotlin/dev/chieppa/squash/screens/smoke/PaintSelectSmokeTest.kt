package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntSize
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.edit.paintSlot
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.restSurface
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.surface.SeenFree
import dev.chieppa.squash.screens.surface.paint.rememberBrushTool
import dev.chieppa.squash.screens.surface.surfaceHit
import dev.chieppa.stretch.gpu.GpuImage
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1200
private const val HEIGHT = 800
private const val STROKE_RADIUS = 0.1f
private const val STROKE_STEPS = 12
private const val STROKE_REACH = 60f

/** The fennec's panes as the app builds them in Color, with nothing chosen, with the torso chosen under Brush, and after a stroke on it. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class PaintSelectSmokeTest {
    private val work = TestWork()
    private val errors = CopyOnWriteArrayList<String>()

    @AfterTest
    fun finish() {
        assertEquals(emptyList(), errors)
    }

    @Test
    fun aStrokeOnTheChosenPartShowsOverTheRestFaded() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.layout.value = SheetLayout.SHEET
        state.armTool(BuildTool.BRUSH)
        state.paint.brushRadius = STROKE_RADIUS
        val session = assertNotNull(state.session.value)
        var frame: SceneFrame? = null
        var size = IntSize.Zero
        var seen: SeenFree? = null
        val recorder: SceneSurfaceContent = { shown, _ -> SideEffect { if (shown != null) frame = shown } }
        setStretchContent {
            CompositionLocalProvider(LocalSceneSurface provides recorder) {
                val brush = rememberBrushTool(state)
                val tool = remember(brush) { SeenFree(brush).also { seen = it } }
                SheetPanes(state, Modifier.onSizeChanged { size = it }, tool = tool)
            }
        }
        fun settled() {
            waitForIdle()
            settle(work) { session.graph.settled.value && session.bodyColor.output.value != null && frame != null && seen?.view != null }
            waitForIdle()
        }
        fun render(name: String): GpuImage {
            val image = renderSnapshot(assertNotNull(frame), size.width, size.height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
            keepImage(image.pixels, image.width, name)
            assertNotBlank(image.pixels, name)
            return image
        }
        settled()
        render("paint-select-nothing-chosen")
        runOnIdle { state.selectPart(TORSO) }
        settled()
        val slot = assertNotNull(state.paintSlot())
        val before = render("paint-select-before")

        val view = assertNotNull(seen?.view)
        val character = assertNotNull(session.assembly.output.value)
        val placed = assertNotNull(character.part(TORSO))
        val patch = placed.surface.patches.first { it.slices != null }
        val start = List(16) { it / 16f }.firstNotNullOf { v ->
            view.toPixel(placed.origin + placed.surface.shown(patch.positionAt(0.5f, v)))
                ?.takeIf { surfaceHit(character, it, view, session.restSurface)?.part == TORSO }
        }
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(start)
            press()
            for (i in 1..STROKE_STEPS) moveTo(start + Offset(STROKE_REACH * i / STROKE_STEPS, 0f))
            release()
        }
        settled()
        val after = render("paint-select-after")

        val full = paneRect(SheetPane.FULL)
        val color = slotColor(state, slot)
        assertTrue(after.countIn(full, color) > before.countIn(full, color), "the stroke shows in Full in its slot's color")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.paneRect(pane: SheetPane): PixelRect {
        val bounds = onNodeWithTag(sheetPaneTag(pane)).fetchSemanticsNode().boundsInRoot
        return PixelRect(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
    }

    private fun slotColor(state: AppState, slot: SlotName): Color {
        val color = assertNotNull(assertNotNull(state.session.value).document.value.character.palette.color(slot))
        return Color(color.red, color.green, color.blue)
    }
}
