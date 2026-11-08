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
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetMarkControls
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
import dev.chieppa.squash.screens.fennec.savedFennec
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
import dev.chieppa.squash.screens.surface.DrawMarkTool
import dev.chieppa.squash.screens.surface.pose.PoseTools
import dev.chieppa.squash.screens.surface.pose.rememberPoseTools
import dev.chieppa.squash.screens.surface.rememberDrawMarkTool
import dev.chieppa.squash.screens.surface.rememberSurfaceLines
import dev.chieppa.squash.screens.toucan.FOOT_L
import dev.chieppa.squash.screens.toucan.FOOT_R
import dev.chieppa.squash.screens.toucan.HEAD
import dev.chieppa.squash.screens.toucan.TORSO
import dev.chieppa.squash.screens.toucan.WING_L
import dev.chieppa.squash.screens.toucan.toucanDocument
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
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val TOUCAN_WIDTH = 1440
private const val TOUCAN_HEIGHT = 900

/** Which way the spread wing reaches from its shoulder: out to its side, a little up and back. */
private val SPREAD = Vec3(-0.3f, 0.35f, -1f)

/** How far out the spread wrist reaches, as a share of its folded distance from the shoulder. */
private const val REACH = 0.95f

/** Where the head looks: ahead, a little up and out to the toucan's left, from its neck, in body units. */
private val LOOK = Vec3(0.8f, 0.15f, -0.7f)

/** How far a planted foot may move while the wing spreads or the head turns, in body units. */
private const val STILL = 1e-5f

private object NoToucanFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * The person, built from its commands on the biped plan, in every pane with its colors, lines and face: in Shape with Move armed,
 * in Pose with a hand raised while both feet stay planted, and with the spine bent forward by a Side stroke of the body curve.
 */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class ToucanSmokeTest {
    private val work = TestWork()
    private val errors = CopyOnWriteArrayList<String>()

    private class Bench(val state: AppState) {
        var selection: SheetSelection? = null
        var look: Look? = null
        var color: PaneColor? = null
        var tools: PoseTools? = null
        var move: MoveTool? = null
        var mark: DrawMarkTool? = null
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
        state.open(toucanDocument())
        state.selectedPart.value = TORSO
        state.screen.value = Screen.WORKSPACE
        setStretchContent {
            SquashApp(state, NoToucanFiles)
            val selection = rememberSheetSelection(state)
            val look = rememberSheetLook()
            val color = rememberPaneColor(state)
            val tools = rememberPoseTools(state)
            val move = rememberMoveTool(state)
            val mark = rememberDrawMarkTool(state)
            val rest = rememberSurfaceLines(state)
            val posed = rememberSurfaceLines(state, posed = true)
            SideEffect {
                bench.selection = selection
                bench.look = look
                bench.color = color
                bench.tools = tools
                bench.move = move
                bench.mark = mark
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
    fun theToucanInShape() = runDesktopComposeUiTest(TOUCAN_WIDTH, TOUCAN_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        assertEquals(BuildTool.MOVE, bench.state.tool.value)
        keepSheet(bench, assertNotNull(bench.move), posed = false, "toucan-shape")
    }

    @Test
    fun theToucanSpreadsAWingWithBothFeetPlanted() = runDesktopComposeUiTest(TOUCAN_WIDTH, TOUCAN_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Six) }
        waitForIdle()
        val state = bench.state
        val session = assertNotNull(state.session.value)
        val solver = PoseSolver(assertNotNull(state.rigNow()))
        assertEquals(setOf(FOOT_L, FOOT_R), solver.chains.limbs.filter { it.plants }.map { it.id }.toSet(), "the feet plant and the wings do not")
        val start = Pose(planted = setOf(FOOT_L, FOOT_R))
        val wing = assertNotNull(solver.chains.limb(WING_L))
        val shoulder = assertNotNull(state.rigNow()).bones[wing.upper].pivot
        val target = shoulder + SPREAD.normalized() * ((solver.endOf(start, wing) - shoulder).length * REACH)
        val spread = solver.reach(start, wing, target, SolveOptions())
        session.send(SetPose(spread.pose))
        state.pose.limb = WING_L
        keepSheet(bench, assertNotNull(bench.tools).drag, posed = true, "toucan-pose-wing")
        listOf(FOOT_L, FOOT_R).forEach { id ->
            val foot = assertNotNull(solver.chains.limb(id))
            assertTrue((solver.endOf(start, foot) - solver.endOf(spread.pose, foot)).length < STILL, "$id stays put")
        }
    }

    @Test
    fun theToucanTurnsItsHeadToLook() = runDesktopComposeUiTest(TOUCAN_WIDTH, TOUCAN_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Six) }
        waitForIdle()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.L) }
        waitForIdle()
        val state = bench.state
        val session = assertNotNull(state.session.value)
        val solver = PoseSolver(assertNotNull(state.rigNow()))
        val start = Pose(planted = setOf(FOOT_L, FOOT_R))
        val look = assertNotNull(solver.chains.look)
        val looked = solver.look(start, assertNotNull(state.rigNow()).bones[look.head].pivot + LOOK)
        session.send(SetPose(looked.pose))
        keepSheet(bench, assertNotNull(bench.tools).look, posed = true, "toucan-pose-look")
        assertTrue(solver.turnedBetween(start, looked.pose, look.head) > 0f, "the head turns")
    }

    @Test
    fun theToucanOpensItsBeak() = runDesktopComposeUiTest(TOUCAN_WIDTH, TOUCAN_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Four) }
        waitForIdle()
        val session = assertNotNull(bench.state.session.value)
        session.send(SetMarkControls(HEAD, MarkGroupId("mouth"), MarkControls(open = 1f)))
        keepSheet(bench, assertNotNull(bench.mark), posed = false, "toucan-face-mouth")
    }
}
