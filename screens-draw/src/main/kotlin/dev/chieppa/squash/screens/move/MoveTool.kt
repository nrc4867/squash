package dev.chieppa.squash.screens.move

import dev.chieppa.squash.screens.openSocketsNow
import dev.chieppa.squash.screens.addSocket
import dev.chieppa.squash.screens.pickSocket
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.screens.addJoint
import dev.chieppa.squash.screens.addLandmark
import dev.chieppa.squash.screens.jointPlace
import dev.chieppa.squash.screens.pickJoint
import dev.chieppa.squash.screens.sheet.SurfaceView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.edit.CurveLook
import dev.chieppa.squash.screens.edit.rememberCurveLook
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/**
 * Move: a dot on every socket and on the selected part's joints, in the locked panes and Full. Dragging a socket's dot slides
 * it over its parent's surface, and a joint's along its part, as one undo step, on the pair or with Mirror off on that side.
 * Pressing a socket's dot without moving selects the part on it, or targets an empty one in Library, and picks a hand-made
 * one. In Add socket a press on a part's surface places a socket there, previewed under the pointer, and a press on a hand-made
 * socket's dot picks it. In Add joint a press on a part places a joint at that u on its axis, and a press on a dot of the selected
 * part's joints picks it, and dragging it moves it along its part; in Add landmark a press places a landmark there. Every drag is a
 * [HandleDrag], held to an axis, slowed and snapped as it says. Arrow keys nudge the selected part's socket.
 */
class MoveTool(private val state: AppState, var look: SheetLook, var curves: CurveLook, var guides: DragGuideLook) : PaneTool {
    private var dragging by mutableStateOf(false)

    /** The drag of a socket's or a joint's dot. */
    private val drag = HandleDrag(state)

    /** Where Add socket, Add joint or Add landmark would place under the pointer. */
    private val hover = SocketHover()

    override val holdsFit: Boolean get() = dragging

    override val takesFreeCamera: Boolean get() = true

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = presses(view, camera, LockedHandleView(camera), camera)

    override fun fullInput(view: FullView): Modifier = presses(FULL, view, FullHandleView(view), view.width to view.height)

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay =
        drawn(LockedHandleView(camera)) + preview(view, camera) + dragGuides(drag.shown, view, guides)

    override fun fullOverlay(view: FullView): ViewportOverlay = drawn(FullHandleView(view)) + preview(FULL, view) + dragGuides(drag.shown, FULL, guides)

    private fun presses(pane: Any, sight: SurfaceView, handles: HandleView, key: Any): Modifier = Modifier
        .pointerInput(this, pane, key) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (dragging || !isToolPress(down)) return@awaitEachGesture
                val handle = handleAt(handles(), down.position, curves.grabReach, handles)
                val mode = state.move.mode.value
                if (mode == MoveMode.ADD_SOCKET && handle is SocketHandle && HandSockets.idOf(handle.socket) != null) {
                    down.consume()
                    handle.owner?.let { state.pickSocket(it, handle.socket) }
                    return@awaitEachGesture
                }
                if (mode == MoveMode.ADD_JOINT) {
                    val joint = jointDotAt(down.position, handles)
                    if (joint != null) {
                        down.consume()
                        joint.first.id?.let { state.pickJoint(it, joint.second) }
                        val movable = handles().filterIsInstance<JointHandle>().firstOrNull { it.part.id == joint.first.id && it.name == joint.second }
                        if (movable != null) drag(movable, down.id, down.position, pane, handles)
                        return@awaitEachGesture
                    }
                }
                if (mode != MoveMode.PLACE_PART) {
                    val place = placeAt(down.position, sight, handles) ?: return@awaitEachGesture
                    val part = place.part.id ?: return@awaitEachGesture
                    down.consume()
                    when (mode) {
                        MoveMode.ADD_SOCKET -> state.addSocket(place.part, place.u, place.v, place.onRight, place.digit)
                        MoveMode.ADD_JOINT -> state.addJoint(part, place.u)
                        else -> state.addLandmark(part, place.u, place.v)
                    }
                    return@awaitEachGesture
                }
                handle ?: return@awaitEachGesture
                down.consume()
                drag(handle, down.id, down.position, pane, handles)
            }
        }
        .then(hover.track(this, pane, key, { state.move.mode.value != MoveMode.PLACE_PART }) { pointer -> placeAt(pointer, sight, handles) })

    /**
     * What a press would place under the pointer in this [pane]: a socket and its twin while Pair is on, a joint on the axis with the
     * selected part's joints on its dashed axis, or a landmark with the selected part's landmarks.
     */
    private fun preview(pane: Any, sight: SurfaceView): ViewportOverlay = buildOverlay {
        val mode = state.move.mode.value
        val character = assembled() ?: return@buildOverlay
        val selected = state.selectedPart.value?.let(character::part)
        val place = hover.placeIn(pane)
        if (mode == MoveMode.ADD_JOINT) {
            if (selected != null) {
                dashedAxis(selected, sight, look)
                val picked = state.move.pickedJoint.value
                for (joint in selected.template.blocks.flatMap { it.joints() }) {
                    jointDot(selected, joint.u, sight, look, picked?.part == selected.id && picked?.joint == joint.name)
                }
            }
            place?.let { jointMark(it.part, jointPlace(it.u, emptyList()) ?: it.u, sight, look) }
            return@buildOverlay
        }
        if (mode == MoveMode.ADD_LANDMARK) {
            selected?.template?.blocks?.flatMap { it.landmarks() }?.forEach { landmarkDot(selected, it.u, it.v, sight, look, look.dotFill) }
            place?.let { landmarkDot(it.part, it.u, it.v, sight, look, look.active) }
            return@buildOverlay
        }
        if (mode == MoveMode.ADD_SOCKET && place != null) socketPreview(state, character, place, sight, look)
    }

    private fun assembled(): AssembledCharacter? = state.session.value?.assembly?.output?.value

    /** The socket place at [pixel] on the settled surface, or null where none shows or the surface is not yet built. */
    private fun placeAt(pixel: Offset, sight: SurfaceView, handles: HandleView): SocketPlace? = state.socketPlaceAt(assembled(), pixel, sight, handles)

    /** The selected part's joint whose dot shows under [pixel], within reach, as the part and the joint's name. */
    private fun jointDotAt(pixel: Offset, view: HandleView): Pair<PlacedPart, String>? {
        val part = state.selectedPart.value?.let { assembled()?.part(it) } ?: return null
        return part.template.blocks.flatMap { it.joints() }.mapNotNull { joint ->
            val at = axisMiddle(part, joint.u)?.let(view::toPixel) ?: return@mapNotNull null
            (at - pixel).getDistance().takeIf { it <= curves.grabReach }?.let { joint.name to it }
        }.minByOrNull { it.second }?.let { part to it.first }
    }

    private fun handles(): List<MoveHandle> = assembled()?.let { moveHandles(it, state.selectedPart.value) }.orEmpty()

    private fun drawn(view: HandleView): ViewportOverlay = buildOverlay {
        val selected = state.selectedPart.value
        val socket = state.move.picked.value
        for (handle in handles()) {
            if (handle is JointHandle && state.move.mode.value == MoveMode.ADD_JOINT) continue
            val at = view.toPixel(handle.position) ?: continue
            val held = handle is SocketHandle && handle.child != null && handle.child == selected
            val picked = held || (handle is SocketHandle && handle.child == null && handle.owner == socket?.parent && handle.socket == socket?.socket)
            val radius = if (picked) look.pickedDotRadius else look.dotRadius
            dot(at, radius + look.dotEdge, if (handle is JointHandle) look.active else look.ring)
            dot(at, radius, if (picked) look.active else look.dotFill)
        }
    }

    private suspend fun AwaitPointerEventScope.drag(handle: MoveHandle, id: PointerId, start: Offset, pane: Any, view: HandleView) {
        dragging = true
        try {
            with(drag) { follow(handle, id, start, pane, view) { pressed(handle) } }
        } finally {
            dragging = false
        }
    }

    private fun pressed(handle: MoveHandle) {
        if (handle !is SocketHandle) return
        handle.owner?.let { state.pickSocket(it, handle.socket) }
        handle.child?.let {
            state.selectPart(it)
            return
        }
        state.openSocketsNow().firstOrNull { it.ref.parent == handle.owner && it.ref.socket == handle.socket }?.let(state::offerSocket)
    }

}

@Composable
fun rememberMoveTool(state: AppState): MoveTool {
    val look = rememberSheetLook()
    val curves = rememberCurveLook()
    val guides = rememberDragGuideLook()
    return remember(state) { MoveTool(state, look, curves, guides) }.also {
        it.look = look
        it.curves = curves
        it.guides = guides
    }
}
