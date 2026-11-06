package dev.chieppa.squash.screens.references

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.document.SetTrace
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.Trace
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import kotlin.math.abs
import kotlin.math.atan2

/** How near, in pane pixels, a press must come to a handle to take it. */
private const val HANDLE_REACH = 10f

/** How near, in pane pixels, an image's bottom comes to the ground before it snaps there. */
private const val GROUND_SNAP_PIXELS = 12f

/** The smallest an image is scaled to, as a share of its first fit. */
private const val MIN_SCALE = 0.05f

/** What a press takes hold of: the image's middle, which moves it, or one of its eight handles, which scale it. */
private sealed interface Grip {
    data object Body : Grip

    data class Handle(val at: Offset) : Grip
}

/**
 * Place image and Rotate image: in a pane looking at the trace's view, a drag on the image moves it, on one of its eight handles
 * scales it about its middle, and with [rotates] a drag anywhere turns it about its middle, each one undo step. With snapping on,
 * an upright image's bottom holds to the ground near it. A locked trace stays put.
 */
class PlaceImageTool(private val state: AppState, var look: SheetLook, val rotates: Boolean) : PaneTool {
    private fun document(): SquashDocument? = state.session.value?.document?.value

    private fun placed(camera: LockedCamera): Pair<View, Trace>? {
        val view = state.references.view
        if (viewFacing(camera.basis.back) != view) return null
        val trace = document()?.character?.traces?.get(view) ?: return null
        return view to trace
    }

    private fun corners(view: View, trace: Trace, camera: LockedCamera): List<Offset>? {
        val image = document()?.references?.image(trace.image) ?: return null
        return traceCorners(trace, view, image.width, image.height).map(camera::toPixel)
    }

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier.pointerInput(this, view, camera) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!isToolPress(down)) return@awaitEachGesture
            val (traceView, start) = placed(camera) ?: return@awaitEachGesture
            if (start.locked) return@awaitEachGesture
            val corners = corners(traceView, start, camera) ?: return@awaitEachGesture
            val grip = gripAt(corners, down.position) ?: return@awaitEachGesture
            val session = state.session.value ?: return@awaitEachGesture
            down.consume()
            val plane = PlaneMap(camera, traceView)
            val center = plane.toPixel(start.centerX, start.centerY)
            var gesture: History<SquashDocument>.Transaction? = null
            var released = false
            try {
                released = followToRelease(down.id, {}) { pixel ->
                    val moved = when {
                        rotates -> start.copy(rotation = start.rotation + angleBetween(down.position - center, pixel - center))
                        grip is Grip.Handle -> scaled(start, center, grip.at, pixel)
                        else -> shifted(start, plane, down.position, pixel)
                    }
                    val held = if (state.references.snapGround && !rotates) grounded(moved, traceView, camera, plane) else moved
                    (gesture ?: session.history.begin().also { gesture = it }).update(SetTrace(traceView, held))
                }
            } finally {
                gesture?.let { if (released) it.commit() else it.cancel() }
            }
        }
    }

    private fun gripAt(corners: List<Offset>, pixel: Offset): Grip? {
        val handles = handlesOf(corners)
        handles.firstOrNull { (it - pixel).getDistance() <= HANDLE_REACH }?.let { return Grip.Handle(it) }
        return if (rotates || inside(corners, pixel)) Grip.Body else null
    }

    private fun shifted(start: Trace, plane: PlaneMap, from: Offset, to: Offset): Trace {
        val (dx, dy) = plane.toPlane(to) - plane.toPlane(from)
        return start.copy(x = start.x + dx / start.fitUnits, y = start.y + dy / start.fitUnits)
    }

    private fun scaled(start: Trace, center: Offset, from: Offset, to: Offset): Trace {
        val reach = (from - center).getDistance()
        if (reach == 0f) return start
        return start.copy(scale = (start.scale * (to - center).getDistance() / reach).coerceAtLeast(MIN_SCALE))
    }

    /** [trace] with its bottom on the character's floor when it is upright and within reach of it in [camera]'s pixels. */
    private fun grounded(trace: Trace, view: View, camera: LockedCamera, plane: PlaneMap): Trace {
        if (view == View.TOP || trace.rotation % ROUND_TURN != 0f) return trace
        val image = document()?.references?.image(trace.image) ?: return trace
        val floor = state.session.value?.assembly?.output?.value?.floor ?: return trace
        val bottom = traceBottom(trace, image.height)
        val gap = floor - bottom
        val pixels = abs(plane.toPixel(0f, gap).y - plane.toPixel(0f, 0f).y)
        return if (pixels <= GROUND_SNAP_PIXELS) trace.copy(y = trace.y + gap / trace.fitUnits) else trace
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay {
        val (traceView, trace) = placed(camera) ?: return ViewportOverlay.Empty
        val corners = corners(traceView, trace, camera) ?: return ViewportOverlay.Empty
        val color = if (trace.locked) look.ring else look.active
        return buildOverlay {
            if (state.references.snapGround && traceView != View.TOP) {
                state.session.value?.assembly?.output?.value?.floor?.let { floor ->
                    val y = PlaneMap(camera, traceView).toPixel(0f, floor).y
                    line(Offset(0f, y), Offset(camera.width.toFloat(), y), look.active, look.activeWidth)
                }
            }
            (corners + corners.first()).zipWithNext().forEach { (a, b) -> line(a, b, color, look.activeWidth) }
            if (trace.locked) return@buildOverlay
            for (handle in handlesOf(corners)) {
                dot(handle, look.dotRadius + look.dotEdge, color)
                dot(handle, look.dotRadius, look.dotFill)
            }
        }
    }

    private companion object {
        const val ROUND_TURN = 360f
    }
}

/** The four corners and the middles of the four edges. */
private fun handlesOf(corners: List<Offset>): List<Offset> = corners + corners.indices.map { (corners[it] + corners[(it + 1) % corners.size]) / 2f }

private fun inside(corners: List<Offset>, pixel: Offset): Boolean {
    val signs = corners.indices.map { i ->
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        (b.x - a.x) * (pixel.y - a.y) - (b.y - a.y) * (pixel.x - a.x)
    }
    return signs.all { it >= 0f } || signs.all { it <= 0f }
}

/** The turn from [from] to [to] about the origin, in degrees, counterclockwise as the view's up runs up the screen. */
private fun angleBetween(from: Offset, to: Offset): Float {
    val turn = atan2(to.y, to.x) - atan2(from.y, from.x)
    return -Math.toDegrees(turn.toDouble()).toFloat()
}

/** A view's plane in a pane: body units across and up it to the pane's pixels and back, which an orthographic pane keeps straight. */
private class PlaneMap(private val camera: LockedCamera, private val view: View) {
    private val origin = camera.toPixel(planeWorld(0f, 0f, view))
    private val across = camera.toPixel(planeWorld(1f, 0f, view)) - origin
    private val up = camera.toPixel(planeWorld(0f, 1f, view)) - origin
    private val determinant = across.x * up.y - across.y * up.x

    fun toPixel(x: Float, y: Float): Offset = origin + across * x + up * y

    fun toPlane(pixel: Offset): Offset {
        val d = pixel - origin
        if (determinant == 0f) return Offset.Zero
        return Offset((d.x * up.y - d.y * up.x) / determinant, (across.x * d.y - across.y * d.x) / determinant)
    }
}

@Composable
fun rememberPlaceImageTool(state: AppState, rotates: Boolean = false): PlaceImageTool {
    val look = rememberSheetLook()
    return remember(state, rotates) { PlaceImageTool(state, look, rotates) }.also { it.look = look }
}
