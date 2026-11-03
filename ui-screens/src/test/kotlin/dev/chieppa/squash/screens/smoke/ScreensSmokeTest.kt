package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.ScratchFormat
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.newCharacter
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.document.RecentFiles
import dev.chieppa.stretch.test.setStretchContent
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.absolutePathString
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag

/** The size the app's window opens at. */
private const val WIDTH = 1440
private const val HEIGHT = 900
private const val SETTLE_MILLIS = 30_000L
private const val STROKE_RADIUS = 60f
private const val STROKE_POINTS = 60

/** The least share of a pane the part's fill must cover where the part is drawn. */
private const val MIN_FILL_SHARE = 0.005f

private object NoFiles : FileChooser {
    override fun chooseOpen(format: DocumentFormat<*>): String? = null
    override fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * Each screen of the app, composed whole over the templates packed into the app's resources, with a real graph behind
 * it, and captured to an image. A native surface has no window here, so the workspace's panes are rendered apart, with
 * the real scene on a real GPU device.
 */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class ScreensSmokeTest {
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> failures += e })
    private val templates = TemplateFolder.shipped()

    @AfterTest
    fun nothingFailedInTheBackground() {
        scope.cancel()
        assertEquals(emptyList(), failures)
    }

    private fun smoke(block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(WIDTH, HEIGHT, block = block)

    private fun ComposeUiTest.show(state: AppState) = setStretchContent { SquashApp(state, NoFiles) }

    private fun ComposeUiTest.assertCaptured(tag: String) {
        waitForIdle()
        assertNotBlank(onNodeWithTag(tag).captureToImage().argbPixels(), tag)
    }

    private fun ComposeUiTest.createCharacter(state: AppState): CharacterSession {
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.screen.value == Screen.WORKSPACE }
        return assertNotNull(state.session.value)
    }

    private fun ComposeUiTest.assertSettled(session: CharacterSession) {
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && onAllNodesWithText("Settled").fetchSemanticsNodes().isNotEmpty() }
        session.document.value.character.parts.forEach { assertNull(session.mesh(it.id).failure.value, "${it.id} failed to build") }
    }

    @Test
    fun home() = smoke {
        val file = Files.createTempFile("marsh-hare", ".json").also { it.toFile().deleteOnExit() }
        val format = ScratchFormat()
        val new = newCharacter(templates.plans.first(), templates, "Marsh hare")
        FileStorage.write(file.absolutePathString(), format.encode(new.commands.fold(new.document) { d, c -> c.apply(d).document }))
        show(AppState(templates, scope, RecentFiles(listOf(file.absolutePathString())), format))
        waitUntil(timeoutMillis = SETTLE_MILLIS) { onAllNodesWithText("Marsh hare").fetchSemanticsNodes().isNotEmpty() }
        assertCaptured(ShellTags.HOME)
    }

    @Test
    fun newCharacter() = smoke {
        show(AppState(templates, scope))
        onNodeWithTag(ShellTags.newPlan(templates.plans.first().id.value)).assertExists()
        assertCaptured(ShellTags.NEW_DIALOG)
    }

    @Test
    fun pickAPartType() = smoke {
        val state = AppState(templates, scope)
        show(state)
        val session = createCharacter(state)
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        assertSettled(session)
        onNodeWithTag(ShellTags.PICK_SOCKET).assertExists()
        assertCaptured(ShellTags.PICK)
    }

    @Test
    fun theWorkspaceWithAPartSelectedAndAnOutlineDrawn() = smoke {
        val state = AppState(templates, scope)
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        setStretchContent {
            SquashApp(state, NoFiles)
            val selected = rememberSheetSelection(state)
            val drawing = rememberSheetLook()
            SideEffect {
                selection = selected
                look = drawing
            }
        }
        val session = createCharacter(state)
        assertSettled(session)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { selection != null }
        val fromTemplate = assertNotNull(selection).part.mesh

        val side = onNodeWithTag(sheetPaneTag(SheetPane.SIDE))
        val size = side.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)
        side.performMouseInput {
            moveTo(middle + Offset(STROKE_RADIUS, 0f))
            press()
            for (i in 1..STROKE_POINTS) {
                val angle = 2.0 * PI * i / STROKE_POINTS
                moveTo(middle + Offset(STROKE_RADIUS * cos(angle).toFloat(), STROKE_RADIUS * sin(angle).toFloat()))
            }
            release()
        }
        waitUntil(timeoutMillis = SETTLE_MILLIS) { selection?.outlines?.containsKey(View.SIDE) == true && selection?.part?.mesh !== fromTemplate }
        assertSettled(session)
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("1 drawn · 2 from template")
        assertCaptured(ShellTags.WORKSPACE)

        val drawn = assertNotNull(selection)
        val mesh = drawn.part.mesh
        assertEquals(fromTemplate.vertexCount, mesh.vertexCount, "drawing keeps the template's topology")
        assertTrue((0 until mesh.vertexCount).any { mesh.position(it) != fromTemplate.position(it) }, "the drawn outline moves the mesh")

        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        val rects = bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val paint = assertNotNull(look)
        val gpuErrors = CopyOnWriteArrayList<String>()
        val image = renderPanes(drawn, paint, rects, width, height, gpuErrors::add)

        assertEquals(emptyList(), gpuErrors)
        assertNotBlank(image.pixels, "the panes")
        fun fill(pane: SheetPane) = image.countIn(rects[pane.ordinal], paint.part.base)
        fun enough(pane: SheetPane) = rects[pane.ordinal].let { (it.width * it.height * MIN_FILL_SHARE).toInt() }
        assertTrue(fill(SheetPane.SIDE) >= enough(SheetPane.SIDE), "Side shows the drawn part filled: ${fill(SheetPane.SIDE)}")
        assertTrue(fill(SheetPane.FULL) >= enough(SheetPane.FULL), "Full shows the part: ${fill(SheetPane.FULL)}")
        assertEquals(0, fill(SheetPane.FRONT), "an undrawn view shows no fill")
        assertNotEquals(0, image.countIn(rects[SheetPane.FRONT.ordinal], paint.template), "an undrawn view shows the template's outline")
    }
}
