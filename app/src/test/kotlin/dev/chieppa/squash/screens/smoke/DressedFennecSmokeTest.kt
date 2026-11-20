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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import dev.chieppa.squash.character.build.GarmentLook
import dev.chieppa.squash.screens.fennec.CAP
import dev.chieppa.squash.screens.fennec.FEET_SLOT
import dev.chieppa.squash.screens.fennec.SCARF
import dev.chieppa.squash.screens.fennec.SWEATER
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.looseEndPartId
import dev.chieppa.squash.rig.looseJoint
import dev.chieppa.squash.screens.fennec.SCARF
import dev.chieppa.squash.screens.fennec.SCARF_END
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
import dev.chieppa.squash.screens.dress.EdgeEditTool
import dev.chieppa.squash.screens.dress.NewGarmentNames
import dev.chieppa.squash.character.dress.PaintStyle
import dev.chieppa.squash.screens.dress.bandStroke
import dev.chieppa.squash.screens.dress.newGarmentCommand
import dev.chieppa.squash.screens.dress.newGarmentIn
import dev.chieppa.squash.character.dress.paintCommand
import dev.chieppa.squash.character.dress.paintedOf
import dev.chieppa.squash.screens.dress.rememberEdgeEditTool
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
import dev.chieppa.squash.screens.library.LibraryTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val DRESSED_WIDTH = 1440
private const val DRESSED_HEIGHT = 900

/** How far the Pose case bends the left front knee, turns the head and swings the scarf's loose end, in degrees. */
private const val KNEE_BENT = 60f
private const val HEAD_TURNED = 20f
private const val END_SWUNG = 35f
private const val KNEE_DEEP = 110f
private const val HEAD_FAR = 40f

private object NoDressedFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * The dressed fennec, built from its commands, in every pane with its colors, lines and fur, wearing its Winter outfit: in Shape at
 * rest with Move's dots over it, shown and ghosted, in Pose with the left front knee bent and the head turned, the sleeve and cap
 * following, and in Dress as W13, W14 and W15 draw it, each with the window around the panes.
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
        var edit: EdgeEditTool? = null
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
            val edit = rememberEdgeEditTool(state)
            val rest = rememberSurfaceLines(state)
            val posed = rememberSurfaceLines(state, posed = true)
            SideEffect {
                bench.selection = selection
                bench.look = look
                bench.color = color
                bench.tools = tools
                bench.move = move
                bench.edit = edit
                bench.rest = rest
                bench.posed = posed
            }
        }
        settle(work) { state.session.value?.graph?.settled?.value == true && state.rigNow() != null }
        waitForIdle()
        return bench
    }

    private fun ComposeUiTest.keepSheet(bench: Bench, tool: PaneTool, posed: Boolean, name: String, window: Boolean = false) {
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
        if (window) {
            val root = onAllNodes(isRoot()).onFirst()
            val shot = root.captureToImage().argbPixels()
            val windowWidth = root.fetchSemanticsNode().size.width
            val corner = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInWindow }
            shot.paste(windowWidth, image.pixels, image.width, corner.minOf { it.left }.roundToInt(), corner.minOf { it.top }.roundToInt())
            keepImage(shot, windowWidth, "$name-window")
        }
        assertEquals(emptyList(), errors)
    }

    private fun ComposeUiTest.key(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun theWinterOutfitAtRestInShape() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        bench.state.dress.lookElsewhere.value = GarmentLook.SHOWN
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.V) }
        waitForIdle()
        assertEquals(BuildTool.MOVE, bench.state.tool.value)
        keepSheet(bench, assertNotNull(bench.move), posed = false, "dressed-shape")
    }

    @Test
    fun theWinterOutfitFollowsAPose() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        bench.state.dress.lookElsewhere.value = GarmentLook.SHOWN
        key(Key.Seven)
        val session = assertNotNull(bench.state.session.value)
        session.send(SetPose(Pose(listOf(BonePose(FRONT_LEG_L, "knee", KNEE_BENT), BonePose(HEAD, "neck", HEAD_TURNED), BonePose(looseEndPartId(SCARF, SCARF_END), looseJoint(1), END_SWUNG)))))
        keepSheet(bench, assertNotNull(bench.tools).drag, posed = true, "dressed-pose")
    }

    @Test
    fun theWinterOutfitFollowsADeepPose() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        bench.state.dress.lookElsewhere.value = GarmentLook.SHOWN
        key(Key.Seven)
        val session = assertNotNull(bench.state.session.value)
        session.send(SetPose(Pose(listOf(BonePose(FRONT_LEG_L, "knee", KNEE_DEEP), BonePose(HEAD, "neck", HEAD_FAR)))))
        keepSheet(bench, assertNotNull(bench.tools).drag, posed = true, "dressed-deep-pose")
    }

    @Test
    fun theWinterOutfitGhostedInShape() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        assertEquals(GarmentLook.GHOSTED, bench.state.dress.lookElsewhere.value, "garments are ghosted outside Dress")
        keepSheet(bench, assertNotNull(bench.move), posed = false, "dressed-ghosted")
    }

    @Test
    fun dressDrawsTheSweaterAsW13() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        key(Key.Six)
        bench.state.dress.garment.value = SWEATER
        keepSheet(bench, assertNotNull(bench.move), posed = false, "dress-w13", window = true)
    }

    @Test
    fun dressShowsLibrarysGarmentsAndTheLayersAsL5() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        key(Key.Six)
        key(Key.L)
        bench.state.dress.garment.value = SCARF
        bench.state.library.tab.value = LibraryTab.GARMENTS
        keepSheet(bench, assertNotNull(bench.move), posed = false, "library-l5", window = true)
    }

    @Test
    fun dressShowsTheCapWithHoleArmedAsW17() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        key(Key.Six)
        key(Key.O)
        bench.state.dress.garment.value = CAP
        bench.state.selectedPart.value = HEAD
        keepSheet(bench, assertNotNull(bench.move), posed = false, "dress-w17", window = true)
    }

    @Test
    fun dressShowsTheSweatersHemsWithHandlesAndThePaintOpeningsCutAsW16() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        key(Key.Six)
        key(Key.E)
        val state = bench.state
        state.dress.garment.value = SWEATER
        val session = assertNotNull(state.session.value)
        val character = session.document.value.character
        val body = assertNotNull(session.assembly.output.value)
        val painted = paintedOf(character, body, assertNotNull(character.wardrobe.garment(SWEATER)))
        state.dress.edge.value = painted.edges.indexOfFirst { it.edge.part == TORSO }.takeIf { it >= 0 }
        state.dress.handles.value = true
        keepSheet(bench, assertNotNull(bench.edit), posed = false, "dress-w16-hems", window = true)
    }

    @Test
    fun dressMarksThePaintTheOpeningsCutOnAVestPaintedOverTheWholeTorso() = runDesktopComposeUiTest(DRESSED_WIDTH, DRESSED_HEIGHT) {
        val bench = opened()
        key(Key.Six)
        key(Key.E)
        val state = bench.state
        val session = assertNotNull(state.session.value)
        val bare = session.document.value.character
        val vest = newGarmentIn(bare.wardrobe) { "Vest" }
        session.send(bare.newGarmentCommand(vest, null, NewGarmentNames("Vest", "Vest")))
        settle(work) { session.graph.settled.value }
        state.dress.garment.value = vest.id
        val character = session.document.value.character
        val body = assertNotNull(session.assembly.output.value)
        val garment = assertNotNull(character.wardrobe.garment(vest.id))
        session.send(character.paintCommand(body, garment, null, bandStroke(character, body, TORSO, 0f, 1f), PaintStyle()))
        settle(work) { session.graph.settled.value }
        keepSheet(bench, assertNotNull(bench.edit), posed = false, "dress-cut-cells", window = true)
    }
}
