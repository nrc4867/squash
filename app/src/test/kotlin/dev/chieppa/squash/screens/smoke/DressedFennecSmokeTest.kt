package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDressedDocument
import dev.chieppa.squash.screens.move.MoveTool
import dev.chieppa.squash.screens.move.rememberMoveTool
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.FurViews
import dev.chieppa.squash.screens.sheet.PaneColor
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetLook as Look
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.ShownFur
import dev.chieppa.squash.screens.sheet.SurfaceRibbons
import dev.chieppa.squash.screens.sheet.furCamera
import dev.chieppa.squash.screens.sheet.rememberPaneColor
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.surface.pose.PoseTools
import dev.chieppa.squash.screens.surface.pose.rememberPoseTools
import dev.chieppa.squash.screens.surface.rememberSurfaceLines
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val DRESSED_WIDTH = 1440
private const val DRESSED_HEIGHT = 900

/** How far the Pose case bends the left front knee and turns the head, in degrees. */
private const val KNEE_BENT = 60f
private const val HEAD_TURNED = 20f

private object NoDressedFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * The dressed fennec, built from its commands, in every pane with its colors, lines and fur, wearing its Winter outfit: in Shape at
 * rest with Move's dots over it, and in Pose with the left front knee bent and the head turned, the sleeve and cap following.
 */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class DressedFennecSmokeTest {
    private val work = TestWork()
    private val errors = CopyOnWriteArrayList<String>()

    private class Bench(val state: AppState) {
        var selection: SheetSelection? = null
        var look: Look? = null
        var color: PaneColor? = null
        var tools: PoseTools? = null
        var move: MoveTool? = null
        var rest: SurfaceRibbons? = null
        var posed: SurfaceRibbons? = null
    }

    private fun ComposeUiTest.paneRects(): List<PixelRect> {
        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        return bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
    }

    private fun ComposeUiTest.opened(): Bench {
        val bench = Bench(AppState(TemplateFolder.shipped(), work))
        val state = bench.state
        state.open(fennecDressedDocument())
        state.selectedPart.value = TORSO
        state.screen.value = Screen.WORKSPACE
        setStretchContent {
            SquashApp(state, NoDressedFiles)
            val selection = rememberSheetSelection(state)
            val look = rememberSheetLook()
            val color = rememberPaneColor(state)
            val tools = rememberPoseTools(state)
            val move = rememberMoveTool(state)
            val rest = rememberSurfaceLines(state)
            val posed = rememberSurfaceLines(state, posed = true)
            SideEffect {
                bench.selection = selection
                bench.look = look
                bench.color = color
                bench.tools = tools
                bench.move = move
                bench.rest = rest
                bench.posed = posed
            }
        }
        settle(work) { state.session.value?.graph?.settled?.value == true && state.rigNow() != null }
        waitForIdle()
        return bench
    }

    private fun ComposeUiTest.keepSheet(bench: Bench, tool: PaneTool, posed: Boolean, name: String) {
        val session = assertNotNull(bench.state.session.value)
        settle(work) { session.graph.settled.value && bench.selection != null && (!posed || bench.selection?.posed != null) }
        waitForIdle()
        val rects = paneRects()
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val framed = panesInput(assertNotNull(bench.selection), assertNotNull(bench.look), rects, width, height)
        val fullRect = rects[SheetPane.FULL.ordinal]
        val camera = assertNotNull(framed.full)
        session.showFurFrom(FurViews.FULL, furCamera(camera, fullRect.width, fullRect.height))
        framed.cameras.forEach { (view, locked) -> session.showFurFrom(FurViews.locked(view), furCamera(locked)) }
        val furViews = (listOf(FurViews.FULL) + framed.cameras.keys.map(FurViews::locked)).map { session.furView(it, posed = posed) }
        settle(work) { session.graph.settled.value && session.viewGraph.settled.value && furViews.all { it.output.value != null } }
        val rules = session.document.value.character.lineRules
        fun fur(key: String) = ShownFur(assertNotNull(session.furView(key, posed = posed).output.value), LineRule(rules.interior, rules.silhouette))
        val input = SheetInput(
            rects, width, height, framed.look, framed.selection, framed.cameras, camera,
            toolOverlays = framed.cameras.mapValues { (view, locked) -> tool.overlay(view, locked) },
            fullOverlay = tool.fullOverlay(FullView({ camera }, fullRect.width, fullRect.height)),
            hints = SheetHints(whole = true), color = bench.color, surface = bench.rest, posedPanes = posed,
            fullFur = fur(FurViews.FULL), lockedFur = framed.cameras.keys.associateWith { fur(FurViews.locked(it)) },
            posedSurface = if (posed) assertNotNull(bench.posed) else null,
        )
        val image = renderSnapshot(sheetFrame(input), width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        assertNotBlank(image.pixels, name)
        keepImage(image.pixels, image.width, name)
        assertEquals(emptyList(), errors)
    }

    @Test
    fun theWinterOutfitAtRestInShape() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        assertEquals(BuildTool.MOVE, bench.state.tool.value)
        keepSheet(bench, assertNotNull(bench.move), posed = false, "dressed-shape")
    }

    @Test
    fun theWinterOutfitFollowsAPose() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Six) }
        waitForIdle()
        val session = assertNotNull(bench.state.session.value)
        session.send(SetPose(Pose(listOf(BonePose(FRONT_LEG_L, "knee", KNEE_BENT), BonePose(HEAD, "neck", HEAD_TURNED)))))
        keepSheet(bench, assertNotNull(bench.tools).drag, posed = true, "dressed-pose")
    }
}
