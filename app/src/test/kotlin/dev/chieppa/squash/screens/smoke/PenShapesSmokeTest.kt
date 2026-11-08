package dev.chieppa.squash.screens.smoke

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.draw.DrawOutlineTool
import dev.chieppa.squash.screens.draw.HOLD_SNAP_MILLIS
import dev.chieppa.squash.screens.draw.rememberStrokeLook
import dev.chieppa.squash.screens.edit.rememberCurveLook
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.OutlineLook
import dev.chieppa.squash.screens.sheet.PaneViewport
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.paneOutlineOverlays
import dev.chieppa.squash.screens.sheet.rememberOutlineLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val PANE_TAG = "pane"

private object NoChosenFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** Hold to snap and Start from template, captured offscreen with the real scene on a real GPU device. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class PenShapesSmokeTest {
    private val work = TestWork()
    private val templates = TemplateFolder.shipped()

    private fun render(input: SheetInput, name: String) {
        val errors = CopyOnWriteArrayList<String>()
        val image = renderSnapshot(sheetFrame(input), input.width, input.height, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, name)
        keepImage(image.pixels, image.width, name)
    }

    private fun ComposeUiTest.settled(session: CharacterSession) {
        settle(work) { session.graph.settled.value && session.assembly.output.value != null }
    }

    @Test
    fun startFromTemplateInTheWholeApp() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = AppState(templates, work)
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        var outlines: OutlineLook? = null
        setStretchContent {
            SquashApp(state, NoChosenFiles)
            val selected = rememberSheetSelection(state)
            val paint = rememberSheetLook()
            val lines = rememberOutlineLook()
            SideEffect {
                selection = selected
                look = paint
                outlines = lines
            }
        }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        settle(work) { state.screen.value == Screen.WORKSPACE }
        val session = assertNotNull(state.session.value)
        settled(session)
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        onNodeWithTag(ShellTags.ADD_PART_ADD).performClick()
        settled(session)
        waitForIdle()
        fun keepWorkspace(name: String) {
            val node = onNodeWithTag(ShellTags.WORKSPACE)
            keepImage(node.captureToImage().argbPixels(), node.fetchSemanticsNode().size.width, name)
        }
        keepWorkspace("pen-shapes-drawer")

        val rects = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = rects.minOf { it.left }
        val top = rects.minOf { it.top }
        val pixels = rects.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
        val width = pixels.maxOf { it.right }
        val height = pixels.maxOf { it.bottom }
        settle(work) { selection?.part?.id == state.selectedPart.value }
        val undrawn = assertNotNull(selection)
        render(panesInput(undrawn, assertNotNull(look), pixels, width, height, outlines = outlines), "pen-shapes-template-hint")

        onNodeWithTag(ShellTags.startTemplate(View.SIDE)).performScrollTo().performClick()
        val before = undrawn.part.mesh
        settle(work) { selection?.outlines?.containsKey(View.SIDE) == true && selection?.part?.mesh !== before && session.graph.settled.value }
        waitForIdle()
        val drawn = assertNotNull(selection)
        assertEquals(before.vertexCount, drawn.part.mesh.vertexCount, "the template's topology")
        assertNotEquals(0, onAllNodesWithText("1 drawn · 2 from template").fetchSemanticsNodes().size)
        render(panesInput(drawn, assertNotNull(look), pixels, width, height, outlines = outlines), "pen-shapes-from-template")
        keepWorkspace("pen-shapes-started")
    }

    @Test
    fun aHeldEllipseShownInPlace() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val plan = templates.plans.first()
        val library = TemplateLibrary(templates.parts, templates.plans)
        fun ref(id: String) = templates.parts.first { it.id.value == id }.ref
        val torso = PartInstance(PartId("torso"), ref("torso"))
        val left = PartInstance(PartId("left"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = PairId("legs"), side = Side.LEFT)
        val right = PartInstance(PartId("right"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = PairId("legs"), side = Side.RIGHT)
        val session = CharacterSession(SquashDocument(Character(CharacterId("dog"), "Dog", BodyPlanRef(plan.id, plan.version))), library, work.scope, work.dispatcher)
        session.send(AddPart(torso))
        session.send(AddParts(listOf(left, right)))
        val state = AppState(templates, work)
        state.session.value = session
        state.selectedPart.value = left.id
        settled(session)
        val rects = sheetPaneRects(WIDTH, HEIGHT, 1)
        var tool: DrawOutlineTool? = null
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        var outlines: OutlineLook? = null
        var input: SheetInput? = null
        setStretchContent {
            val stroke = rememberStrokeLook()
            val curve = rememberCurveLook()
            val selected = rememberSheetSelection(state)
            val paint = rememberSheetLook()
            val lines = rememberOutlineLook()
            val drawing = remember { DrawOutlineTool(state, stroke, curve) }
            SideEffect {
                tool = drawing
                selection = selected
                look = paint
                outlines = lines
            }
            val sized = remember(selected) { selected?.let { panesInput(it, paint, rects, WIDTH, HEIGHT) } }
            val camera = sized?.cameras?.get(View.SIDE)
            if (camera != null) {
                val pane = rects[SheetPane.SIDE.ordinal]
                with(density) {
                    Box(Modifier.size(pane.width.toDp(), pane.height.toDp())) {
                        PaneViewport(BodyView.SIDE, null, drawing.pointerInput(View.SIDE, camera).testTag(PANE_TAG))
                    }
                }
            }
            SideEffect { input = sized }
        }
        settle(work) { input != null && tool != null }
        waitForIdle()
        val panes = assertNotNull(input)
        val camera = assertNotNull(panes.cameras[View.SIDE])
        val center = camera.toPixel(CurvePoint(0.12f, 0.0f))
        mainClock.autoAdvance = false
        onNodeWithTag(PANE_TAG).performMouseInput {
            for (i in 0..70) {
                val angle = 2.0 * PI * i / 70
                val at = center + Offset(90f * cos(angle).toFloat(), 40f * sin(angle).toFloat())
                moveTo(at)
                if (i == 0) press()
            }
        }
        waitForIdle()
        fun overlay() = paneOutlineOverlays(selection, panes.cameras, tool, assertNotNull(outlines))
        render(
            SheetInput(rects, WIDTH, HEIGHT, assertNotNull(look), selection, panes.cameras, panes.full, View.SIDE, overlay()),
            "pen-shapes-stroke-before-hold",
        )
        mainClock.advanceTimeBy(HOLD_SNAP_MILLIS + 1)
        waitForIdle()
        render(
            SheetInput(rects, WIDTH, HEIGHT, assertNotNull(look), selection, panes.cameras, panes.full, View.SIDE, overlay()),
            "pen-shapes-held-ellipse",
        )
        onNodeWithTag(PANE_TAG).performMouseInput { release() }
        mainClock.autoAdvance = true
        waitForIdle()
    }
}
