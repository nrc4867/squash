package dev.chieppa.squash.screens.move

import dev.chieppa.squash.screens.openSocketsNow
import dev.chieppa.squash.screens.addSocket
import dev.chieppa.squash.screens.offerPickedSocket
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.character.build.AssembledCharacter
import androidx.compose.ui.geometry.Offset
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
import dev.chieppa.stretch.frame.LocalDragAndDrop
import dev.chieppa.stretch.frame.dropTarget
import dev.chieppa.squash.screens.addPart
import dev.chieppa.squash.screens.library.TemplateDrag
import dev.chieppa.stretch.frame.buildOverlay

/**
 * Add part: a dot on every open socket, in the locked panes and Full, larger and accent on the one Library targets.
 * Pressing a dot targets its socket in Library. While a Library tile drags, the dots whose sockets take it are large and accent and
 * the rest faint; near one that takes it the tile snaps beside it, and dropping it there adds it on that socket. A press on a part's
 * surface away from the dots places a hand-made socket there, with the Move tool's outward angle and pair flag and previewed under
 * the pointer as Add socket previews it, and targets it in Library.
 */
class AddPartTool(private val state: AppState, var look: SheetLook, var curves: CurveLook) : PaneTool {
    /** Where a press would place a socket. */
    private val hover = SocketHover()

    /** A Library tile's drag over the dots. */
    internal val drop = SocketDrop()

    override val takesFreeCamera: Boolean get() = true

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = presses(view, camera, LockedHandleView(camera), camera)

    override fun fullInput(view: FullView): Modifier = presses(FULL, view, FullHandleView(view), view.width to view.height)

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = drawn(view, LockedHandleView(camera)) + preview(view, camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = drawn(FULL, FullHandleView(view)) + preview(FULL, view)

    private fun assembled(): AssembledCharacter? = state.session.value?.assembly?.output?.value


    private fun dots(): List<SocketHandle> {
        val character = assembled() ?: return emptyList()
        return moveHandles(character, null).filterIsInstance<SocketHandle>().filter { it.child == null }
    }

    private fun presses(pane: Any, sight: SurfaceView, handles: HandleView, key: Any): Modifier = Modifier
        .pointerInput(this, pane, key) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!isToolPress(down)) return@awaitEachGesture
                val dot = handleAt(dots(), down.position, curves.grabReach, handles) as? SocketHandle
                if (dot != null) {
                    down.consume()
                    socketOf(dot, state.openSocketsNow())?.let(state::offerSocket)
                    return@awaitEachGesture
                }
                val place = placeAt(down.position, sight, handles) ?: return@awaitEachGesture
                down.consume()
                state.addSocket(place.part, place.u, place.v, place.onRight)
                state.offerPickedSocket()
            }
        }
        .then(hover.track(this, pane, key, { true }) { pointer -> placeAt(pointer, sight, handles) })
        .dropTarget<TemplateDrag>(
            accepts = { true },
            onHover = { drag, at -> drop.hover(pane, drag?.let { dotFor(it, at, handles) }) },
            snap = { drag, at -> dotFor(drag, at, handles)?.let { drop.snapAt(it, handles, look) } },
            onDrop = { drag, at -> dropOn(drag, at, handles) },
        )

    /** The dot in reach of [at] whose socket takes [drag], the one the tile snaps to. */
    private fun dotFor(drag: TemplateDrag, at: Offset, handles: HandleView): SocketHandle? =
        drop.dotFor(drag, at, dots(), state.openSocketsNow(), curves.grabReach, handles)

    /** Adds a tile dragged from Library on the dot it snapped to, where that socket takes it; false sends the tile back. */
    private fun dropOn(drag: TemplateDrag, at: Offset, handles: HandleView): Boolean {
        val dot = dotFor(drag, at, handles) ?: return false
        val socket = socketOf(dot, state.openSocketsNow()) ?: return false
        state.offerSocket(socket)
        state.addPart(drag.template)
        return true
    }

    /** Where a press at [pixel] places a socket: on a part's surface, away from every dot. */
    private fun placeAt(pixel: Offset, sight: SurfaceView, handles: HandleView): SocketPlace? {
        if (handleAt(dots(), pixel, curves.grabReach, handles) != null) return null
        return state.socketPlaceAt(assembled(), pixel, sight, handles)
    }

    private fun preview(pane: Any, sight: SurfaceView): ViewportOverlay = buildOverlay {
        if (state.addingSocket.value != null) return@buildOverlay
        val character = assembled() ?: return@buildOverlay
        hover.placeIn(pane)?.let { socketPreview(state, character, it, sight, look) }
    }

    private fun drawn(pane: Any, view: HandleView): ViewportOverlay = buildOverlay {
        val dragged = drop.dragged()
        val sockets = if (dragged != null) state.openSocketsNow() else emptyList()
        val adding = state.addingSocket.value?.ref
        for (dot in dots()) {
            val at = view.toPixel(dot.position) ?: continue
            if (dragged != null) {
                with(drop) { dragDot(pane, dot, at, dragged, sockets, look) }
                continue
            }
            val picked = adding != null && adding.parent == dot.owner && adding.socket == dot.socket
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
    val drags = LocalDragAndDrop.current
    return remember(state) { AddPartTool(state, look, curves) }.also {
        it.look = look
        it.curves = curves
        it.drop.drags = drags
    }
}
