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
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.lizard.FRONT_FOOT_L
import dev.chieppa.squash.screens.lizard.LIZARD_BODY
import dev.chieppa.squash.screens.lizard.lizardDocument
import dev.chieppa.squash.screens.move.MoveMode
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
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val LIZARD_WIDTH = 1440
private const val LIZARD_HEIGHT = 900

/** How far the Side stroke arches the middle of the chain above the body's line, in body units. */
private const val ARCH = 0.12f

/** How far the Top stroke sways the chain each way, an S from tail to head, in body units. */
private const val SWAY = 0.15f

/** How far the lifted front foot goes forward and up, in body units. */
private val LIFT = Vec3(0.12f, 0.1f, 0f)

private object NoLizardFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * The lizard, built by hand from its commands on the Blank plan, in every pane with its colors and lines: in Shape with Move's Add
 * joint showing the body's joints on its axis, and in Pose with a front foot lifted while the others stay planted and the body bent
 * by a Side and a Top stroke of the body curve.
 */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class LizardSmokeTest {
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
        state.open(lizardDocument())
        state.selectedPart.value = LIZARD_BODY
        state.screen.value = Screen.WORKSPACE
        setStretchContent {
            SquashApp(state, NoLizardFiles)
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
    fun theLizardInShape() = runDesktopComposeUiTest(LIZARD_WIDTH, LIZARD_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        assertEquals(BuildTool.MOVE, bench.state.tool.value)
        bench.state.move.mode.value = MoveMode.ADD_JOINT
        keepSheet(bench, assertNotNull(bench.move), posed = false, "lizard-shape")
    }

    @Test
    fun theLizardLiftsAFootWithTheOthersPlanted() = runDesktopComposeUiTest(LIZARD_WIDTH, LIZARD_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Six) }
        waitForIdle()
        val state = bench.state
        val session = assertNotNull(state.session.value)
        val solver = PoseSolver(assertNotNull(state.rigNow()))
        assertEquals(4, solver.chains.limbs.size, "four hand-jointed legs reach as limbs")
        val planted = solver.chains.limbs.map { it.id }.filter { it != FRONT_FOOT_L }.toSet()
        val start = Pose(planted = planted)
        val limb = assertNotNull(solver.chains.limb(FRONT_FOOT_L))
        val lifted = solver.reach(start, limb, solver.endOf(start, limb) + LIFT, SolveOptions())
        session.send(SetPose(lifted.pose))
        state.pose.limb = FRONT_FOOT_L
        keepSheet(bench, assertNotNull(bench.tools).drag, posed = true, "lizard-pose-lifted")
        assertTrue(lifted.pose.bones.any { it.part.value == "front-leg-l" }, "the leg's hand-made bones turned")
    }

    @Test
    fun theBodyCurveBendsTheLizardOverItsPlantedFeet() = runDesktopComposeUiTest(LIZARD_WIDTH, LIZARD_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Six) }
        waitForIdle()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.C) }
        waitForIdle()
        val state = bench.state
        assertEquals(BuildTool.BODY_CURVE, state.tool.value)
        val session = assertNotNull(state.session.value)
        val rig = assertNotNull(state.rigNow())
        val bones = assertNotNull(curveBones(rig), "the tail, the body and the head make a body chain")
        assertEquals(3, bones.behind.size, "the tail's three hand-made spans are behind the body")
        val chain = restChain(rig, bones)
        val last = chain.lastIndex.toFloat()
        val side = BodyStroke(View.SIDE, polyline(chain.mapIndexed { i, p -> Vec2(p.x, p.y + ARCH * sin(PI * i / last).toFloat()) }))
        val top = BodyStroke(View.TOP, polyline(chain.mapIndexed { i, p -> Vec2(p.x, p.z + SWAY * sin(2.0 * PI * i / last).toFloat()) }))
        val solver = PoseSolver(rig)
        val standing = Pose(planted = solver.chains.limbs.map { it.id }.toSet())
        val bent = solver.bendTo(standing, BodyCurve(listOf(side, top), 1f)).pose
        session.send(SetPose(bent))
        keepSheet(bench, assertNotNull(bench.tools).curve, posed = true, "lizard-pose-curve")
        assertTrue(bent.bones.count { it.part == LIZARD_BODY } >= 3, "the body bends at its hand-made joints")
    }

    /** The chain's joints at rest from the tail fin's tip to the body's end. */
    private fun restChain(rig: Rig, bones: dev.chieppa.squash.rig.CurveBones): List<Vec3> {
        val tip = assertNotNull(rig.bone(bones.behind.last())).let { it.pivot + it.direction * it.length }
        val names = bones.behind.reversed() + bones.spine + bones.ahead
        val end = assertNotNull(rig.bone(names.last())).let { it.pivot + it.direction * it.length }
        return listOf(tip) + names.mapNotNull { rig.bone(it)?.pivot } + end
    }

    /** Joined cubics straight through [points], as a body stroke holds them. */
    private fun polyline(points: List<Vec2>): List<CurvePoint> = listOf(points.first()).plus(
        points.zipWithNext().flatMap { (a, b) -> listOf(a + (b - a) * (1f / 3f), a + (b - a) * (2f / 3f), b) },
    ).map { CurvePoint(it.x, it.y) }
}
