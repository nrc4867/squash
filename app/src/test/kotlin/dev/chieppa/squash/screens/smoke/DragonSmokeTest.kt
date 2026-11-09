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
import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.rig.PoseSolver
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.SolveOptions
import dev.chieppa.squash.rig.bendTo
import dev.chieppa.squash.rig.curveBones
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.rig.curled
import dev.chieppa.squash.screens.dragon.BACK_PAW_L
import dev.chieppa.squash.screens.dragon.BACK_PAW_R
import dev.chieppa.squash.screens.dragon.FRONT_PAW_L
import dev.chieppa.squash.screens.dragon.FRONT_PAW_R
import dev.chieppa.squash.screens.dragon.TORSO
import dev.chieppa.squash.screens.dragon.WING_L
import dev.chieppa.squash.screens.dragon.WING_R
import dev.chieppa.squash.screens.dragon.dragonDocument
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
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val DRAGON_WIDTH = 1440
private const val DRAGON_HEIGHT = 900

/** How far the Pose case lifts the left wing's wrist, in body units. */
private val WING_RAISED = Vec3(0.0f, 0.3f, 0.05f)

private object NoDragonFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * The dragon, built from its commands, in every pane with its colors and lines, spikes down its back and tail and horns on its crown:
 * in Shape with Move's dots over it, its membrane wings
 * spread at rest, and with a wing selected, close; in Pose with the left wing raised by its wrist; and with both wings folded by a whole Curl;
 * and its clawed front paw, close, at rest in Shape and with every paw curled in Pose.
 */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class DragonSmokeTest {
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

    private fun ComposeUiTest.opened(selected: PartId): Bench {
        val bench = Bench(AppState(TemplateFolder.shipped(), work))
        val state = bench.state
        state.open(dragonDocument())
        state.selectedPart.value = selected
        state.screen.value = Screen.WORKSPACE
        setStretchContent {
            SquashApp(state, NoDragonFiles)
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
    fun theDragonInShape() = runDesktopComposeUiTest(DRAGON_WIDTH, DRAGON_HEIGHT) {
        val bench = opened(TORSO)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        assertEquals(BuildTool.MOVE, bench.state.tool.value)
        keepSheet(bench, assertNotNull(bench.move), posed = false, "dragon-shape")
    }

    @Test
    fun aMembraneWingCloseInShape() = runDesktopComposeUiTest(DRAGON_WIDTH, DRAGON_HEIGHT) {
        val bench = opened(WING_L)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        keepSheet(bench, assertNotNull(bench.move), posed = false, "dragon-wing-shape")
    }

    @Test
    fun aWingRaisedByItsWristInPose() = runDesktopComposeUiTest(DRAGON_WIDTH, DRAGON_HEIGHT) {
        val bench = opened(WING_L)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Seven) }
        waitForIdle()
        val state = bench.state
        val session = assertNotNull(state.session.value)
        val solver = PoseSolver(assertNotNull(state.rigNow()))
        val wing = assertNotNull(solver.chains.limb(WING_L))
        assertEquals(false, wing.plants)
        val planted = solver.chains.limbs.filter { it.plants }.map { it.id }.toSet()
        val start = session.document.value.character.pose.copy(planted = planted)
        val raised = solver.reach(start, wing, solver.endOf(start, wing) + WING_RAISED, SolveOptions())
        session.send(SetPose(raised.pose))
        state.pose.limb = WING_L
        keepSheet(bench, assertNotNull(bench.tools).drag, posed = true, "dragon-pose-wing")
    }

    @Test
    fun theWingsFoldByCurl() = runDesktopComposeUiTest(DRAGON_WIDTH, DRAGON_HEIGHT) {
        val bench = opened(TORSO)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Seven) }
        waitForIdle()
        val state = bench.state
        val session = assertNotNull(state.session.value)
        val rig = assertNotNull(state.rigNow())
        val folded = rig.curled(session.document.value.character.pose, listOf(WING_L, WING_R), 1f)
        session.send(SetPose(folded))
        keepSheet(bench, assertNotNull(bench.tools).drag, posed = true, "dragon-pose-folded")
    }

    @Test
    fun aClawedPawCloseInShape() = runDesktopComposeUiTest(DRAGON_WIDTH, DRAGON_HEIGHT) {
        val bench = opened(FRONT_PAW_L)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        keepSheet(bench, assertNotNull(bench.move), posed = false, "dragon-paw-shape")
    }

    @Test
    fun theClawsRideTheirCurledToes() = runDesktopComposeUiTest(DRAGON_WIDTH, DRAGON_HEIGHT) {
        val bench = opened(FRONT_PAW_L)
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Seven) }
        waitForIdle()
        val state = bench.state
        val session = assertNotNull(state.session.value)
        val rig = assertNotNull(state.rigNow())
        val curled = rig.curled(session.document.value.character.pose, listOf(FRONT_PAW_L, FRONT_PAW_R, BACK_PAW_L, BACK_PAW_R), 1f)
        session.send(SetPose(curled))
        keepSheet(bench, assertNotNull(bench.tools).drag, posed = true, "dragon-pose-paws-curled")
    }
}
