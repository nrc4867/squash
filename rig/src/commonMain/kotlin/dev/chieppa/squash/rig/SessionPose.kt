package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The one source a posed node reads. */
private object RigSource

/**
 * The session's character in its pose: a node of the session's graph after [rig], rerun when the rig or the shown pose changes,
 * so its `settled` covers it. The shown pose is [held] while it holds one, a pose a view shows for a moment without changing the
 * document, and the character's own otherwise. Its `output` keeps the last posed character while a newer run is pending.
 * See HLD: architecture, The graph.
 */
class SessionPose(session: CharacterSession, val rig: SessionRig = SessionRig(session), val held: StateFlow<Pose?> = MutableStateFlow(null)) {
    val posed: Node<*, PosedCharacter> = session.graph.gather(
        combine(session.document.map { it.character.pose }, held) { own, shown -> shown ?: own }.distinctUntilChanged(),
        { mapOf(RigSource to rig.rig) },
    ) { gathered -> gathered.values.getValue(RigSource).posed(gathered.shape) }
}
