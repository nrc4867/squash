package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.surface.FurDrawerTags
import dev.chieppa.squash.screens.surface.rememberFurStrokeTool
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val SETTLE_MILLIS = 30_000L
private const val DRAG_PIXELS = 90f
private const val DRAG_STEPS = 15

private object NoFurFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The fennec's fur in the panes as the app composes them, the Fur stroke tool dragged across Full, and the Lines drawer's Fur section in the whole window. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class FurSmokeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun fennecState(): AppState = AppState(TemplateFolder.shipped(), scope).apply {
        open(savedFennec())
        selectedPart.value = TORSO
        screen.value = Screen.WORKSPACE
    }

    private fun ComposeUiTest.settled(session: CharacterSession, also: () -> Boolean = { true }) = waitUntil(timeoutMillis = SETTLE_MILLIS) {
        session.graph.settled.value && session.viewGraph.settled.value && session.assembly.output.value != null && also()
    }

    private fun furMarks(session: CharacterSession) =
        assertNotNull(session.document.value.character.resolved(TORSO)).surface.marks.count { it.kind == MarkKind.FUR }

    @Test
    fun aDragInFullLaysStrokesAndTheFrameDrawsThem() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecState()
        val session = assertNotNull(state.session.value)
        var frame: SceneFrame? = null
        val recorder: SceneSurfaceContent = { shown, _ -> frame = shown }
        setStretchContent {
            CompositionLocalProvider(LocalSceneSurface provides recorder) {
                SheetPanes(state, Modifier.fillMaxSize(), tool = rememberFurStrokeTool(state))
            }
        }
        settled(session) { frame?.views?.any { it.fur.isNotEmpty() } == true }
        waitForIdle()
        val before = furMarks(session)
        val full = assertNotNull(frame).views[SheetPane.FULL.ordinal]
        val center = assertNotNull(session.assembly.output.value?.part(TORSO)).box!!.center
        val clip = full.viewProjection.transform(center.toGpu())
        val pixel = Offset((clip[0] / clip[3] + 1f) / 2f * full.viewport.width, (1f - clip[1] / clip[3]) / 2f * full.viewport.height)
        onNodeWithTag(sheetPaneTag(SheetPane.FULL)).performMouseInput {
            moveTo(pixel)
            press()
            repeat(DRAG_STEPS) { moveBy(Offset(DRAG_PIXELS / DRAG_STEPS, 0f)) }
            release()
        }
        settled(session) { furMarks(session) > before }
        waitForIdle()
        assertTrue(furMarks(session) > before, "the drag laid strokes on the torso")
        val errors = CopyOnWriteArrayList<String>()
        val shown = assertNotNull(frame)
        val image = renderSnapshot(shown, shown.views.maxOf { it.viewport.right }, shown.views.maxOf { it.viewport.bottom }, GpuOptions(onError = errors::add)) { ViewsScene(it) }
        keepImage(image.pixels, image.width, "fur-sheet")
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, "the panes")
        scope.cancel()
    }

    @Test
    fun theFurStrokeToolArmsAndItsDrawerShowsTheRule() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = fennecState()
        val session = assertNotNull(state.session.value)
        setStretchContent { SquashApp(state, NoFurFiles) }
        settled(session)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Three) }
        waitForIdle()
        onNodeWithTag(ShellTags.tool(BuildTool.FUR_STROKE)).performClick()
        waitForIdle()
        assertEquals(BuildTool.FUR_STROKE, state.tool.value)
        for (tag in listOf(FurDrawerTags.SPACING, FurDrawerTags.LENGTH)) onNodeWithTag(tag).assertExists()
        onNodeWithTag(ShellTags.tool(BuildTool.COMB)).assertExists()
        onNodeWithTag(ShellTags.tool(BuildTool.SPINE_WIDTH)).assertDoesNotExist()
        val window = onNodeWithTag(ShellTags.WORKSPACE).captureToImage().argbPixels()
        keepImage(window, onNodeWithTag(ShellTags.WORKSPACE).fetchSemanticsNode().size.width, "fur-workspace")
        scope.cancel()
    }
}
