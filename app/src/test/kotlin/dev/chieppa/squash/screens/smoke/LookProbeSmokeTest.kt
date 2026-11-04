package dev.chieppa.squash.screens.smoke
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.fennec.BIN_FRONT
import dev.chieppa.squash.screens.fennec.BIN_SIDE
import dev.chieppa.squash.screens.fennec.BIN_THREE_QUARTER
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.EAR_R
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.FRONT_PAW_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.INK
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.surface.AngleBins
import dev.chieppa.squash.screens.surface.CharacterLook
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.degreesBetween
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.sheet.furCamera
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.GpuOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WIDTH = 1440
private const val HEIGHT = 900
private const val WHOLE_MARGIN = 0.08f
private const val CLOSE_MARGIN = 0.06f
private const val SWEEP_TILES = 12
private const val SWEEP_COLUMNS = 6
private const val SWEEP_LAST_DEGREES = 90f
private const val RISING_AT_72 = 4
private const val FALLING_AT_72 = 7
private const val SMOKE_IMAGES = "build/smoke-images"
private const val RAISE = 0.08f

/** The probe's call on where a face's versions switch, in degrees off the head's axis. */
private val PROBE_BINS = AngleBins(listOf(BIN_FRONT, BIN_THREE_QUARTER, BIN_SIDE), listOf(35f, 70f), 6f)

internal val PROBE_GROUND = Color(0xFFF2EEE6)
private val NO_SLOT = Color(0xFFB0B0B0)
internal val PROBE_LOOK = CharacterLook(
    lines = LineRule(interiorPixels = 2.5f, silhouettePixels = 6f),
    marks = LineRule(interiorPixels = 2.5f, silhouettePixels = 3.5f),
    creases = LineRule(interiorPixels = 2.5f),
    inkSlot = INK,
    fallback = NO_SLOT,
)

internal fun probeAzimuth(degrees: Float): Vec3 {
    val radians = Math.toRadians(degrees.toDouble())
    return Vec3(kotlin.math.cos(radians).toFloat(), RAISE, -kotlin.math.sin(radians).toFloat())
}

private val VIEWS = listOf("side" to probeAzimuth(90f), "three-quarter" to probeAzimuth(45f), "front" to probeAzimuth(0f))
private val MODES = listOf("flat" to Shading.FLAT, "two-tone" to Shading.TWO_TONE)

private const val PROBE_FUR_VIEW = "probe"

/** What the probe's views draw from this session as it stands, its fur through [camera] on a [width] by [height] surface. */
internal fun CharacterSession.probeState(camera: CameraView? = null, width: Int = WIDTH, height: Int = HEIGHT): CharacterState {
    camera?.let { showFurFrom(PROBE_FUR_VIEW, furCamera(it, width, height, 1f)) }
    settled()
    val character = assertNotNull(assembly.output.value)
    return CharacterState(
        character,
        assertNotNull(bodyColor.output.value),
        assertNotNull(bodySurfaceWork.output.value),
        document.value.character.palette,
        assertNotNull(bodyLines.output.value),
        fur = if (camera == null) FurView.EMPTY else assertNotNull(furView(PROBE_FUR_VIEW).output.value),
    )
}

/** This state with [shown] for its fur. */
internal fun CharacterState.withFur(shown: FurView) = CharacterState(character, color, work, palette, lines, shown, taper)

/** The fennec through commands, the real graph and scene, flat and two-tone from three sides, left for a person to judge. */
@Tag("slow")
class LookProbeSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun session(): CharacterSession {
        val folder = TemplateFolder.shipped()
        val session = CharacterSession(EMPTY_FENNEC, TemplateLibrary(folder.parts, folder.plans), scope, Dispatchers.Default)
        fennecCommands().forEach(session::send)
        session.settled()
        return session
    }

    private fun keep(name: String, frame: SceneFrame, width: Int = WIDTH, height: Int = HEIGHT) {
        val image = renderSnapshot(frame, width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertNotBlank(image.pixels, name)
        System.getProperty("squash.lookProbeImages")?.let { folder ->
            File(SMOKE_IMAGES, "$name.png").copyTo(File(folder, "$name.png").apply { parentFile.mkdirs() }, overwrite = true)
        }
    }

    private fun binAt(toCamera: Vec3, held: CameraBin? = null): CameraBin = PROBE_BINS.binFor(degreesBetween(Vec3.X, toCamera), held)

    @Test
    fun theFennecRendersFlatAndTwoToneFromThreeSides() {
        val session = session()
        assertEquals(savedFennec(), session.document.value, "the saved fixture is what the commands make")
        val state = session.probeState()
        val parts = state.character.parts.filter { it.id != null }
        val floor = state.character.floor
        val paw = assertNotNull(state.character.part(FRONT_PAW_L)?.box)
        assertTrue(paw.low.y - floor < 0.02f, "the paws stand on one ground: ${paw.low.y} and $floor")
        val creased = state.lines.parts.values.filter { it.crease.isNotEmpty() }
        assertTrue(creased.size >= parts.size - 1, "every part meets its parent in a crease: ${creased.map { it.part.id?.value }}")
        val head = parts.filter { it.id == HEAD || it.id == EAR_L || it.id == EAR_R }
        val rect = PixelRect(0, 0, WIDTH, HEIGHT)
        for ((viewName, toViewer) in VIEWS) {
            val whole = framedCamera(parts, toViewer, WIDTH, HEIGHT, WHOLE_MARGIN)
            val close = framedCamera(head, toViewer, WIDTH, HEIGHT, CLOSE_MARGIN)
            session.showColorAt(close.pixelsPerUnit)
            session.settled()
            val fresh = session.probeState(whole.camera)
            for ((modeName, mode) in MODES) {
                val view = characterView(rect, whole.camera, fresh, PROBE_LOOK, ObjectShading(mode), whole.pixelsPerUnit) { binAt(toViewer) }
                keep("fennec-$modeName-$viewName", SceneFrame(PROBE_GROUND, listOf(view)))
            }
            val probed = session.probeState(close.camera)
            for ((modeName, mode) in MODES) {
                val view = characterView(rect, close.camera, probed, PROBE_LOOK, ObjectShading(mode), close.pixelsPerUnit) { binAt(toViewer) }
                keep("fennec-head-$modeName-$viewName", SceneFrame(PROBE_GROUND, listOf(view)))
            }
        }
        scope.cancel()
        assertEquals(emptyList(), errors)
    }

    @Test
    fun theEyeSwitchesVersionsWithHysteresisOnASweep() {
        val session = session()
        val state = session.probeState()
        val head = state.character.parts.filter { it.id == HEAD || it.id == EAR_L || it.id == EAR_R }
        val tile = WIDTH / SWEEP_COLUMNS
        var held: CameraBin? = null
        val shown = ArrayList<CameraBin>()
        val views = List(SWEEP_TILES) { i ->
            val half = SWEEP_TILES / 2
            val degrees = if (i < half) SWEEP_LAST_DEGREES * i / (half - 1) else SWEEP_LAST_DEGREES * (SWEEP_TILES - 1 - i) / (half - 1)
            val toViewer = probeAzimuth(degrees)
            val rect = PixelRect((i % SWEEP_COLUMNS) * tile, (i / SWEEP_COLUMNS) * tile, tile, tile)
            val framed = framedCamera(head, toViewer, tile, tile, CLOSE_MARGIN)
            val bin = binAt(toViewer, held)
            held = bin
            shown += bin
            characterView(rect, framed.camera, state, PROBE_LOOK, ObjectShading(), framed.pixelsPerUnit) { bin }
        }
        keep("fennec-head-sweep", SceneFrame(PROBE_GROUND, views), WIDTH, tile * (SWEEP_TILES / SWEEP_COLUMNS))
        assertEquals(setOf(BIN_FRONT, BIN_THREE_QUARTER, BIN_SIDE), shown.toSet())
        assertEquals(BIN_FRONT, shown.first())
        assertEquals(BIN_FRONT, shown.last())
        assertEquals(BIN_THREE_QUARTER, shown[RISING_AT_72], "turning out, the bin holds past its edge")
        assertEquals(BIN_SIDE, shown[FALLING_AT_72], "turning back at the same angle, the other bin holds")
        scope.cancel()
        assertEquals(emptyList(), errors)
    }
}
