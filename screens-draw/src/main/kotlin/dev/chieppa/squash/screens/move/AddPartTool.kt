package dev.chieppa.squash.screens.move

import dev.chieppa.squash.screens.openSocketsNow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.document.View
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
 * Add part: a dot on every open socket, in the locked panes and Full, larger and accent on the one Add part's popover is open on.
 * Pressing a dot opens the popover there.
 */
class AddPartTool(private val state: AppState, var look: SheetLook, var curves: CurveLook) : PaneTool {
    override val takesFreeCamera: Boolean get() = true

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = presses(LockedHandleView(camera), camera)

    override fun fullInput(view: FullView): Modifier = presses(FullHandleView(view), view)

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = drawn(LockedHandleView(camera))

    override fun fullOverlay(view: FullView): ViewportOverlay = drawn(FullHandleView(view))

    private fun dots(): List<SocketHandle> {
        val character = state.session.value?.assembly?.output?.value ?: return emptyList()
        return moveHandles(character, null).filterIsInstance<SocketHandle>().filter { it.child == null }
    }

    private fun presses(handles: HandleView, key: Any): Modifier = Modifier.pointerInput(this, key) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!isToolPress(down)) return@awaitEachGesture
            val dot = handleAt(dots(), down.position, curves.grabReach, handles) as? SocketHandle ?: return@awaitEachGesture
            down.consume()
            state.openSocketsNow().firstOrNull { it.ref.parent == dot.parent.id && it.ref.socket == dot.socket }?.let(state::offerSocket)
        }
    }

    private fun drawn(view: HandleView): ViewportOverlay = buildOverlay {
        val adding = state.addingSocket.value?.ref
        for (dot in dots()) {
            val at = view.toPixel(dot.position) ?: continue
            val picked = adding != null && adding.parent == dot.parent.id && adding.socket == dot.socket
            val radius = if (picked) look.pickedDotRadius else look.dotRadius
            dot(at, radius + look.dotEdge, look.ring)
            dot(at, radius, if (picked) look.active else look.dotFill)
        }
    }
}

@Composable
fun rememberAddPartTool(state: AppState): AddPartTool {
    val look = rememberSheetLook()
    val curves = rememberCurveLook()
    return remember(state) { AddPartTool(state, look, curves) }.also {
        it.look = look
        it.curves = curves
    }
}
