package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.rig.PoseSolver
import dev.chieppa.squash.rig.SolveOptions
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.PaneColor
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.rememberPaneColor
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.surface.pose.PoseTools
import dev.chieppa.squash.screens.surface.pose.rememberPoseTools
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
import kotlin.test.assertNotNull

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val SETTLE_MILLIS = 60_000L
private val LIFTED = PartId("front-paw-l")

private object NoPoseFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The saved fennec in the Pose stage with Drag armed, its front left paw lifted forward and three paws planted, as board 13 draws it. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class PoseSmokeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errors = CopyOnWriteArrayList<String>()

    @AfterTest
    fun stop() = scope.cancel()

    private class Bench(val state: AppState) {
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        var color: PaneColor? = null
        var tools: PoseTools? = null
    }

    private fun ComposeUiTest.paneRects(): List<PixelRect> {
        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        return bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
    }

    @Test
    fun theFennecLiftsAPawWithItsHandlesShowing() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val bench = Bench(AppState(TemplateFolder.shipped(), scope))
        val state = bench.state
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent {
            SquashApp(state, NoPoseFiles)
            val selection = rememberSheetSelection(state)
            val look = rememberSheetLook()
            val color = rememberPaneColor(state)
            val tools = rememberPoseTools(state)
            SideEffect {
                bench.selection = selection
                bench.look = look
                bench.color = color
                bench.tools = tools
            }
        }
        waitUntil(timeoutMillis = SETTLE_MILLIS) { state.session.value?.graph?.settled?.value == true && state.rigNow() != null }
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Six) }
        waitForIdle()
        assertEquals(BuildTool.DRAG, state.tool.value)

        val session = assertNotNull(state.session.value)
        val solver = PoseSolver(assertNotNull(state.rigNow()))
        val planted = solver.chains.limbs.map { it.id }.filter { it != LIFTED }.toSet()
        val start = session.document.value.character.pose.copy(planted = planted)
        val limb = assertNotNull(solver.chains.limb(LIFTED))
        val lifted = solver.reach(start, limb, solver.endOf(start, limb) + Vec3(0.18f, 0.2f, 0f), SolveOptions())
        val back = assertNotNull(solver.chains.limb(PartId("back-paw-l")))
        val stretched = solver.reach(lifted.pose, back, solver.endOf(lifted.pose, back) + Vec3(0f, -0.04f, 0f), SolveOptions(stretch = true))
        session.send(SetPose(stretched.pose.withPlanted(back.id, true)))
        state.pose.limb = LIFTED
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value && bench.selection?.posed != null }
        waitForIdle()

        val rects = paneRects()
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val framed = panesInput(assertNotNull(bench.selection), assertNotNull(bench.look), rects, width, height)
        val drag = assertNotNull(bench.tools).drag
        val fullRect = rects[SheetPane.FULL.ordinal]
        val camera = assertNotNull(framed.full)
        val input = SheetInput(
            rects, width, height, framed.look, framed.selection, framed.cameras, camera,
            toolOverlays = framed.cameras.mapValues { (view, locked) -> drag.overlay(view, locked) },
            fullOverlay = drag.fullOverlay(FullView({ camera }, fullRect.width, fullRect.height)),
            hints = SheetHints(whole = true), color = bench.color, posedPanes = true,
        )
        val image = renderSnapshot(sheetFrame(input), width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        assertNotBlank(image.pixels, "pose-lifted")
        keepImage(image.pixels, image.width, "pose-lifted")
        val root = onAllNodes(isRoot()).onFirst()
        val window = root.captureToImage().argbPixels()
        val windowWidth = root.fetchSemanticsNode().size.width
        val corner = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInWindow }
        window.paste(windowWidth, image.pixels, image.width, corner.minOf { it.left }.roundToInt(), corner.minOf { it.top }.roundToInt())
        keepImage(window, windowWidth, "pose-lifted-window")
        assertEquals(emptyList(), errors)
    }
}
