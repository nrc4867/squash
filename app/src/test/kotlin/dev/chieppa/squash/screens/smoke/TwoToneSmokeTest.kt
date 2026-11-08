package dev.chieppa.squash.screens.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.CharacterShading
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetCharacterShading
import dev.chieppa.squash.document.ShadingMode
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.BIN_THREE_QUARTER
import dev.chieppa.squash.screens.fennec.CREAM
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.EAR_R
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.FUR
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.IRIS
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.SheetPanes
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.test.setStretchContent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val CLOSE_MARGIN = 0.06f
private const val WHOLE_MARGIN = 0.08f

/** The most times a row of the head may pass between fur and cream: a comb of covered color passes many times. */
private const val MOST_CROSSINGS = 6

/** The orbit's tiles and how far apart they stand, in degrees either side of head-on. */
private const val ORBIT_TILES = 12
private const val ORBIT_COLUMNS = 6
private const val ORBIT_STEP_DEGREES = 4f

/** How far a channel may move, and how many pixels may move further, when a soft stroke in a part's own color lands on it: rounding in the blend, no edge of its own. */
private const val BLEND_ROUNDING = 2
private const val MOST_MOVED_PIXELS = 40

/** The most an iris's pixel count may change between flat and two-tone, as a share of it. */
private const val IRIS_SHARE = 0.02f

/** The fennec shaded as spike 6 asks: one run's color at a time on the head, marks untoned, far sides that turn without a step, and the setting reaching the real panes. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class TwoToneSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val work = TestWork()
    private val templates = TemplateFolder.shipped()

    @AfterTest
    fun cleanUp() {
        assertEquals(emptyList(), errors)
    }

    private fun session(): CharacterSession {
        val session = CharacterSession(EMPTY_FENNEC, TemplateLibrary(templates.parts, templates.plans), work.scope, work.dispatcher)
        fennecCommands().forEach(session::send)
        work.settle(session)
        return session
    }

    private fun render(frame: SceneFrame, name: String, width: Int = WIDTH, height: Int = HEIGHT): IntArray {
        val image = renderSnapshot(frame, width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertNotBlank(image.pixels, name)
        return image.pixels
    }

    private fun CharacterSession.headView(toViewer: Vec3, shading: ObjectShading): SceneFrame {
        val parts = assertNotNull(assembly.output.value).parts.filter { it.id == HEAD || it.id == EAR_L || it.id == EAR_R }
        val close = framedCamera(parts, toViewer, WIDTH, HEIGHT, CLOSE_MARGIN)
        showColorAt(close.pixelsPerUnit)
        work.settle(this)
        val view = characterView(PixelRect(0, 0, WIDTH, HEIGHT), close.camera, probeState(work), PROBE_LOOK, shading, close.pixelsPerUnit) { BIN_THREE_QUARTER }
        return SceneFrame(PROBE_GROUND, listOf(view))
    }

    /** The most times any row passes between [one] and [other]. */
    private fun mostCrossings(pixels: IntArray, one: Color, other: Color): Int {
        val a = one.toArgb()
        val b = other.toArgb()
        return (0 until pixels.size / WIDTH).maxOf { y ->
            var last = 0
            var crossings = 0
            for (x in 0 until WIDTH) {
                val at = pixels[y * WIDTH + x]
                val now = if (near(at, a)) a else if (near(at, b)) b else 0
                if (now != 0) {
                    if (last != 0 && now != last) crossings++
                    last = now
                }
            }
            crossings
        }
    }

    private fun near(a: Int, b: Int) = (0..2).all { abs((a shr (it * 8) and 0xFF) - (b shr (it * 8) and 0xFF)) <= 3 }

    @Test
    fun theMuzzleShowsNoCombOfTheSkullsColorFlatOrTwoTone() {
        val session = session()
        val palette = session.document.value.character.palette
        val fur = assertNotNull(palette.colorOf(FUR))
        val cream = assertNotNull(palette.colorOf(CREAM))
        for ((viewName, toViewer) in listOf("side" to probeAzimuth(90f), "three-quarter" to probeAzimuth(45f), "low" to Vec3(0.5f, -0.35f, -0.8f))) {
            for ((modeName, mode) in listOf("flat" to Shading.FLAT, "two-tone" to Shading.TWO_TONE)) {
                val pixels = render(session.headView(toViewer, ObjectShading(mode)), "two-tone-comb-$viewName-$modeName")
                val crossings = mostCrossings(pixels, fur, cream)
                assertTrue(crossings <= MOST_CROSSINGS, "$viewName $modeName: a row passes between fur and cream $crossings times")
            }
        }
    }

    @Test
    fun aSoftStrokeTakesTheToneAndTheShadowEdgeRunsThroughItAsOneLine() {
        val session = session()
        val parts = assertNotNull(session.assembly.output.value).parts.filter { it.id != null }
        val toViewer = probeAzimuth(70f)
        val framed = framedCamera(parts, toViewer, WIDTH, HEIGHT, WHOLE_MARGIN)
        session.showColorAt(framed.pixelsPerUnit)
        work.settle(session)
        fun view() = SceneFrame(
            PROBE_GROUND,
            listOf(characterView(PixelRect(0, 0, WIDTH, HEIGHT), framed.camera, session.probeState(work), PROBE_LOOK, ObjectShading(Shading.TWO_TONE), framed.pixelsPerUnit) { BIN_THREE_QUARTER }),
        )
        val bare = render(view(), "two-tone-soft-bare")
        val id = BrushStrokeId("flank")
        val across = listOf(0.4f to 0.2f, 0.4f to 0.3f, 0.4f to 0.45f).map { (u, v) -> Anchor(TORSO, u = u, v = v) }
        session.send(SetBrushStroke(TORSO, id, BrushStroke(id, across, FUR, 0.2f, 1f)))
        work.settle(session)
        val samePlace = render(view(), "two-tone-soft-own-color")
        val moved = bare.indices.count { i -> (0..2).any { abs((bare[i] shr (it * 8) and 0xFF) - (samePlace[i] shr (it * 8) and 0xFF)) > BLEND_ROUNDING } }
        assertTrue(moved <= MOST_MOVED_PIXELS, "a fade in the part's own color moves $moved pixels, so its tone or its shadow edge differs from the part's")
        session.send(SetBrushStroke(TORSO, id, BrushStroke(id, across, CREAM, 0.2f, 1f)))
        work.settle(session)
        render(view(), "two-tone-soft-cream")
    }

    @Test
    fun marksTakeNoShadowTone() {
        val session = session()
        val iris = assertNotNull(session.document.value.character.palette.colorOf(IRIS))
        val toViewer = probeAzimuth(20f)
        val flat = render(session.headView(toViewer, ObjectShading(Shading.FLAT)), "two-tone-marks-flat").countOf(iris)
        val shaded = render(session.headView(toViewer, ObjectShading(Shading.TWO_TONE)), "two-tone-marks-two-tone").countOf(iris)
        assertNotEquals(0, flat, "the eye shows")
        assertTrue(abs(flat - shaded) <= flat * IRIS_SHARE, "the iris keeps its color: $flat flat, $shaded two-tone")
    }

    @Test
    fun theFarSidePassesThroughHeadOnWithNoStep() {
        val session = session()
        val state = session.probeState(work)
        val parts = state.character.parts.filter { it.id != null }
        val tile = WIDTH / ORBIT_COLUMNS
        val rows = ORBIT_TILES / ORBIT_COLUMNS
        val views = List(ORBIT_TILES) { i ->
            val degrees = (i - (ORBIT_TILES - 1) / 2f) * ORBIT_STEP_DEGREES
            val toViewer = probeAzimuth(degrees)
            val framed = framedCamera(parts, toViewer, tile, tile, WHOLE_MARGIN)
            val rect = PixelRect((i % ORBIT_COLUMNS) * tile, (i / ORBIT_COLUMNS) * tile, tile, tile)
            characterView(rect, framed.camera, state, PROBE_LOOK, ObjectShading(), framed.pixelsPerUnit) { BIN_THREE_QUARTER }
        }
        render(SceneFrame(PROBE_GROUND, views), "two-tone-far-side-orbit", WIDTH, tile * rows)
    }

    @Test
    fun theSettingTurnsTheRealPanesTwoTone() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = AppState(templates, work)
        var frame: SceneFrame? = null
        val recorder: SceneSurfaceContent = { shown, _ -> SideEffect { if (shown != null) frame = shown } }
        setStretchContent { CompositionLocalProvider(LocalSceneSurface provides recorder) { SheetPanes(state, Modifier.fillMaxSize()) } }
        val session = state.open(EMPTY_FENNEC)
        fennecCommands().forEach(session::send)
        state.selectedPart.value = HEAD
        settle(work) { session.graph.settled.value && frame?.views?.isNotEmpty() == true }
        fun fullShading() = assertNotNull(frame).views.flatMap { it.draws }.map { it.material.shading }.toSet()
        assertEquals(setOf(Shading.FLAT), fullShading())
        session.send(SetCharacterShading(CharacterShading(ShadingMode.TWO_TONE)))
        settle(work) { Shading.TWO_TONE in fullShading() }
        waitForIdle()
        val shown = assertNotNull(frame)
        val pixels = renderSnapshot(shown, WIDTH, HEIGHT, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(pixels.pixels, pixels.width, "two-tone-real-panes")
        assertNotBlank(pixels.pixels, "the panes")
    }
}
