package dev.chieppa.squash.screens.surface.paint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.Ulid
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** How far the pointer moves on screen before a stroke takes another anchor, in pixels. */
private const val ANCHOR_SPACING = 4f

private const val RING_POINTS = 48
private const val TWO_PI = 6.2831855f

/**
 * Lays down a brush stroke as one undo step, in anchors on whatever surface the pointer passes over, starting on the part that will hold it.
 * The stroke's color arrives through the graph, one recut at a time: a new position waits for the last cut to finish, so a slow cut still
 * shows while the pointer moves, and the ring under the pointer is drawn at once.
 */
@Stable
class BrushTool(state: AppState, private val scope: CoroutineScope, internal var ringColor: Color = Color.Unspecified, internal var ringWidth: Float = 0f) : SurfaceTool(state) {
    private var ringAt by mutableStateOf<Pair<Vec3, Float>?>(null)

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: PaneSight) {
        val session = state.session.value ?: return
        val slot = state.paint.slotIn(session.document.value.character.palette) ?: return
        val live = LiveStroke(session, hit.part, slot, state.paint.brushRadius, state.paint.softness, state.thisSide, scope)
        var last = down.position
        var lastHit = hit
        live.add(hit.anchor)
        showRing(hit, sight)
        var released = false
        try {
            released = followToRelease(down.id, {}) { pixel ->
                if ((pixel - last).getDistance() < ANCHOR_SPACING) return@followToRelease
                val next = hitAt(pixel, sight) ?: return@followToRelease
                crossingBetween(lastHit, last, next, pixel, sight).forEach { live.add(it.anchor) }
                last = pixel
                lastHit = next
                live.add(next.anchor)
                showRing(next, sight)
            }
        } finally {
            ringAt = null
            live.finish(released)
        }
    }

    private fun showRing(hit: SurfaceHit, sight: PaneSight) {
        val length = state.session.value?.assembly?.output?.value?.part(hit.part)?.surface?.axisLength ?: return
        ringAt = hit.position to state.paint.brushRadius * length * sight.pixelsPerUnitAt(hit.position)
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = ring(LockedSight(camera))

    override fun fullOverlay(view: FullView): ViewportOverlay = ring(FullSight(view))

    private fun ring(sight: PaneSight): ViewportOverlay {
        val (at, radius) = ringAt ?: return ViewportOverlay.Empty
        val center = sight.toPixel(at) ?: return ViewportOverlay.Empty
        return buildOverlay {
            val path = List(RING_POINTS + 1) { i ->
                val angle = TWO_PI * i / RING_POINTS
                center + Offset(cos(angle), sin(angle)) * radius
            }
            path.zipWithNext().forEach { (a, b) -> line(a, b, ringColor, ringWidth) }
        }
    }
}

/** A stroke being laid down on [holder], open on the session's history until [finish]. */
private class LiveStroke(
    private val session: CharacterSession,
    private val holder: PartId,
    private val slot: SlotName,
    private val radius: Float,
    private val softness: Float,
    private val thisSide: Boolean,
    private val scope: CoroutineScope,
) {
    private val id = BrushStrokeId(Ulid.next())
    private val anchors = ArrayList<Anchor>()
    private val gesture: History<SquashDocument>.Transaction = session.history.begin()
    private var waiting: Job? = null
    private var sent = 0

    fun add(anchor: Anchor) {
        anchors += anchor
        if (waiting != null) return
        send()
        waiting = scope.launch {
            session.graph.settled.first { it }
            waiting = null
            if (anchors.size > sent) add(anchors.removeLast())
        }
    }

    private fun send() {
        sent = anchors.size
        val side = thisSide && session.document.value.character.part(holder)?.pair != null
        gesture.update(SetBrushStroke(holder, id, BrushStroke(id, anchors.toList(), slot, radius, softness), side))
    }

    fun finish(keep: Boolean) {
        waiting?.cancel()
        if (!keep) {
            gesture.cancel()
            return
        }
        if (anchors.size > sent) send()
        gesture.commit()
    }
}

@Composable
fun rememberBrushTool(state: AppState): BrushTool {
    val look = rememberSheetLook()
    val scope = rememberCoroutineScope()
    return remember(state) { BrushTool(state, scope) }.also {
        it.ringColor = look.ring
        it.ringWidth = look.guideWidth
    }
}
