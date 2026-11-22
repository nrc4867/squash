package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.mouth.jawLimitOf
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SeamStroke
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the rig reads from the document beside the assembly: each part's joint reaches by joint name, each seam's strokes by its child, and
 * how each part a garment owns that sets its binding is bound.
 */
data class RigInput(
    val reach: Map<PartId, Map<String, Float>> = emptyMap(),
    val seams: Map<PartId, List<SeamStroke>> = emptyMap(),
    val binds: Map<PartId, OwnedBind> = emptyMap(),
    val jaws: Map<PartId, Float> = emptyMap(),
)

/**
 * What [character] gives its rig: the reaches and seam strokes each part resolves to, its side's reading of its pair's, its owned parts'
 * bindings, and with [templates] the widest each head's jaw opens.
 */
fun rigInput(character: Character, templates: TemplateLibrary? = null): RigInput {
    val jaws = templates?.let { library -> character.parts.mapNotNull { part -> jawLimitOf(character, part, library)?.let { part.id to it } }.toMap() }.orEmpty()
    return rigInputOf(character).copy(jaws = jaws)
}

private fun rigInputOf(character: Character): RigInput {
    val reach = character.parts.associate { part ->
        part.id to character.resolved(part).params.mapNotNull { (name, value) -> RigParams.reachJoint(name)?.let { it to value } }.toMap()
    }.filterValues { it.isNotEmpty() }
    val seams = character.parts.associate { it.id to character.resolved(it).surface.seams }.filterValues { it.isNotEmpty() }
    val binds = character.parts.filter { it.garment != null && (it.bind != null || it.stiff != null) }.associate { it.id to OwnedBind(it.bind, it.stiff ?: 0f) }
    return RigInput(reach, seams, binds)
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
        session.document.map { rigInput(it.character, session.templates) }.distinctUntilChanged(),
        { mapOf(AssemblySource to session.bodyAssembly) },
    ) { gathered ->
        val assembled = gathered.values.getValue(AssemblySource)
        val input = gathered.shape
        rigOf(assembled, StrokePaint(assembled, input.seams), cancellationCheck(), JointReach { part, joint -> input.reach[part]?.get(joint) ?: 1f }) { input.binds[it] ?: OwnedBind.RULE }
            .withJawLimits(input.jaws, assembled)
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
