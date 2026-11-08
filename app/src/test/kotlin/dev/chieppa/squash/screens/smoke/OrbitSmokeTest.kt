package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.BIN_THREE_QUARTER
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.INK
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.GpuImage
import dev.chieppa.stretch.gpu.GpuOptions
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val FRAMES = 12
private const val COLUMNS = 4
private const val TILE = 420
private const val MARGIN = 0.1f
private const val STEP_DEGREES = 360f / FRAMES

/** How near its place, in pixels, a stroke's ink must lie. */
private const val INK_REACH = 3

/** The share of a stroke's places that face the camera in a frame which must show ink; a leg may hide the rest. */
private const val LEAST_INKED_SHARE = 0.75f

private val STROKE = MarkId("orbit-stroke")

/** A stroke high on the torso's flank, where most of an orbit can see it. */
private val STROKE_POINTS = List(9) { i -> Anchor(TORSO, BODY_PATCH, 0.3f + 0.04f * i, 0.13f) }

/** The fennec turned through a whole orbit with the real graph and scene: its lines move with its surface. [LineFlickerSmokeTest] holds them across small turns. */
@Tag("slow")
class OrbitSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val work = TestWork()

    private fun session(): CharacterSession {
        val folder = TemplateFolder.shipped()
        val session = CharacterSession(EMPTY_FENNEC, TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)
        fennecCommands().forEach(session::send)
        session.send(SetMark(TORSO, STROKE, Mark(STROKE, STROKE_POINTS)))
        work.settle(session)
        return session
    }

    private class Frame(val view: SceneView, val camera: CameraView, val rect: PixelRect, val toViewer: Vec3)

    private fun frames(session: CharacterSession, turn: Float): List<Frame> {
        val state = session.probeState(work)
        val parts = state.character.parts.filter { it.id != null }
        return List(FRAMES) { i ->
            val toViewer = probeAzimuth(STEP_DEGREES * i + turn)
            val framed = framedCamera(parts, toViewer, TILE, TILE, MARGIN)
            val rect = PixelRect((i % COLUMNS) * TILE, (i / COLUMNS) * TILE, TILE, TILE)
            val view = characterView(rect, framed.camera, state, PROBE_LOOK, ObjectShading(), framed.pixelsPerUnit) { BIN_THREE_QUARTER }
            Frame(view, framed.camera, rect, toViewer)
        }
    }

    private fun render(frames: List<Frame>, name: String): GpuImage {
        val width = TILE * COLUMNS
        val height = TILE * (FRAMES / COLUMNS)
        val image = renderSnapshot(SceneFrame(PROBE_GROUND, frames.map { it.view }), width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertNotBlank(image.pixels, name)
        return image
    }

    private fun GpuImage.isInk(x: Int, y: Int, ink: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        val pixel = this[x, y]
        return (0..2).all { abs((pixel shr (it * 8) and 0xFF) - (ink shr (it * 8) and 0xFF)) <= 2 }
    }

    private fun GpuImage.inkNear(x: Int, y: Int, ink: Int): Boolean =
        (-INK_REACH..INK_REACH).any { dy -> (-INK_REACH..INK_REACH).any { dx -> isInk(x + dx, y + dy, ink) } }

    private fun inkColor(session: CharacterSession): Color = assertNotNull(session.document.value.character.palette.colorOf(INK))

    @Test
    fun strokesStayOnTheSurfaceThroughAnOrbit() {
        val session = session()
        val placed = assertNotNull(assertNotNull(session.bodySurfaceWork.output.value).parts[TORSO]).marks.first { it.id == STROKE }.points
        val before = frames(session, 0f)
        val first = render(before, "orbit-frames")
        val ink = inkColor(session).toArgb()
        var framesShowingStroke = 0
        for (i in 0 until FRAMES) {
            val share = inkedShare(first, before[i], placed, ink)
            if (share != null) {
                framesShowingStroke++
                assertTrue(share >= LEAST_INKED_SHARE, "frame $i: only ${share * 100}% of the stroke's places show ink where the surface puts them")
            }
            val rect = before[i].rect
            assertTrue((rect.y until rect.bottom).any { y -> (rect.x until rect.right).any { x -> first.isInk(x, y, ink) } }, "frame $i shows lines")
        }
        assertTrue(framesShowingStroke >= FRAMES / 3, "the stroke faced the camera in only $framesShowingStroke frames")
        assertEquals(emptyList(), errors)
    }

    /** The share of [points] facing the camera that have ink at their projected place in [frame], or null with too few facing to tell. */
    private fun inkedShare(image: GpuImage, frame: Frame, points: List<ResolvedPoint>, ink: Int): Float? {
        val view = FullView({ frame.camera }, frame.rect.width, frame.rect.height)
        val facing = points.filter { (it.normal dot view.toViewer(it.position)) > FACING_ENOUGH }
        if (facing.size < points.size) return null
        val inked = facing.count { point ->
            val at: Offset = view.toPixel(point.position) ?: return@count false
            image.inkNear(frame.rect.x + at.x.toInt(), frame.rect.y + at.y.toInt(), ink)
        }
        return inked.toFloat() / facing.size
    }

    @Test
    fun theHeavierStretchOfTheBackStaysHeavierAsTheCameraOrbits() {
        val session = session()
        val frames = frames(session, 0f)
        val image = render(frames, "orbit-weight")
        val ink = inkColor(session).toArgb()
        val character = assertNotNull(session.assembly.output.value)
        val torso = assertNotNull(assertNotNull(character.part(TORSO)).box)
        val middle = torso.center
        val top = Vec3(middle.x, torso.high.y + CLEAR_OF_TORSO, middle.z)
        val under = Vec3(middle.x, torso.low.y - CLEAR_OF_TORSO, middle.z)
        val sideOn = frames.filter { abs(it.toViewer.z) > abs(it.toViewer.x) * SIDE_ON_RATIO }
        assertTrue(sideOn.isNotEmpty(), "the orbit passes the character side on")
        for (frame in sideOn) {
            val view = FullView({ frame.camera }, frame.rect.width, frame.rect.height)
            val above = assertNotNull(view.toPixel(top))
            val below = assertNotNull(view.toPixel(under))
            val x = frame.rect.x + above.x.toInt()
            val back = firstRun(image, x, (frame.rect.y + above.y.toInt()) until frame.rect.bottom, ink)
            val belly = firstRun(image, x, (frame.rect.y + below.y.toInt()) downTo frame.rect.y, ink)
            assertTrue(back > belly, "side on, the back's line is $back pixels and the belly's $belly")
        }
        assertEquals(emptyList(), errors)
    }

    /** How many pixels the first run of ink along [ys] in column [x] spans. */
    private fun firstRun(image: GpuImage, x: Int, ys: IntProgression, ink: Int): Int {
        var run = 0
        for (y in ys) {
            if (image.isInk(x, y, ink)) run++ else if (run > 0) return run
        }
        return run
    }

    private companion object {
        const val FACING_ENOUGH = 0.25f
        const val SIDE_ON_RATIO = 2f
        const val CLEAR_OF_TORSO = 0.02f
    }
}
