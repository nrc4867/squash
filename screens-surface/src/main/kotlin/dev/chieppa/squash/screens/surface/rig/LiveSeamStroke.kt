package dev.chieppa.squash.screens.surface.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.SeamBrush
import dev.chieppa.squash.document.SeamStroke
import dev.chieppa.squash.document.SeamStrokeId
import dev.chieppa.squash.document.SetSeamStroke
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.Ulid
import dev.chieppa.stretch.core.History
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A seam stroke being laid down on [target]'s seam, open on the session's history until [finish], so the drag is one undo step. The
 * rig redoes the weights one place at a time: a new place waits for the graph to settle, so a slow run still shows while the
 * pointer moves.
 */
internal class LiveSeamStroke(
    private val session: CharacterSession,
    private val target: SeamTarget,
    private val brush: SeamBrush,
    private val radius: Float,
    private val strength: Float,
    private val thisSide: Boolean,
    private val scope: CoroutineScope,
) {
    private val id = SeamStrokeId(Ulid.next())
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
        val side = thisSide && session.document.value.character.part(target.child)?.pair != null
        val toward = if (brush == SeamBrush.PAINT) target.toward else null
        gesture.update(SetSeamStroke(target.child, id, SeamStroke(id, anchors.toList(), brush, toward, radius, strength), side))
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
