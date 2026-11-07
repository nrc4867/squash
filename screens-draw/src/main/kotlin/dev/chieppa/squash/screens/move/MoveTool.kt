package dev.chieppa.squash.screens.move

import dev.chieppa.squash.screens.openSocketsNow
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
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.moveSocket
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.edit.CurveLook
import dev.chieppa.squash.screens.edit.rememberCurveLook
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/**
 * Move: a dot on every socket and on the selected part's joints, in the locked panes and Full. Dragging a socket's dot slides
 * it over its parent's surface, and a joint's along its part, as one undo step, on the pair or with Mirror off on that side.
 * Pressing a socket's dot without moving selects the part on it, or opens Add part's popover on an empty one. A drag the pane loses
 * restores what was there.
 */
class MoveTool(private val state: AppState, var look: SheetLook, var curves: CurveLook) : PaneTool {
    private var dragging by mutableStateOf(false)

    override val holdsFit: Boolean get() = dragging

    override val takesFreeCamera: Boolean get() = true

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier.pointerInput(this, view, camera) {
        val handles = LockedHandleView(camera)
        awaitEachGesture {
            val down = awaitFirstDown()
            if (dragging || !isToolPress(down)) return@awaitEachGesture
            val handle = handleAt(handles(), down.position, curves.grabReach, handles) ?: return@awaitEachGesture
            down.consume()
            drag(handle, down.id, down.position, handles)
        }
    }

    override fun fullInput(view: FullView): Modifier = Modifier.pointerInput(this, view.width, view.height) {
        val handles = FullHandleView(view)
        awaitEachGesture {
            val down = awaitFirstDown()
            if (dragging || !isToolPress(down)) return@awaitEachGesture
            val handle = handleAt(handles(), down.position, curves.grabReach, handles) ?: return@awaitEachGesture
            down.consume()
            drag(handle, down.id, down.position, handles)
        }
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = drawn(LockedHandleView(camera))

    override fun fullOverlay(view: FullView): ViewportOverlay = drawn(FullHandleView(view))

    private fun assembled(): AssembledCharacter? = state.session.value?.assembly?.output?.value

    private fun handles(): List<MoveHandle> = assembled()?.let { moveHandles(it, state.selectedPart.value) }.orEmpty()

    private fun drawn(view: HandleView): ViewportOverlay = buildOverlay {
        val selected = state.selectedPart.value
        for (handle in handles()) {
            val at = view.toPixel(handle.position) ?: continue
            val picked = handle is SocketHandle && handle.child != null && handle.child == selected
            val radius = if (picked) look.pickedDotRadius else look.dotRadius
            dot(at, radius + look.dotEdge, if (handle is JointHandle) look.active else look.ring)
            dot(at, radius, if (picked) look.active else look.dotFill)
        }
    }

    private suspend fun AwaitPointerEventScope.drag(handle: MoveHandle, id: PointerId, start: Offset, view: HandleView) {
        val session = state.session.value ?: return
        dragging = true
        var gesture: History<SquashDocument>.Transaction? = null
        var released = false
        try {
            var moved = false
            released = followToRelease(id, {}) { pixel ->
                moved = moved || (pixel - start).getDistance() > viewConfiguration.touchSlop
                if (!moved) return@followToRelease
                val command = commandFor(session, handle, pixel, view) ?: return@followToRelease
                val open = gesture ?: session.history.begin().also { gesture = it }
                open.update(command)
            }
            if (released && !moved) pressed(handle)
        } finally {
            dragging = false
            val open = gesture
            if (open != null) if (released) open.commit() else open.cancel()
        }
    }

    private fun commandFor(session: CharacterSession, handle: MoveHandle, pixel: Offset, view: HandleView): DocumentCommand? = when (handle) {
        is SocketHandle -> surfaceUnder(handle.parent, pixel, view)?.let { place ->
            val parent = handle.parent.id ?: return null
            moveSocket(session.document.value.character, parent, handle.socket, handle.side, place.u, place.v, handle.parent.mirrored, state.thisSide)
        }
        is JointHandle -> {
            val part = handle.part.id ?: return null
            val thisSide = state.thisSide && session.document.value.character.part(part)?.pair != null
            SetParameter(part, PlacementParams.joint(handle.name), jointUnder(handle, pixel, view), thisSide)
        }
    }

    private fun pressed(handle: MoveHandle) {
        if (handle !is SocketHandle) return
        if (handle.child != null) {
            state.selectPart(handle.child)
            return
        }
        state.openSocketsNow().firstOrNull { it.ref.parent == handle.parent.id && it.ref.socket == handle.socket }?.let(state::offerSocket)
    }

}

@Composable
fun rememberMoveTool(state: AppState): MoveTool {
    val look = rememberSheetLook()
    val curves = rememberCurveLook()
    return remember(state) { MoveTool(state, look, curves) }.also {
        it.look = look
        it.curves = curves
    }
}
