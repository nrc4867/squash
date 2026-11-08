package dev.chieppa.squash.screens.smoke

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BoardPlace
import dev.chieppa.squash.document.ImageBytes
import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.ReferenceImage
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ImageDraw
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.references.TraceDraws
import dev.chieppa.squash.screens.references.decodeImage
import dev.chieppa.squash.screens.references.fittedTrace
import dev.chieppa.squash.screens.references.sketchPng
import dev.chieppa.squash.screens.references.traceCorners
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.DEFAULT_GHOST
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.OutlineLook
import dev.chieppa.squash.screens.sheet.PANE_ZOOM_TAG
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.paneOutlineOverlays
import dev.chieppa.squash.screens.sheet.rememberOutlineLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetSelection
import dev.chieppa.squash.screens.sheet.restPoseCamera
import dev.chieppa.squash.screens.sheet.sheetPaneTag
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.GpuRestPoseArtist
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.shell.SquashFormat
import dev.chieppa.squash.screens.single.SingleCamera
import dev.chieppa.squash.screens.single.SingleInput
import dev.chieppa.squash.screens.single.SingleTags
import dev.chieppa.squash.screens.single.singleFrame
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.document.RecentFiles
import dev.chieppa.stretch.gpu.GpuImage
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import dev.chieppa.stretch.theme.Stretch
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag

/** The size the app's window opens at. */
private const val WIDTH = 1440
private const val HEIGHT = 900
private const val STROKE_RADIUS = 60f
private const val STROKE_POINTS = 60

/** A drag that takes Single's camera well away from every view, in pixels. */
private const val FREE_ORBIT_X = 90f
private const val FREE_ORBIT_Y = 50f

/** The least share of a pane the part's fill must cover where the part is drawn. */
private const val MIN_FILL_SHARE = 0.005f

private object NoFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/**
 * Each screen of the app, composed whole over the templates packed into the app's resources, with a real graph behind
 * it, and captured to an image. A native surface has no window here, so the workspace's panes are rendered apart, with
 * the real scene on a real GPU device.
 */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class ScreensSmokeTest {
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val work = TestWork(CoroutineExceptionHandler { _, e -> failures += e })
    private val templates = TemplateFolder.shipped()

    @AfterTest
    fun nothingFailedInTheBackground() {
        assertEquals(emptyList(), failures)
    }

    private fun smoke(block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(WIDTH, HEIGHT, block = block)

    private fun ComposeUiTest.show(state: AppState) = setStretchContent { SquashApp(state, NoFiles) }

    private fun ComposeUiTest.assertCaptured(tag: String) {
        waitForIdle()
        assertNotBlank(onNodeWithTag(tag).captureToImage().argbPixels(), tag)
    }

    private fun ComposeUiTest.createCharacter(state: AppState): CharacterSession {
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        settle(work) { state.screen.value == Screen.WORKSPACE }
        return assertNotNull(state.session.value)
    }

    private fun ComposeUiTest.assertSettled(session: CharacterSession) {
        settle(work) { session.graph.settled.value && onAllNodesWithText("Settled").fetchSemanticsNodes().isNotEmpty() }
        session.document.value.character.parts.forEach { assertNull(session.mesh(it.id).failure.value, "${it.id} failed to build") }
    }

    @Test
    fun home() = smoke {
        val file = Files.createTempFile("marsh-hare", ".json").also { it.toFile().deleteOnExit() }
        val format = SquashFormat()
        val new = newCharacter(templates.plans.first(), templates, "Marsh hare")
        runBlocking { FileStorage.write(file.absolutePathString(), format.encode(new.commands.fold(new.document) { d, c -> c.apply(d).document })) }
        show(AppState(templates, work, RecentFiles(listOf(file.absolutePathString())), format, artist = GpuRestPoseArtist))
        settle(work) { onAllNodesWithTag(ShellTags.HOME_ART).fetchSemanticsNodes().isNotEmpty() }
        assertCaptured(ShellTags.HOME)
        keep(onNodeWithTag(ShellTags.HOME).captureToImage().argbPixels(), onNodeWithTag(ShellTags.HOME).fetchSemanticsNode().size.width, "home")
    }

    @Test
    fun newCharacter() = smoke {
        show(AppState(templates, work, artist = GpuRestPoseArtist))
        onNodeWithTag(ShellTags.newPlan(templates.plans.first().id.value)).assertExists()
        settle(work) { onAllNodesWithTag(ShellTags.NEW_ART).fetchSemanticsNodes().isNotEmpty() }
        assertCaptured(ShellTags.NEW_DIALOG)
        keep(onNodeWithTag(ShellTags.NEW_DIALOG).captureToImage().argbPixels(), onNodeWithTag(ShellTags.NEW_DIALOG).fetchSemanticsNode().size.width, "new")
    }

    @Test
    fun theWheelZoomsOnePaneOfTheRealSheetAndItsReadoutFitsItAgain() = smoke {
        val state = AppState(templates, work)
        show(state)
        assertSettled(createCharacter(state))
        val side = onNodeWithTag(sheetPaneTag(SheetPane.SIDE))
        side.performMouseInput {
            moveTo(center)
            scroll(-2f)
        }
        waitForIdle()
        assertTrue(state.paneNavigation[View.SIDE].zoom > 1f, "Side zooms")
        assertEquals(1f, state.paneNavigation[View.TOP].zoom, "Top stays")
        onNodeWithTag(PANE_ZOOM_TAG).performClick()
        waitForIdle()
        assertEquals(1f, state.paneNavigation[View.SIDE].zoom)
        onNodeWithTag(PANE_ZOOM_TAG).assertDoesNotExist()
    }

    @Test
    fun addPartOverTheWorkspace() = smoke {
        val state = AppState(templates, work)
        show(state)
        val session = createCharacter(state)
        assertSettled(session)
        state.armTool(BuildTool.ADD_PART)
        val document = session.document.value
        val open = openSockets(document, assertNotNull(templates.plan(document.character.bodyPlan)), templates)
        assertEquals(setOf("neck", "front-leg", "back-leg", "tail"), open.map { it.name }.toSet())
        for (socket in open) onNodeWithTag(ShellTags.addSocket(socket.name)).assertExists()
        onNodeWithTag(ShellTags.addSocket("front-leg")).performClick()
        assertCaptured(ShellTags.ADD_PART)
        assertCaptured(ShellTags.WORKSPACE)
    }

    @Test
    fun theAssembledWorkspaceDrawnInPlace() = smoke {
        val state = AppState(templates, work)
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        setStretchContent {
            SquashApp(state, NoFiles)
            val selected = rememberSheetSelection(state)
            val drawing = rememberSheetLook()
            SideEffect {
                selection = selected
                look = drawing
            }
        }
        val session = createCharacter(state)
        assertSettled(session)
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        onNodeWithTag(ShellTags.ADD_PART_ADD).performClick()
        assertSettled(session)
        settle(work) { selection?.part?.id == state.selectedPart.value && selection != null }
        val leg = assertNotNull(selection)
        assertEquals(View.SIDE, leg.part.axes.framing[BodyView.SIDE])
        assertEquals(View.FRONT, leg.part.axes.framing[BodyView.TOP], "a leg's Front sits above its Side")
        assertEquals(View.TOP, leg.part.axes.framing[BodyView.FRONT], "and its Top beside it")

        val side = onNodeWithTag(sheetPaneTag(SheetPane.SIDE))
        val size = side.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)
        side.performMouseInput {
            moveTo(middle + Offset(STROKE_RADIUS, 0f))
            press()
            for (i in 1..STROKE_POINTS) {
                val angle = 2.0 * PI * i / STROKE_POINTS
                moveTo(middle + Offset(STROKE_RADIUS * cos(angle).toFloat(), 2f * STROKE_RADIUS * sin(angle).toFloat()))
            }
            release()
        }
        val fromTemplate = leg.part.mesh
        settle(work) { selection?.outlines?.containsKey(View.SIDE) == true && selection?.part?.mesh !== fromTemplate }
        assertSettled(session)
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("1 drawn · 2 from template")
        assertCaptured(ShellTags.WORKSPACE)

        val drawn = assertNotNull(selection)
        val mesh = drawn.part.mesh
        assertEquals(fromTemplate.vertexCount, mesh.vertexCount, "drawing keeps the template's topology")

        val bounds = SheetPane.entries.map { onNodeWithTag(sheetPaneTag(it)).fetchSemanticsNode().boundsInRoot }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        val rects = bounds.map { PixelRect((it.left - left).roundToInt(), (it.top - top).roundToInt(), it.width.roundToInt(), it.height.roundToInt()) }
        val width = rects.maxOf { it.right }
        val height = rects.maxOf { it.bottom }
        val paint = assertNotNull(look)
        val gpuErrors = CopyOnWriteArrayList<String>()
        val image = renderPanes(drawn, paint, rects, width, height, gpuErrors::add)
        keep(image, "workspace-leg")

        assertEquals(emptyList(), gpuErrors)
        assertNotBlank(image.pixels, "the panes")
        val input = panesInput(drawn, paint, rects, width, height)
        val camera = assertNotNull(input.cameras[View.SIDE])
        val sidePane = rects[SheetPane.SIDE.ordinal]

        val filled = image.pointsIn(sidePane, paint.part.base)
        assertTrue(filled.size >= (sidePane.width * sidePane.height * MIN_FILL_SHARE).toInt(), "Side shows the drawn part filled: ${filled.size}")
        val socket = camera.toPixel(drawn.part.origin)
        assertTrue(filled.maxOf { it.y } - filled.minOf { it.y } > filled.maxOf { it.x } - filled.minOf { it.x }, "the leg stands upright")
        assertTrue(filled.count { it.y >= socket.y - 2f } > filled.size * 9 / 10, "and hangs down from its socket")

        val ghost = paint.part.base.over(paint.well, DEFAULT_GHOST)
        val ghostly = image.pointsIn(sidePane, ghost)
        assertTrue(ghostly.isNotEmpty(), "the rest of the character shows in Side")
        val partBox = camera.toPixel(Vec3(drawn.part.box.high.x, drawn.part.box.low.y, 0f)).x..camera.toPixel(Vec3(drawn.part.box.low.x, 0f, 0f)).x
        assertTrue(ghostly.any { it.x !in partBox }, "beyond the part")

        val fullPane = rects[SheetPane.FULL.ordinal]
        val solid = image.pointsIn(fullPane, paint.part.base) + image.pointsIn(fullPane, paint.part.shade) + image.pointsIn(fullPane, ghost)
        assertTrue(solid.size >= (fullPane.width * fullPane.height * MIN_FILL_SHARE).toInt(), "Full shows the character")
        val fullCamera = assertNotNull(input.full)
        for (part in drawn.others + drawn.part) {
            val at = assertNotNull(fullCamera.project(part.center.toGpu(), fullPane.width, fullPane.height))
            assertTrue(at.x in 0f..fullPane.width.toFloat() && at.y in 0f..fullPane.height.toFloat(), "Full holds every part: $at")
        }
        val across = solid.maxOf { it.x } - solid.minOf { it.x }
        val partAcross = (drawn.part.box.radius * 2f) / (drawn.box.radius * 2f) * fullPane.width
        assertTrue(across > partAcross, "Full holds the whole character, wider than the part: $across, $partAcross")

        assertEquals(0, image.countIn(rects[SheetPane.FRONT.ordinal], paint.part.base), "an undrawn view shows no fill of the selection")
        assertNotEquals(0, image.countIn(rects[SheetPane.FRONT.ordinal], paint.template), "an undrawn view shows the template's outline")
    }

    @Test
    fun theSingleLayout() = smoke {
        val state = AppState(templates, work)
        var selection: SheetSelection? = null
        var look: SheetLook? = null
        var outlineLook: OutlineLook? = null
        var warn: Color? = null
        var accent: Color? = null
        setStretchContent {
            SquashApp(state, NoFiles)
            val selected = rememberSheetSelection(state)
            val drawing = rememberSheetLook()
            val outlines = rememberOutlineLook()
            val colors = Stretch.colors
            SideEffect {
                selection = selected
                look = drawing
                outlineLook = outlines
                warn = colors.warnEdge
                accent = colors.accent
            }
        }
        val session = createCharacter(state)
        assertSettled(session)
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        onNodeWithTag(ShellTags.ADD_PART_ADD).performClick()
        assertSettled(session)
        runOnIdle { state.layout.value = SheetLayout.SINGLE }
        settle(work) { onAllNodesWithText("Front leg · L · side").fetchSemanticsNodes().isNotEmpty() }
        val fromTemplate = assertNotNull(selection).part.mesh

        val viewport = onNodeWithTag(SingleTags.VIEWPORT)
        val size = viewport.fetchSemanticsNode().size
        val middle = Offset(size.width / 2f, size.height / 2f)
        viewport.performMouseInput {
            moveTo(middle + Offset(STROKE_RADIUS, 0f))
            press()
            for (i in 1..STROKE_POINTS) {
                val angle = 2.0 * PI * i / STROKE_POINTS
                moveTo(middle + Offset(STROKE_RADIUS * cos(angle).toFloat(), 2f * STROKE_RADIUS * sin(angle).toFloat()))
            }
            release()
        }
        settle(work) { selection?.outlines?.containsKey(View.SIDE) == true && selection?.part?.mesh !== fromTemplate }
        assertSettled(session)
        val drawn = assertNotNull(selection)
        assertEquals(fromTemplate.vertexCount, drawn.part.mesh.vertexCount, "drawing in Single keeps the template's topology")

        val thumbnails = onNodeWithTag(SingleTags.THUMBNAILS).captureToImage().argbPixels()
        assertNotEquals(0, thumbnails.countOf(assertNotNull(warn)), "the views not drawn are dashed in warn")
        assertNotEquals(0, thumbnails.countOf(assertNotNull(accent)), "the view on screen is lit")

        val paint = assertNotNull(look)
        val camera = SingleCamera().apply { frame(drawn.part.box, drawn.part.axes, size.width, size.height) }
        val locked = camera.lockedCamera(View.SIDE, drawn, size.width, size.height, hold = false)
        val overlay = paneOutlineOverlays(drawn, mapOf(View.SIDE to locked), null, assertNotNull(outlineLook)).getValue(View.SIDE)
        val errors = CopyOnWriteArrayList<String>()
        fun render(input: SingleInput) = renderSnapshot(singleFrame(input), size.width, size.height, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        val whole = PixelRect(0, 0, size.width, size.height)

        val snapped = render(SingleInput(size.width, size.height, paint, drawn, locked, camera.orbit.view, overlay))
        assertNotBlank(snapped.pixels, "the snapped view")
        assertNotEquals(0, snapped.countIn(whole, paint.part.base), "the drawn part fills")
        assertNotEquals(0, snapped.countIn(whole, paint.part.base.over(paint.well, DEFAULT_GHOST)), "the rest of the character is ghosted")
        keepSingleWorkspace(snapped, "single-snapped")

        val png = sketchPng()
        val sketch = assertNotNull(decodeImage(png))
        val reference = ReferenceImage(ImageId("sketch"), "Sketch", ImageBytes(png), sketch.width, sketch.height, BoardPlace(0f, 0f, 100f))
        val trace = fittedTrace(reference, View.SIDE, drawn.part.box)
        val draw = ImageDraw(sketch.trace, traceCorners(trace, View.SIDE, sketch.width, sketch.height), 0.6f)
        for ((name, traces) in listOf("single-trace-over" to TraceDraws(over = listOf(draw)), "single-trace-behind" to TraceDraws(behind = listOf(draw)))) {
            val traced = render(SingleInput(size.width, size.height, paint, drawn, locked, camera.orbit.view, overlay, traces = { traces }))
            keepSingleWorkspace(traced, name)
            assertTrue(traced.pixels.indices.any { traced.pixels[it] != snapped.pixels[it] }, "$name shows the image")
        }
        assertEquals(emptyList(), errors.toList(), "the image pass compiles and draws")

        camera.orbit.orbitBy(FREE_ORBIT_X, FREE_ORBIT_Y)
        assertNull(camera.snap(drawn.part.axes, hold = false))
        val free = render(SingleInput(size.width, size.height, paint, drawn, null, camera.orbit.view))
        assertNotBlank(free.pixels, "the free view")
        assertNotEquals(0, free.countIn(whole, paint.part.base), "the part fills from any direction")
        keep(free, "single-free")
        assertEquals(emptyList(), errors)
    }

    private fun keep(image: GpuImage, name: String) = keep(image.pixels, image.width, name)

    private fun keep(pixels: IntArray, width: Int, name: String) = keepImage(pixels, width, name)
}
