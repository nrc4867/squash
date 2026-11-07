package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SeamStroke
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the rig reads from the document beside the assembly: each part's joint reaches by joint name, and each seam's strokes by its child. */
data class RigInput(val reach: Map<PartId, Map<String, Float>> = emptyMap(), val seams: Map<PartId, List<SeamStroke>> = emptyMap())

/** What [character] gives its rig: the reaches and seam strokes each part resolves to, its side's reading of its pair's. */
fun rigInput(character: Character): RigInput {
    val reach = character.parts.associate { part ->
        part.id to character.resolved(part).params.mapNotNull { (name, value) -> RigParams.reachJoint(name)?.let { it to value } }.toMap()
    }.filterValues { it.isNotEmpty() }
    val seams = character.parts.associate { it.id to character.resolved(it).surface.seams }.filterValues { it.isNotEmpty() }
    return RigInput(reach, seams)
}

/** The one source a rig node reads. */
private object AssemblySource

/**
 * The rig of a session's character, a node of the session's graph gathering its assembly and what the document gives the rig, so
 * its `settled` covers it and a stroke or a reach reruns it with no mesh. Its `output` keeps the last rig while a newer run is pending.
 */
class SessionRig(session: CharacterSession) {
    val rig: Node<*, Rig> = session.graph.gather(
        session.document.map { rigInput(it.character) }.distinctUntilChanged(),
        { mapOf(AssemblySource to session.assembly) },
    ) { gathered ->
        val assembled = gathered.values.getValue(AssemblySource)
        val input = gathered.shape
        rigOf(assembled, StrokePaint(assembled, input.seams), cancellationCheck(), JointReach { part, joint -> input.reach[part]?.get(joint) ?: 1f })
    }
}
