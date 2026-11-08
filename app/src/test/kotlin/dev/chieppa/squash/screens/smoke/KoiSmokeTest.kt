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
import dev.chieppa.squash.screens.koi.KOI_BODY
import dev.chieppa.squash.screens.koi.koiDocument
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val KOI_WIDTH = 1440
private const val KOI_HEIGHT = 900

/** How far the Side stroke lifts the tail end of the chain above the body's line, in body units, falling away as a square toward the head. */
private const val TAIL_LIFT = 0.2f

private object NoKoiFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * The koi, built from its commands on the Blank plan, in every pane with its colors and lines: in Shape with Move's dots on its
 * hand-made sockets, and in Pose bent by a Side stroke of the body curve that lifts its tail.
 */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class KoiSmokeTest {
    private val work = TestWork()
    private val errors = CopyOnWriteArrayList<String>()

    @AfterTest
    fun stop() = work.close()

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
        state.open(koiDocument())
        state.selectedPart.value = KOI_BODY
        state.screen.value = Screen.WORKSPACE
        setStretchContent {
            SquashApp(state, NoKoiFiles)
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
    fun theKoiInShape() = runDesktopComposeUiTest(KOI_WIDTH, KOI_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        assertEquals(BuildTool.MOVE, bench.state.tool.value)
        keepSheet(bench, assertNotNull(bench.move), posed = false, "koi-shape")
    }

    @Test
    fun theBodyCurveBendsTheKoiInPose() = runDesktopComposeUiTest(KOI_WIDTH, KOI_HEIGHT) {
        val bench = opened()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.Six) }
        waitForIdle()
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.C) }
        waitForIdle()
        val state = bench.state
        assertEquals(BuildTool.BODY_CURVE, state.tool.value)
        val session = assertNotNull(state.session.value)
        val rig = assertNotNull(state.rigNow())
        val bones = assertNotNull(curveBones(rig), "the body and the tail fin make a body chain")
        assertEquals(1, bones.behind.size, "the tail fin is the chain behind the body")
        val chain = restChain(rig, bones)
        val last = chain.lastIndex.toFloat()
        val side = BodyStroke(View.SIDE, polyline(chain.mapIndexed { i, p -> Vec2(p.x, p.y + TAIL_LIFT * (1f - i / last).let { it * it }) }))
        val solver = PoseSolver(rig)
        val bent = solver.bendTo(Pose(), BodyCurve(listOf(side), 1f)).pose
        session.send(SetPose(bent))
        keepSheet(bench, assertNotNull(bench.tools).curve, posed = true, "koi-pose-curve")
        assertTrue(bent.bones.isNotEmpty(), "the curve turns the chain's bones")
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
