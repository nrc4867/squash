package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.graphics.toArgb
import dev.chieppa.squash.character.build.FurCamera
import dev.chieppa.squash.character.build.FurWedge
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.furView
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.fennec.BIN_THREE_QUARTER
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.INK
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.sheet.furCamera
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.GpuImage
import dev.chieppa.stretch.gpu.GpuOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Tag
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.tan
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FRAMES = 9
private const val COLUMNS = 3
private const val TILE = 520
private const val MARGIN = 0.1f

/** The most ink a frame may bring from outside any line's travel, or lose there, as a share of the frame's. */
private const val MOST_UNEXPLAINED_INK = 0.01f

/** Connected pixels from this many up read as a stretch of line. Fewer are the rounding of a line's edge. */
private const val STRETCH_PIXELS = 12

/** Turns per frame between neighbouring frames of an orbit, in degrees. */
private val STEPS = listOf(1f, 2f)

/**
 * What the eye reads as flicker, measured across consecutive frames of a true orbit, with a fixed target and distance, over the views
 * where a contour is hardest to hold. A line may move as far as the turn carries the furthest place from the axis. Past that, ink that
 * shows in a frame with none near it in either neighbour flashed, and ink with none near it in the frame between two that show it dropped out.
 */
@Tag("slow")
class LineFlickerSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private fun session(): CharacterSession {
        val folder = TemplateFolder.shipped()
        val session = CharacterSession(EMPTY_FENNEC, TemplateLibrary(folder.parts, folder.plans), scope, Dispatchers.Default)
        fennecCommands().forEach(session::send)
        session.settled()
        return session
    }

    /** The turn that looks from [direction], as [probeAzimuth] reads it. */
    private fun azimuthOf(direction: Vec3): Float = Math.toDegrees(atan2(-direction.z.toDouble(), direction.x.toDouble())).toFloat()

    private fun cameraAt(target: dev.chieppa.stretch.gpu.Vec3, distance: Float, centre: Float, step: Float, frame: Int): CameraView {
        val turned = probeAzimuth(centre + step * (frame - FRAMES / 2)).normalized().toGpu()
        return CameraView(eye = target + turned * distance, target = target)
    }

    private fun render(session: CharacterSession, centre: Float, step: Float, name: String): GpuImage {
        val state = session.probeState()
        val parts = state.character.parts.filter { it.id != null }
        val framed = framedCamera(parts, probeAzimuth(centre), TILE, TILE, MARGIN)
        val target = framed.camera.target
        val distance = (framed.camera.eye - target).length
        val views = List(FRAMES) { i ->
            val camera = cameraAt(target, distance, centre, step, i)
            val rect = PixelRect((i % COLUMNS) * TILE, (i / COLUMNS) * TILE, TILE, TILE)
            val fur = furView(assertNotNull(session.bodyFur.output.value), furCamera(camera, TILE, TILE))
            characterView(rect, camera, state.withFur(fur), PROBE_LOOK, ObjectShading(), framed.pixelsPerUnit) { BIN_THREE_QUARTER }
        }
        val image = renderSnapshot(SceneFrame(PROBE_GROUND, views), TILE * COLUMNS, TILE * (FRAMES / COLUMNS), GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertNotBlank(image.pixels, name)
        return image
    }

    private fun GpuImage.tile(index: Int, ink: Int): Array<BooleanArray> {
        val left = (index % COLUMNS) * TILE
        val top = (index / COLUMNS) * TILE
        return Array(TILE) { y -> BooleanArray(TILE) { x -> isInk(left + x, top + y, ink) } }
    }

    private fun Array<BooleanArray>.near(x: Int, y: Int, reach: Int): Boolean {
        for (dy in -reach..reach) {
            val row = getOrNull(y + dy) ?: continue
            for (dx in -reach..reach) if (row.getOrNull(x + dx) == true) return true
        }
        return false
    }

    private fun Array<BooleanArray>.count(): Int = sumOf { row -> row.count { it } }

    private inline fun marks(test: (x: Int, y: Int) -> Boolean) = Array(TILE) { y -> BooleanArray(TILE) { x -> test(x, y) } }

    /** How many of [marks] lie in connected pieces of at least [STRETCH_PIXELS]. */
    private fun inStretches(marks: Array<BooleanArray>): Int {
        val seen = Array(TILE) { BooleanArray(TILE) }
        var total = 0
        for (y in 0 until TILE) for (x in 0 until TILE) {
            if (!marks[y][x] || seen[y][x]) continue
            val stack = ArrayDeque(listOf(x to y))
            seen[y][x] = true
            var size = 0
            while (stack.isNotEmpty()) {
                val (px, py) = stack.removeLast()
                size++
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx
                    val ny = py + dy
                    if (nx in 0 until TILE && ny in 0 until TILE && marks[ny][nx] && !seen[ny][nx]) {
                        seen[ny][nx] = true
                        stack.addLast(nx to ny)
                    }
                }
            }
            if (size >= STRETCH_PIXELS) total += size
        }
        return total
    }

    /** The worst share of new and lost ink, and what the triples of neighbouring frames read: raw on, off, on pixels, and those past a line's travel. */
    private class Measure(
        val newShare: Float,
        val lostShare: Float,
        val rawOnOffOn: Int,
        val droppedOut: Int,
        val droppedOutStretches: Int,
        val flashed: Int,
        val flashedStretches: Int,
    )

    private fun measure(tiles: List<Array<BooleanArray>>, travel: Int): Measure {
        var worstNew = 0f
        var worstLost = 0f
        for (i in 0 until FRAMES - 1) {
            val ink = tiles[i].count().toFloat()
            worstNew = maxOf(worstNew, marks { x, y -> tiles[i + 1][y][x] && !tiles[i].near(x, y, travel) }.count() / ink)
            worstLost = maxOf(worstLost, marks { x, y -> tiles[i][y][x] && !tiles[i + 1].near(x, y, travel) }.count() / ink)
        }
        var raw = 0
        var dropped = 0
        var droppedStretches = 0
        var flashed = 0
        var flashedStretches = 0
        for (i in 1 until FRAMES - 1) {
            val before = tiles[i - 1]
            val now = tiles[i]
            val after = tiles[i + 1]
            raw += marks { x, y -> before[y][x] && !now[y][x] && after[y][x] }.count()
            val gaps = marks { x, y -> before[y][x] && after[y][x] && !now.near(x, y, travel) }
            dropped += gaps.count()
            droppedStretches += inStretches(gaps)
            val flashes = marks { x, y -> now[y][x] && !before.near(x, y, travel) && !after.near(x, y, travel) }
            flashed += flashes.count()
            flashedStretches += inStretches(flashes)
        }
        return Measure(worstNew, worstLost, raw, dropped, droppedStretches, flashed, flashedStretches)
    }

    @Test
    fun noStretchOfLineDropsOutOrFlashesThroughSmallTurnsWithTheTailAtOrAwayFromTheCamera() {
        val session = session()
        val ink = assertNotNull(session.document.value.character.palette.colorOf(INK)).toArgb()
        val character = assertNotNull(session.assembly.output.value)
        val toward = azimuthOf(assertNotNull(character.part(TAIL)?.box).center - assertNotNull(character.part(TORSO)?.box).center)
        val views = listOf("tail-toward" to toward, "tail-away" to toward + HALF_TURN, "side" to toward + QUARTER_TURN)
        val radius = TILE * (1f - MARGIN) / 2f
        for ((label, azimuth) in views) {
            for (step in STEPS) {
                val image = render(session, azimuth, step, "flicker-$label-${step.toInt()}deg")
                val tiles = List(FRAMES) { image.tile(it, ink) }
                val travel = ceil(radius * tan(Math.toRadians(step.toDouble()))).toInt()
                val m = measure(tiles, travel)
                val line = "$label at $step degrees, travel $travel px: new ${percent(m.newShare)}%, lost ${percent(m.lostShare)}%, pixels on off on ${m.rawOnOffOn}, " +
                    "dropped out ${m.droppedOut} (${m.droppedOutStretches} in stretches), flashed ${m.flashed} (${m.flashedStretches} in stretches)"
                println("FLICKER $line")
                assertTrue(m.newShare <= MOST_UNEXPLAINED_INK && m.lostShare <= MOST_UNEXPLAINED_INK, line)
                assertEquals(0, m.droppedOutStretches, line)
                assertEquals(0, m.flashedStretches, line)
            }
        }
        assertEquals(emptyList(), errors)
    }

    /**
     * What an orbit does to the strokes both frames show: how far a stroke's length changes, and how far the tip of one clearing the rim runs, in pixels per
     * degree, past what its root runs. The rim is estimated from the part's radius there.
     */
    @Test
    fun aStrokeHoldsItsSizeAsTheViewTurns() {
        val session = session()
        val fur = assertNotNull(session.bodyFur.output.value)
        val state = session.probeState()
        val framed = framedCamera(state.character.parts.filter { it.id != null }, probeAzimuth(0f), TILE, TILE, MARGIN)
        val target = framed.camera.target
        val distance = (framed.camera.eye - target).length
        var worstChange = 0f
        var worstRun = 0f
        var shownMost = 0
        val runs = ArrayList<Float>()
        val rootsById = fur.parts.values.flatMap { it.roots }.associateBy { it.id }
        fun clearsTheRim(wedge: FurWedge, camera: FurCamera): Boolean {
            val root = assertNotNull(rootsById[wedge.id])
            val toViewer = camera.toViewerFrom(root.position)
            val facing = root.normal dot toViewer
            val outward = root.normal - toViewer * facing
            if (outward.length <= 0f) return false
            return ((wedge.tip - root.position) dot outward.normalized()) > root.radius * (1f - outward.length)
        }
        var worstClearing = ""
        var worstClearingRun = 0f
        for (view in 0 until POP_VIEWS) {
            val centre = view * FULL_TURN / POP_VIEWS
            val cameras = List(FRAMES) { furCamera(cameraAt(target, distance, centre, 1f, it), TILE, TILE) }
            val shown = cameras.map { camera -> furView(fur, camera).parts.values.flatMap { it.wedges }.associateBy { it.id } }
            shownMost = maxOf(shownMost, shown.maxOf { it.size })
            for (i in 0 until FRAMES - 1) {
                for ((id, now) in shown[i]) {
                    val next = shown[i + 1][id] ?: continue
                    worstChange = maxOf(worstChange, abs(lengthPixels(next, cameras[i + 1]) - lengthPixels(now, cameras[i])))
                    val root = pixelsBetween(assertNotNull(cameras[i].toPixel(now.root)), assertNotNull(cameras[i + 1].toPixel(next.root)))
                    val tip = pixelsBetween(assertNotNull(cameras[i].toPixel(now.tip)), assertNotNull(cameras[i + 1].toPixel(next.tip)))
                    val run = (tip - root).coerceAtLeast(0f)
                    worstRun = maxOf(worstRun, run)
                    if (clearsTheRim(now, cameras[i]) || clearsTheRim(next, cameras[i + 1])) {
                        runs += run
                        if (run > worstClearingRun) {
                            worstClearingRun = run
                            worstClearing = id.value
                        }
                    }
                }
            }
        }
        runs.sort()
        println("FURHOLD at most $shownMost strokes; length change per degree, worst ${"%.2f".format(worstChange)} px; tip run past its root per degree, median ${"%.2f".format(runs[runs.size / 2])}, 99th ${"%.2f".format(runs[runs.size * 99 / 100])}, worst hidden or not ${"%.2f".format(worstRun)} px, worst clearing the rim ${"%.2f".format(worstClearingRun)} px on $worstClearing")
        assertTrue(worstChange <= MOST_LENGTH_CHANGE, "a stroke's length changed $worstChange px in a degree")
        assertTrue(worstClearingRun <= MOST_TIP_RUN, "the tip of $worstClearing ran $worstClearingRun px past its root in a degree")
    }

    /**
     * How far past the edge the tail's strokes reach from each of a full turn of views, in body units: of the strokes rooted close to the rim,
     * the mean run outward from base to tip. Spikiness that depends on the angle shows as a wide gap between the views.
     */
    @Test
    fun theTailReachesAsFarPastItsEdgeFromEveryAngle() {
        val session = session()
        val fur = assertNotNull(session.bodyFur.output.value)
        val state = session.probeState()
        val framed = framedCamera(state.character.parts.filter { it.id != null }, probeAzimuth(0f), TILE, TILE, MARGIN)
        val target = framed.camera.target
        val distance = (framed.camera.eye - target).length
        val reaches = List(POP_VIEWS) { view ->
            val camera = furCamera(cameraAt(target, distance, view * FULL_TURN / POP_VIEWS, 0f, 0), TILE, TILE)
            val tail = assertNotNull(furView(fur, camera).parts[TAIL]).wedges.mapNotNull { wedge ->
                val toViewer = camera.toViewerFrom(wedge.root)
                val facing = wedge.normal dot toViewer
                if (abs(facing) >= NEAR_THE_RIM) return@mapNotNull null
                val base = (wedge.left.first() + wedge.right.first()) * 0.5f
                (wedge.tip - base) dot (wedge.normal - toViewer * facing).normalized()
            }
            if (tail.isEmpty()) 0f else tail.average().toFloat()
        }
        println("FURREACH by view: ${reaches.joinToString { "%.3f".format(it) }}")
        val shown = reaches.filter { it > 0f }
        assertTrue(shown.max() <= shown.min() * MOST_REACH_GAP, "the tail's strokes reach from ${shown.min()} to ${shown.max()} by view")
    }

    private fun pixelsBetween(a: dev.chieppa.squash.geom.Vec2, b: dev.chieppa.squash.geom.Vec2) = hypot(a.x - b.x, a.y - b.y)

    private fun lengthPixels(wedge: FurWedge, camera: FurCamera): Float {
        val root = (wedge.left.first() + wedge.right.first()) * 0.5f
        val base = assertNotNull(camera.toPixel(root))
        val tip = assertNotNull(camera.toPixel(wedge.tip))
        return hypot(tip.x - base.x, tip.y - base.y)
    }

    private fun percent(share: Float) = "%.2f".format(share * PERCENT)

    private companion object {
        const val HALF_TURN = 180f
        const val QUARTER_TURN = 90f
        const val PERCENT = 100f
        const val FULL_TURN = 360f
        const val POP_VIEWS = 12

        /** The most a stroke's length may change in a degree of orbit, in pixels, against a rule of up to 40. */
        const val MOST_LENGTH_CHANGE = 1f

        /** How closely a root faces away from the viewer to count as on the rim, as a cosine. */
        const val NEAR_THE_RIM = 0.2f

        /** How many times further the tail's strokes may reach past its edge from one view than from another. */
        const val MOST_REACH_GAP = 1.5f

        /** The furthest the tip of a stroke clearing the rim may run past its root in a degree of orbit, in pixels. */
        const val MOST_TIP_RUN = 5f
    }
}

/** Whether the pixel at [x], [y] of this image is [ink] to within rounding. */
private fun GpuImage.isInk(x: Int, y: Int, ink: Int): Boolean {
    val pixel = this[x, y]
    return (0..2).all { abs((pixel shr (it * 8) and 0xFF) - (ink shr (it * 8) and 0xFF)) <= 2 }
}
