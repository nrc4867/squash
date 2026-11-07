package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The one source a posed node reads. */
private object RigSource

/**
 * The session's character in its pose: a node of the session's graph after [rig], rerun when the rig or the character's pose
 * changes, so its `settled` covers it. Its `output` keeps the last posed character while a newer run is pending.
 * See HLD: architecture, The graph.
 */
class SessionPose(session: CharacterSession, val rig: SessionRig = SessionRig(session)) {
    val posed: Node<*, PosedCharacter> = session.graph.gather(
        session.document.map { it.character.pose }.distinctUntilChanged(),
        { mapOf(RigSource to rig.rig) },
    ) { gathered -> gathered.values.getValue(RigSource).posed(gathered.shape) }
}
