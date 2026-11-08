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
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.FurViews
import dev.chieppa.squash.screens.sheet.PaneColor
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetLook
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

private const val WIDTH = 1440
private const val HEIGHT = 900
private val LIFTED = PartId("front-paw-l")

/** The bow the Body curve case draws, in body units: the head's end sunk, the body swayed to one side at its middle; and a little short. */
private const val BOW_SINK = 0.08f
private const val SWAY = 0.08f
private const val SQUASHED = 0.92f

private object NoPoseFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * The saved fennec in the Pose stage: with Drag armed, its front left paw lifted forward and three paws planted, as board 13 draws it;
 * with Body curve armed, bowed and swayed to a Side and a Top stroke over four planted paws, as board 12 draws it. Every pane shows
 * the posed body with its lines, face and fur found on the posed surface.
 */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class PoseSmokeTest {
    private val work = TestWork()
    private val errors = CopyOnWriteArrayList<String>()

    private class Bench(val state: AppState) {
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        var color: PaneColor? = null
        var tools: PoseTools? = null
        var surface: SurfaceRibbons? = null
    }

    private fun ComposeUiTest.paneRects(): List<PixelRect> {
        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        return bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
    }

    /** Opens the saved fennec in the Pose stage and gives its bench once the rig has run. */
    private fun ComposeUiTest.posing(): Bench {
        val bench = Bench(AppState(TemplateFolder.shipped(), work))
        val state = bench.state
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent {
            SquashApp(state, NoPoseFiles)
            val selection = rememberSheetSelection(state)
            val look = rememberSheetLook()
            val color = rememberPaneColor(state)
            val tools = rememberPoseTools(state)
            val surface = rememberSurfaceLines(state, posed = true)
            SideEffect {
                bench.surface = surface
                bench.selection = selection
                bench.look = look
                bench.color = color
                bench.tools = tools
            }
        }
        settle(work) { state.session.value?.graph?.settled?.value == true && state.rigNow() != null }
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Six) }
        waitForIdle()
        return bench
    }

    /** Renders the Sheet with [tool]'s handles over it and keeps the panes and the window as [name]. */
    private fun ComposeUiTest.keepSheet(bench: Bench, tool: PaneTool, name: String) {
        val session = assertNotNull(bench.state.session.value)
        settle(work) { session.graph.settled.value && bench.selection?.posed != null && bench.surface != null }
        waitForIdle()
        val rects = paneRects()
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val framed = panesInput(assertNotNull(bench.selection), assertNotNull(bench.look), rects, width, height)
        val fullRect = rects[SheetPane.FULL.ordinal]
        val camera = assertNotNull(framed.full)
        session.showFurFrom(FurViews.FULL, furCamera(camera, fullRect.width, fullRect.height))
        framed.cameras.forEach { (view, locked) -> session.showFurFrom(FurViews.locked(view), furCamera(locked)) }
        val furViews = (listOf(FurViews.FULL) + framed.cameras.keys.map(FurViews::locked)).map { session.furView(it, posed = true) }
        settle(work) { session.graph.settled.value && session.viewGraph.settled.value && furViews.all { it.output.value != null } }
        val rules = session.document.value.character.lineRules
        fun fur(key: String) = ShownFur(assertNotNull(session.furView(key, posed = true).output.value), LineRule(rules.interior, rules.silhouette))
        val input = SheetInput(
            rects, width, height, framed.look, framed.selection, framed.cameras, camera,
            toolOverlays = framed.cameras.mapValues { (view, locked) -> tool.overlay(view, locked) },
            fullOverlay = tool.fullOverlay(FullView({ camera }, fullRect.width, fullRect.height)),
            hints = SheetHints(whole = true), color = bench.color, posedPanes = true,
            fullFur = fur(FurViews.FULL), lockedFur = framed.cameras.keys.associateWith { fur(FurViews.locked(it)) },
            posedSurface = assertNotNull(bench.surface),
        )
        val image = renderSnapshot(sheetFrame(input), width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        assertNotBlank(image.pixels, name)
        keepImage(image.pixels, image.width, name)
        val root = onAllNodes(isRoot()).onFirst()
        val window = root.captureToImage().argbPixels()
        val windowWidth = root.fetchSemanticsNode().size.width
        val corner = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInWindow }
        window.paste(windowWidth, image.pixels, image.width, corner.minOf { it.left }.roundToInt(), corner.minOf { it.top }.roundToInt())
        keepImage(window, windowWidth, "$name-window")
        assertEquals(emptyList(), errors)
    }

    @Test
    fun theFennecLiftsAPawWithItsHandlesShowing() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val bench = posing()
        val state = bench.state
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
        keepSheet(bench, assertNotNull(bench.tools).drag, "pose-lifted")
    }

    @Test
    fun theFennecBowsToABodyCurveWithItsHandlesShowing() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val bench = posing()
        val state = bench.state
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.C) }
        waitForIdle()
        assertEquals(BuildTool.BODY_CURVE, state.tool.value)
        val session = assertNotNull(state.session.value)
        val rig = assertNotNull(state.rigNow())
        val solver = PoseSolver(rig)
        val standing = Pose(planted = solver.chains.limbs.map { it.id }.toSet())
        val chain = restChain(rig)
        val side = BodyStroke(View.SIDE, polyline(chain.mapIndexed { i, p -> Vec2(p.x, p.y - BOW_SINK * share(i, chain.lastIndex).let { it * it }) }))
        val top = BodyStroke(View.TOP, polyline(chain.mapIndexed { i, p -> Vec2(p.x, p.z + SWAY * sin(PI * share(i, chain.lastIndex)).toFloat()) }))
        session.send(SetPose(solver.bendTo(standing, BodyCurve(listOf(side, top), SQUASHED)).pose))
        keepSheet(bench, assertNotNull(bench.tools).curve, "pose-curve")
    }

    private fun share(i: Int, last: Int) = i.toFloat() / last

    /** The body chain's joints at rest from the tail's tip to the head's end, as the body curve lays it. */
    private fun restChain(rig: Rig): List<Vec3> {
        val bones = assertNotNull(curveBones(rig))
        val tip = assertNotNull(rig.bone(bones.behind.last())).let { it.pivot + it.direction * it.length }
        val end = assertNotNull(rig.bone(bones.ahead.last())).let { it.pivot + it.direction * it.length }
        return listOf(tip) + (bones.behind.reversed() + bones.spine.drop(1) + bones.ahead).mapNotNull { rig.bone(it)?.pivot } + end
    }

    /** Joined cubics straight through [points], as a body stroke holds them. */
    private fun polyline(points: List<Vec2>): List<CurvePoint> = listOf(points.first()).plus(
        points.zipWithNext().flatMap { (a, b) -> listOf(a + (b - a) * (1f / 3f), a + (b - a) * (2f / 3f), b) },
    ).map { CurvePoint(it.x, it.y) }
}
