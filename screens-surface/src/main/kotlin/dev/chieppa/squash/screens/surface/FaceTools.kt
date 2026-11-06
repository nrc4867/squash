package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PART_INK
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch

/** How close a press takes a mark's place or line, in density independent pixels. */
private const val REACH = 10f

private const val LIVE_WIDTH = 2f
private const val HANDLE_SIZE = 4f
private const val TARGET_RADIUS = 9f
private const val TARGET_ARM = 13f

/** A tool working on the picked mark group and role, on the part holding the group. */
abstract class FaceTool(state: AppState) : SurfaceTool(state) {
    protected val picked: PickedGroup? get() = state.face.group
    protected val role: MarkRole? get() = state.face.role

    /** The placed part holding the picked group. */
    protected fun head(): PlacedPart? = picked?.let { state.session.value?.assembly?.output?.value?.part(it.part) }

    /** The version the tool works in through [sight]: the one picked, else the one the view shows. */
    protected fun binThrough(sight: SurfaceView): CameraBin? =
        state.face.version ?: head()?.let { FACE_BINS.binFor(headAngle(it, sight.toViewer(it.box?.center ?: it.origin))) }

    /** Whether Mirror writes the other eye too. */
    protected val mirrors: Boolean get() = state.mirror.value

    /** Where the places of what the role draws in [bin] show in body space. */
    protected fun placesIn(bin: CameraBin): List<Vec3> {
        val session = state.session.value ?: return emptyList()
        val character = session.document.value.character
        val group = picked?.let(character::groupOf) ?: return emptyList()
        val at = picked?.part ?: return emptyList()
        val part = head() ?: return emptyList()
        val mark = role?.let { character.markIn(group, it, at) } ?: return emptyList()
        return (mark.versions[bin]?.points ?: mark.points).mapNotNull { anchor ->
            part.surface.patches.firstOrNull { it.name == anchor.patch }?.let { part.origin + part.surface.shown(it.positionAt(anchor.u, anchor.v)) }
        }
    }

    protected fun SurfaceView.pixelsOf(places: List<Vec3>): List<Offset?> = places.map(::toPixel)
}

/** Draw mark: a drag across the picked group's part draws what the picked role draws, in the version shown, in the expression shown. */
class DrawMarkTool(state: AppState, var ink: Color, var lineWidth: Float) : FaceTool(state) {
    private val live = mutableStateListOf<Vec3>()

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val group = picked ?: return
        val drawing = role ?: return
        if (hit.part != group.part) return
        val bin = binThrough(sight) ?: return
        val anchors = arrayListOf(hit.anchor)
        live += hit.position
        try {
            val released = followSurface(hit, down, sight) { next ->
                if (next.part == group.part && next.anchor.patch == hit.anchor.patch) {
                    anchors += next.anchor
                    live += next.position
                }
            }
            if (released) drawnMarkCommand(session.document.value.character, group, drawing, anchors, bin, mirrors)?.let(session::send)
        } finally {
            live.clear()
        }
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        live.mapNotNull { sight.toPixel(it) }.zipWithNext().forEach { (from, to) -> line(from, to, ink, lineWidth) }
    }
}

/** Edit curves on a mark: the picked role's places show as handles in the version shown, and a drag moves one over the surface as one undo step. */
class EditMarkTool(state: AppState, private val reach: Float, var color: Color, private val size: Float) : FaceTool(state) {
    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean {
        val session = state.session.value ?: return false
        val group = picked ?: return false
        val editing = role ?: return false
        val bin = binThrough(sight) ?: return false
        val index = sight.pixelsOf(placesIn(bin)).withIndex()
            .mapNotNull { (i, pixel) -> pixel?.let { i to (it - down.position).getDistance() } }
            .filter { it.second <= reach }
            .minByOrNull { it.second }?.first ?: return false
        down.consume()
        val gesture = session.history.begin()
        val released = followToRelease(down.id, {}) { pixel ->
            val hit = hitAt(pixel, sight)?.takeIf { it.part == group.part } ?: return@followToRelease
            movedPlaceCommand(session.document.value.character, group, editing, bin, index, hit.anchor, mirrors)?.let(gesture::update)
        }
        if (released) gesture.commit() else gesture.cancel()
        return true
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        val bin = binThrough(sight) ?: return@buildOverlay
        val half = size
        sight.pixelsOf(placesIn(bin)).filterNotNull().forEach { at ->
            line(at + Offset(-half, 0f), at + Offset(half, 0f), color, half)
        }
    }
}

/** Erase on a mark: a press on what the picked role draws takes it from the version shown. */
class EraseMarkTool(state: AppState, private val reach: Float) : FaceTool(state) {
    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val group = picked ?: return
        val erasing = role ?: return
        val bin = binThrough(sight) ?: return
        val near = sight.pixelsOf(placesIn(bin)).filterNotNull().any { (it - down.position).getDistance() <= reach }
        if (near) erasedMarkCommand(session.document.value.character, group, erasing, bin)?.let(session::send)
    }
}

/** Gaze target: a press anywhere in a pane puts the test target there and a drag moves it, on the plane through it that faces the viewer. It is not part of the character. */
class GazeTargetTool(state: AppState, var color: Color, private val density: Float) : FaceTool(state) {
    private var dragging by mutableStateOf(false)

    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean {
        val part = head() ?: return false
        val from = state.face.gazeTarget ?: part.box?.center ?: part.origin
        fun moveTo(pixel: Offset) {
            pointFacingViewer(from, pixel, sight)?.let {
                state.face.gazeTarget = it
                state.face.gazeOn = true
            }
        }
        down.consume()
        dragging = true
        moveTo(down.position)
        followToRelease(down.id, {}, ::moveTo)
        dragging = false
        return true
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        if (!state.face.gazeOn) return@buildOverlay
        val target = state.face.gazeTarget ?: return@buildOverlay
        val at = sight.toPixel(target) ?: return@buildOverlay
        val width = LIVE_WIDTH * density
        val arm = TARGET_ARM * density
        val radius = TARGET_RADIUS * density
        line(at + Offset(-arm, 0f), at + Offset(arm, 0f), color, width)
        line(at + Offset(0f, -arm), at + Offset(0f, arm), color, width)
        val steps = 24
        val ring = List(steps + 1) { i ->
            val angle = 2.0 * Math.PI * i / steps
            at + Offset(radius * kotlin.math.cos(angle).toFloat(), radius * kotlin.math.sin(angle).toFloat())
        }
        ring.zipWithNext().forEach { (a, b) -> line(a, b, color, width) }
    }
}

@Composable
fun rememberDrawMarkTool(state: AppState): DrawMarkTool {
    val width = LIVE_WIDTH * LocalDensity.current.density
    return remember(state) { DrawMarkTool(state, PART_INK, width) }.also { it.lineWidth = width }
}

@Composable
fun rememberEditMarkTool(state: AppState): EditMarkTool {
    val density = LocalDensity.current.density
    val accent = Stretch.colors.accent
    return remember(state, density) { EditMarkTool(state, REACH * density, accent, HANDLE_SIZE * density) }.also { it.color = accent }
}

@Composable
fun rememberEraseMarkTool(state: AppState): EraseMarkTool {
    val density = LocalDensity.current.density
    return remember(state, density) { EraseMarkTool(state, REACH * density) }
}

@Composable
fun rememberGazeTargetTool(state: AppState): GazeTargetTool {
    val density = LocalDensity.current.density
    val accent = Stretch.colors.accent
    return remember(state, density) { GazeTargetTool(state, accent, density) }.also { it.color = accent }
}
