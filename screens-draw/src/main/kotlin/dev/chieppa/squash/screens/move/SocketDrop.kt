package dev.chieppa.squash.screens.move

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.screens.edit.ring
import dev.chieppa.squash.screens.library.FAINT
import dev.chieppa.squash.screens.library.TemplateDrag
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.stretch.frame.DragAndDrop
import dev.chieppa.stretch.frame.DropSnap
import dev.chieppa.stretch.frame.OverlayBuilder

/** Where a snapped tile's corner sits: its bottom left, so the tile stands up and to the right of the dot and leaves it in sight. */
private val TILE_CORNER = Offset(0f, 1f)

/** One dot as the panes redraw it: the socket of [owner] it stands for, on [side]. */
private data class DotKey(val owner: PartId?, val socket: String, val side: Side?)

private fun SocketHandle.key() = DotKey(owner, socket, side)

/**
 * A Library tile's drag over Add part's dots. While it lasts, the dots that take the tile's template show large and in the accent and
 * the rest faint; near one that takes it, in [pane], the tile snaps beside that dot, which wears a ring, and a drop there adds on its
 * socket, or replaces the part on it. What a dot takes is the tool's to say.
 */
internal class SocketDrop {
    /** The window's drags, whose item lights the dots. */
    var drags: DragAndDrop? = null

    private var snapped by mutableStateOf<Pair<Any, DotKey>?>(null)

    /** The tile being dragged, while the pointer holds it. */
    fun dragged(): TemplateDrag? = drags?.item as? TemplateDrag

    /** The dot within [reach] of [pixel] that [takes]; dots that cannot take the drag are passed over. */
    fun dotFor(pixel: Offset, dots: List<SocketHandle>, takes: (SocketHandle) -> Boolean, reach: Float, view: HandleView): SocketHandle? =
        handleAt(dots.filter(takes), pixel, reach, view) as? SocketHandle

    /** Where the tile goes while it is near [dot]: beside it, clear of its ring. */
    fun snapAt(dot: SocketHandle, view: HandleView, look: SheetLook): DropSnap? {
        val at = view.toPixel(dot.position) ?: return null
        val clear = ringRadius(look) + look.ringWidth
        return DropSnap(at + Offset(clear, -clear), TILE_CORNER)
    }

    /** Notes [dot] as the one the tile is snapped to in [pane], or that it is snapped to none there. */
    fun hover(pane: Any, dot: SocketHandle?) {
        val now = dot?.let { pane to it.key() }
        if (now != null) {
            if (snapped != now) snapped = now
        } else if (snapped?.first == pane) {
            snapped = null
        }
    }

    /** [dot] in [pane] at [at] as it shows during a drag: large and accent where it [takes] the drag, ringed while snapped to, else faint. */
    fun OverlayBuilder.dragDot(pane: Any, dot: SocketHandle, at: Offset, takes: Boolean, look: SheetLook) {
        if (!takes) {
            dot(at, look.dotRadius + look.dotEdge, look.ring.faint())
            dot(at, look.dotRadius, look.dotFill.faint())
            return
        }
        if (snapped == pane to dot.key()) ring(at, ringRadius(look), look.ring, look.ringWidth)
        dot(at, look.pickedDotRadius + look.dotEdge, look.ring)
        dot(at, look.pickedDotRadius, look.active)
    }

    /** The ring's middle, a dot's edge clear of a lit dot. */
    private fun ringRadius(look: SheetLook) = look.pickedDotRadius + look.dotEdge * 2 + look.ringWidth / 2

    private fun Color.faint() = copy(alpha = alpha * FAINT)
}

/** The open socket [dot] stands for, of [sockets]. */
internal fun socketOf(dot: SocketHandle, sockets: List<OpenSocket>): OpenSocket? =
    sockets.firstOrNull { it.ref.parent == dot.owner && it.ref.socket == dot.socket }
