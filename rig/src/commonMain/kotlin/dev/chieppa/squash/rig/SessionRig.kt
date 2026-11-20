package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SeamStroke
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The one source the body's rig node reads. */
private object AssemblySource

/** What the whole rig reads: the body's rig and the assembly with its lock groups. */
private enum class WholeRigSource { BODY, ASSEMBLY }

private sealed interface WholeRigInput {
    class Body(val rig: Rig) : WholeRigInput

    class Assembly(val character: AssembledCharacter) : WholeRigInput
}

/**
 * The rig of a session's character, a node of the session's graph gathering its assembly and what the document gives the rig, so
 * its `settled` covers it and a stroke or a reach reruns it with no mesh. The body is rigged from the assembly without its lock groups and
 * the locks' chains added after, so an edit of the hair rigs only the hair. Its `output` keeps the last rig while a newer run is pending.
 */
class SessionRig(session: CharacterSession) {
    private val body: Node<*, Rig> = session.graph.gather(
        session.document.map { rigInput(it.character) }.distinctUntilChanged(),
        { mapOf(AssemblySource to session.bodyAssembly) },
    ) { gathered ->
        val assembled = gathered.values.getValue(AssemblySource)
        val input = gathered.shape
        rigOf(assembled, StrokePaint(assembled, input.seams), cancellationCheck(), JointReach { part, joint -> input.reach[part]?.get(joint) ?: 1f })
    }

    private val bodyInput = session.graph.follow<Rig, WholeRigInput>(body) { WholeRigInput.Body(it) }
    private val assemblyInput = session.graph.follow<AssembledCharacter, WholeRigInput>(session.assembly) { WholeRigInput.Assembly(it) }

    val rig: Node<*, Rig> = session.graph.gather(
        MutableStateFlow(Unit),
        { mapOf(WholeRigSource.BODY to bodyInput, WholeRigSource.ASSEMBLY to assemblyInput) },
    ) { gathered ->
        val rig = (gathered.values.getValue(WholeRigSource.BODY) as WholeRigInput.Body).rig
        rig.withLocks((gathered.values.getValue(WholeRigSource.ASSEMBLY) as WholeRigInput.Assembly).character)
    }
}
