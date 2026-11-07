package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck

/**
 * The rig of a session's character, a node of the session's graph following its assembly, so its `settled` covers it.
 * Its `output` keeps the last rig while a newer run is pending. [paint] says what painted seam strokes make of the seams.
 */
class SessionRig(session: CharacterSession, paint: SeamPaint = SeamPaint.NONE) {
    val rig: Node<AssembledCharacter, Rig> = session.graph.follow(session.assembly) { rigOf(it, paint, cancellationCheck()) }
}
