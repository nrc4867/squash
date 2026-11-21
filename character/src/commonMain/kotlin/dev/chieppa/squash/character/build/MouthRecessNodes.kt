package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.mouth.MouthRecess
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** Every recess the character's mouths cut into its parts, by the part each is cut into. */
data class BodyRecesses(val byPart: Map<PartId, List<MouthRecess>>) {
    /** Every recess, part by part. */
    val all: List<MouthRecess> get() = byPart.values.flatten()

    /** [part]'s recesses: where a part's mouth opens, the wall its teeth stand on. */
    fun of(part: PartId): List<MouthRecess> = byPart[part].orEmpty()

    companion object {
        val NONE = BodyRecesses(emptyMap())
    }
}

/** The parts of [character] holding a mouth group, which a mouth may cut a recess into. */
fun mouthHolders(character: Character): List<PartId> =
    character.parts.filter { part -> character.resolved(part).surface.groups.any { it.isMouth } }.map { it.id }

/** What a part's recess node gathers: its resolved surface work and its surface. */
private sealed interface RecessSource {
    class Work(val work: ResolvedSurfaceWork) : RecessSource

    class Surface(val surface: PartSurface) : RecessSource
}

private enum class RecessKey { WORK, SURFACE }

/** The recesses [part]'s mouths cut into it, from its [work] on its [surface] (see [mouthRecesses]). */
fun Graph.partRecessesNode(part: PartId, work: Node<*, ResolvedSurfaceWork>, surface: Node<*, PartSurface>): Node<*, PartRecesses> {
    val sources = mapOf<RecessKey, Node<*, RecessSource>>(
        RecessKey.WORK to follow(work, stage = BuildStages.SURFACE_WORK) { RecessSource.Work(it) },
        RecessKey.SURFACE to follow(surface, stage = BuildStages.SURFACE_WORK) { RecessSource.Surface(it) },
    )
    return gather(flowOf(Unit), sourcesOf = { sources }, stage = BuildStages.SURFACE_WORK) { gathered ->
        val resolved = (gathered.values.getValue(RecessKey.WORK) as RecessSource.Work).work
        val shape = (gathered.values.getValue(RecessKey.SURFACE) as RecessSource.Surface).surface
        PartRecesses(mouthRecesses(part, resolved, shape))
    }
}

/** One part's recesses. */
data class PartRecesses(val recesses: List<MouthRecess>)

/** Every recess the parts [holders] names cut into them, each from the node [recesses] gives for it. */
fun Graph.bodyRecessesNode(holders: Flow<List<PartId>>, recesses: (PartId) -> Node<*, PartRecesses>): Node<*, BodyRecesses> =
    gather(holders.distinctUntilChanged(), sourcesOf = { parts -> parts.associateWith(recesses) }, stage = BuildStages.SURFACE_WORK) { gathered ->
        BodyRecesses(gathered.values.mapValues { it.value.recesses }.filterValues { it.isNotEmpty() })
    }
