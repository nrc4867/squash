package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import dev.chieppa.squash.character.build.TemplateLibrary
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
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.TEST_STROKE_LOOK
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.PaneViewport
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.SheetPart
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.test.setStretchContent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal const val BENCH_TAG = "pane"
internal const val BENCH_WIDTH = 800
internal const val BENCH_HEIGHT = 700
internal const val BENCH_SCALE = 500f
internal const val BENCH_SETTLE_MILLIS = 20_000L

/** How far the mesh may stray from the stroke on screen: the smoothing, and rings a span apart. */
internal const val BENCH_SLACK_PIXELS = 6f

/** A stroke in a view's plane: an ellipse of radii [x] and [y] around ([centerX], [centerY]), bent by [bend] times the square of how far along x it is. */
internal class PlaneStroke(val view: View, val centerX: Float, val centerY: Float, val x: Float, val y: Float, val bend: Float = 0f) {
    fun points(count: Int = 160): List<CurvePoint> = List(count) {
        val angle = 2.0 * PI * it / (count - 1)
        val alongX = x * cos(angle).toFloat()
        CurvePoint(centerX + alongX, centerY + y * sin(angle).toFloat() + bend * (alongX + x) * (alongX + x))
    }

    /** What stays of it on the part's side of the socket plane. */
    fun kept(): List<CurvePoint> = if (view == View.FRONT) points() else points().filter { it.x >= 0f }
}

private fun distanceToSegment(point: Offset, a: Offset, b: Offset): Float {
    val run = b - a
    val square = run.x * run.x + run.y * run.y
    val t = if (square == 0f) 0f else (((point.x - a.x) * run.x + (point.y - a.y) * run.y) / square).coerceIn(0f, 1f)
    return (point - (a + run * t)).getDistance()
}

internal fun distanceToPaths(point: Offset, paths: List<List<Offset>>): Float =
    paths.minOf { path -> if (path.size < 2) (point - path.first()).getDistance() else path.zipWithNext().minOf { (a, b) -> distanceToSegment(point, a, b) } }

/** The dog the stroke tests draw on, and what its parts are. */
internal object StrokeParts {
    private fun ref(id: String) = shipped.parts.first { it.id == TemplateId(id) }.ref

    val torso = PartInstance(PartId("torso"), ref("torso"))
    val leftLeg = PartInstance(PartId("left"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = PairId("legs"), side = Side.LEFT)
    val rightLeg = PartInstance(PartId("right"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = PairId("legs"), side = Side.RIGHT)
    val tail = PartInstance(PartId("tail"), ref("tail"), SocketRef(torso.id, "tail"))
    val head = PartInstance(PartId("head"), ref("head"), SocketRef(torso.id, "neck"))
    val leftEar = PartInstance(PartId("left-ear"), ref("ear"), SocketRef(head.id, "ear"), pair = PairId("ears"), side = Side.LEFT)
    val rightEar = PartInstance(PartId("right-ear"), ref("ear"), SocketRef(head.id, "ear"), pair = PairId("ears"), side = Side.RIGHT)
}

/** A real character with its graph, a Draw outline tool on it and one pane to draw in. */
@OptIn(ExperimentalTestApi::class)
internal class StrokeBench(tilted: Boolean = false) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val plan = shipped.plans.first()
    val session = CharacterSession(
        SquashDocument(Character(CharacterId("dog"), "Dog", BodyPlanRef(plan.id, plan.version))),
        TemplateLibrary(shipped.parts, shipped.plans),
        scope,
    ).also {
        it.send(AddPart(StrokeParts.torso))
        it.send(AddParts(listOf(StrokeParts.leftLeg, StrokeParts.rightLeg)))
        if (tilted) {
            it.send(AddPart(StrokeParts.tail))
            it.send(AddPart(StrokeParts.head))
            it.send(AddParts(listOf(StrokeParts.leftEar, StrokeParts.rightEar)))
        }
    }
    val state = AppState(shipped).also { it.session.value = session }
    val tool = DrawOutlineTool(state, TEST_STROKE_LOOK, TEST_CURVE_LOOK)

    fun ComposeUiTest.settled() = waitUntil(timeoutMillis = BENCH_SETTLE_MILLIS) { session.graph.settled.value && session.assembly.output.value != null }

    fun part(id: PartId) = SheetPart(assertNotNull(session.assembly.output.value?.part(id)))

    fun camera(part: SheetPart, view: View): LockedCamera {
        val fit = SheetFit(part.box.center, BENCH_SCALE, part.box.radius * 4f)
        return LockedCamera(view, part.axes, fit, BENCH_WIDTH, BENCH_HEIGHT, part.origin)
    }

    fun ComposeUiTest.show(camera: LockedCamera) {
        setStretchContent {
            with(density) {
                Box(Modifier.size(BENCH_WIDTH.toDp(), BENCH_HEIGHT.toDp())) {
                    PaneViewport(camera.view, null, tool.pointerInput(camera.view, camera).testTag(BENCH_TAG))
                }
            }
        }
    }

    fun outline(id: PartId, view: View) = session.document.value.character.let { character ->
        assertNotNull(character.part(id)?.let(character::resolved)?.outlines?.get(view), "$id $view")
    }

    var shown: LockedCamera? by mutableStateOf(null)
    var working: PaneTool by mutableStateOf(tool)

    /** A pane whose camera and tool follow [shown] and [working], so one test can move between views and tools. */
    fun ComposeUiTest.showPanes(first: LockedCamera) {
        shown = first
        setStretchContent { showPanesBody() }
    }

    /** The pane [showPanes] sets, to compose among other content. */
    @Composable
    fun showPanesBody() {
        with(LocalDensity.current) {
            Box(Modifier.size(BENCH_WIDTH.toDp(), BENCH_HEIGHT.toDp())) {
                shown?.let { camera -> PaneViewport(camera.view, null, working.pointerInput(camera.view, camera).testTag(BENCH_TAG)) }
            }
        }
    }

    fun ComposeUiTest.strokeIn(camera: LockedCamera, plane: List<CurvePoint>) {
        shown = camera
        waitForIdle()
        stroke(plane.map(camera::toPixel))
        settled()
    }

    /** [part] as it stands: [stroke] lies on the mesh's silhouette in its pane, with every vertex no further out than the stroke's own reach. */
    fun assertSilhouetteUnder(id: PartId, stroke: PlaneStroke, whole: Boolean = true) {
        val part = part(id)
        val camera = camera(part, stroke.view)
        val silhouette = part.silhouette(stroke.view).map { path -> path.map(camera::toPixel) }
        val drawn = stroke.kept().map(camera::toPixel)
        val label = "$id ${stroke.view}"
        if (whole) {
            val worst = drawn.maxOf { distanceToPaths(it, silhouette) }
            assertTrue(worst < BENCH_SLACK_PIXELS, "$label: the stroke strays $worst pixels from the silhouette")
        }
        val onPlane = stroke.points().map { CurvePoint(it.x, it.y) }
        val low = CurvePoint(if (stroke.view == View.FRONT) onPlane.minOf { it.x } else 0f, onPlane.minOf { it.y })
        val high = CurvePoint(onPlane.maxOf { it.x }, onPlane.maxOf { it.y })
        val vertices = (0 until part.mesh.vertexCount).map { camera.toPlane(camera.toPixel(part.origin + part.mesh.position(it))) }
        val slack = BENCH_SLACK_PIXELS * camera.planePixel
        val mesh = CurvePoint(vertices.minOf { it.x }, vertices.minOf { it.y }) to CurvePoint(vertices.maxOf { it.x }, vertices.maxOf { it.y })
        assertTrue(abs(mesh.first.x - low.x) < slack && abs(mesh.first.y - low.y) < slack, "$label: mesh from ${mesh.first}, stroke from $low")
        assertTrue(abs(mesh.second.x - high.x) < slack && abs(mesh.second.y - high.y) < slack, "$label: mesh to ${mesh.second}, stroke to $high")
        if (stroke.view != View.FRONT) {
            val base = vertices.filter { it.x < slack }
            val crossing = stroke.kept().let { abs(it.first().y - it.last().y) }
            assertTrue(base.maxOf { it.y } - base.minOf { it.y } > crossing - 2f * slack, "$label: the open end spans the stroke's crossing, $crossing")
        }
    }

    fun ComposeUiTest.stroke(path: List<Offset>) {
        onNodeWithTag(BENCH_TAG).performMouseInput {
            moveTo(path.first())
            press()
            path.drop(1).forEach { moveTo(it) }
            release()
        }
        waitForIdle()
    }

    /** Every vertex of [part] as [camera] shows it. */
    fun onScreen(part: SheetPart, camera: LockedCamera): List<Offset> =
        (0 until part.mesh.vertexCount).map { camera.toPixel(part.origin + part.mesh.position(it)) }

    fun close() = scope.cancel()
}
