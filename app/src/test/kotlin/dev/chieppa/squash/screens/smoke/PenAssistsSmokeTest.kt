package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.draw.BENCH_TAG
import dev.chieppa.squash.screens.draw.StrokeBench
import dev.chieppa.squash.screens.draw.StrokeLook
import dev.chieppa.squash.screens.draw.StrokeParts
import dev.chieppa.squash.screens.draw.rememberStrokeLook
import dev.chieppa.squash.screens.edit.LEG_SIDE
import dev.chieppa.squash.screens.edit.edges
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.paneOutlineOverlays
import dev.chieppa.squash.screens.sheet.rememberOutlineLook
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import dev.chieppa.squash.screens.sheet.sheetSelection
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900

private object PenAssistsNoFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** A stroke held down over a real assembled dog, rendered on a real GPU with the theme's colors, one image an assist. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class PenAssistsSmokeTest {
    private val work = TestWork()

    @AfterTest
    fun stopWork() = work.close()

    private val rects = sheetPaneRects(WIDTH, HEIGHT, 2)

    private fun scene(
        name: String,
        id: PartId = StrokeParts.leftLeg.id,
        view: View = View.SIDE,
        bench: StrokeBench = StrokeBench(),
        prepare: StrokeBench.() -> Unit = {},
        stroke: (camera: dev.chieppa.squash.screens.sheet.LockedCamera) -> List<CurvePoint>,
    ) = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        var strokeLook: StrokeLook? = null
        var outlineLook: dev.chieppa.squash.screens.sheet.OutlineLook? = null
        with(bench) { settled() }
        bench.state.selectedPart.value = id
        bench.prepare()
        with(bench) { settled() }
        val selection = currentSelection(bench, id)
        val input = panesInput(selection, SMOKE_LOOK, rects, WIDTH, HEIGHT)
        val camera = assertNotNull(input.cameras[view])
        bench.shown = camera
        setStretchContent {
            val look = rememberStrokeLook()
            val outlines = rememberOutlineLook()
            SideEffect {
                strokeLook = look
                outlineLook = outlines
                bench.tool.look = look
            }
            bench.showPanesBody()
        }
        waitForIdle()
        val pixels = stroke(camera).map(camera::toPixel)
        onNodeWithTag(BENCH_TAG).performMouseInput {
            moveTo(pixels.first())
            press()
            pixels.drop(1).forEach { moveTo(it) }
        }
        waitForIdle()
        val overlays = paneOutlineOverlays(selection, input.cameras, bench.tool, assertNotNull(outlineLook), active = view)
        val errors = CopyOnWriteArrayList<String>()
        val frame = sheetFrame(
            SheetInput(rects, WIDTH, HEIGHT, SMOKE_LOOK, selection, input.cameras, input.full, active = view, toolOverlays = overlays, hints = SheetHints()),
        )
        val image = renderSnapshot(frame, WIDTH, HEIGHT, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, name)
        assertNotNull(strokeLook)
        onNodeWithTag(BENCH_TAG).performMouseInput { release() }
        waitForIdle()
    }

    private fun currentSelection(bench: StrokeBench, id: PartId): SheetSelection {
        val character = bench.session.document.value.character
        val outlines = character.resolved(assertNotNull(character.part(id))).outlines
        return assertNotNull(sheetSelection(assertNotNull(bench.session.assembly.output.value), id, outlines))
    }

    private fun line(vararg points: Pair<Float, Float>, per: Int = 30): List<CurvePoint> = points.toList().zipWithNext().flatMap { (a, b) ->
        List(per) { i ->
            val t = i.toFloat() / per
            CurvePoint(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t)
        }
    } + CurvePoint(points.last().first, points.last().second)

    @Test
    fun aHookShowsTheShadeAndTheUnreadRun() = scene("pen-assists-reading", prepare = { state.curves.stabilizer = 0f }) {
        line(-0.06f to 0.1f, 0.35f to 0.1f, 0.2f to 0.03f, 0.45f to 0.03f, 0.45f to -0.1f, -0.06f to -0.1f)
    }

    @Test
    fun aHalfOutlineShowsItsImageLive() = scene("pen-assists-half", prepare = { state.curves.halfOutline = true; state.curves.stabilizer = 0f }) {
        List(60) {
            val t = it / 59f
            CurvePoint(0.45f * t, 0.1f * sin(PI.toFloat() * 0.5f * (1f + t)))
        }
    }

    @Test
    fun aHalfOutlineInFrontShowsItsImageLive() = scene("pen-assists-half-front", view = View.FRONT, prepare = { state.curves.halfOutline = true; state.curves.stabilizer = 0f }) {
        List(60) {
            val t = it / 59f
            CurvePoint(0.12f * sin(PI.toFloat() * t), 0.12f - 0.24f * t)
        }
    }

    @Test
    fun theStabilizersLineTrailsTheTip() = scene("pen-assists-stabilizer", prepare = { state.curves.stabilizer = 1f; state.curves.snapGuides = false }) {
        line(0f to 0.1f, 0.25f to 0.1f, 0.35f to -0.02f)
    }

    @Test
    fun aGuideCatchesOnATiltedPane() = scene(
        "pen-assists-snap-tilted",
        id = StrokeParts.tail.id,
        bench = StrokeBench(tilted = true),
        prepare = {
            state.curves.stabilizer = 0f
            session.send(SetOutline(StrokeParts.tail.id, View.TOP, edges(0f to 0.1f, 0.4f to 0.1f, 0.4f to -0.1f, 0f to -0.1f)))
        },
    ) {
        List(50) {
            val t = 0.8f * it / 49f
            CurvePoint(0.405f * sin(PI.toFloat() * t), 0.06f * kotlin.math.cos(PI.toFloat() * t))
        }
    }

    @Test
    fun theDrawerAndStripRead() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = AppState(shipped, work)
        setStretchContent { SquashApp(state, PenAssistsNoFiles) }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        waitForIdle()
        keep("pen-assists-drawer-outline")
        checkNotNull(state.session.value).send(SetOutline(checkNotNull(state.selectedPart.value), View.SIDE, LEG_SIDE))
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(Key.H) }
        waitForIdle()
        keep("pen-assists-drawer-curves")
        assertTrue(state.curves.halfOutline)
    }

    private fun ComposeUiTest.keep(name: String) {
        val image = onNodeWithTag(ShellTags.WORKSPACE).captureToImage()
        keepImage(image.argbPixels(), image.width, name)
    }
}
