package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.surface.HeldPosedSurface
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A drag sends a new pose every frame, so a posed node keeps only the last few. */
private const val POSED_CACHE = 4

/** A result held by identity, so a node reading it neither compares nor hashes what it holds. */
class Held(val value: Any)

/** [node]'s results each [Held], so one gathered node can read nodes of several kinds by identity. */
fun Graph.held(node: Node<*, out Any>): Node<*, Held> {
    @Suppress("UNCHECKED_CAST")
    return follow(node as Node<*, Any>, cacheSize = 1) { Held(it) }
}

private enum class PosedKey { REST, POSE, SHOWN, REST_WORK, STATE, REST_HELD, SURFACE }

/**
 * The character in a pose with its surface work resolved there: [body] is [rest] in the pose and [work] is [restWork] on [body]'s
 * grids. While the pose leaves every part at rest, [body] is [rest] and [work] is [restWork] themselves. [outlined], while the one
 * surface shown in the pose is a drag's coarse surface, holds the parts that surface holds, and the pose's lines are outlines only.
 */
class PosedState(
    val rest: AssembledCharacter,
    val body: AssembledCharacter,
    val restWork: BodySurfaceWork,
    val work: BodySurfaceWork,
    val outlined: Set<PartId>? = null,
) {
    val atRest: Boolean get() = body === rest

    /** The parts the pose moves or shows more or less of. */
    val moved: Set<PartId> by lazy { body.movedFrom(rest) }

    /** The parts whose surface the pose carries elsewhere. */
    val carried: Set<PartId> by lazy { body.carriedFrom(rest) }

    /** Whether [part]'s surface work in the pose differs from its work at rest. */
    fun workMoved(part: PartId): Boolean = work.parts[part] !== restWork.parts[part]
}

/**
 * [assembly] in the pose [pose] holds the node of, once a rig gives one, with what the garments hide decided again in the pose: the
 * character itself while the pose leaves every part at rest or before there is a pose, with its hair pushed out of the body it lies
 * against. See [posedBy], [withHairPushed], over the garments [worn] grows from the body, and [hiddenAgain]. The pose of a new
 * assembly comes after the assembly, through its rig; a run on an assembly with a pose made for another waits for the next.
 */
fun Graph.posedBodyNode(
    assembly: Node<*, AssembledCharacter>,
    pose: StateFlow<Node<*, Held>?>,
    worn: Flow<WornOver> = MutableStateFlow(emptyMap()),
): Node<*, AssembledCharacter> {
    val rest = held(assembly)
    return gather(
        combine(pose, worn) { posed, over -> posed to over },
        sourcesOf = { (posed, _) -> listOfNotNull(PosedKey.REST to rest, posed?.let { PosedKey.POSE to it }).toMap() },
        cacheSize = POSED_CACHE,
    ) { (shape, sources) ->
        val character = sources.getValue(PosedKey.REST).value as AssembledCharacter
        val pose = sources[PosedKey.POSE]?.value as PosedParts? ?: return@gather character
        if (!pose.isOf(character)) awaitCancellation()
        character.posedBy(pose).withHairPushed(shape.second).hiddenAgain(character)
    }
}

/**
 * [body], [assembly] in its shown pose, with [restWork], the surface work at rest, resolved again on its grids from what [document]
 * gives each part. See [posedSurfaceWork].
 */
fun Graph.posedStateNode(
    document: Flow<SquashDocument>,
    assembly: Node<*, AssembledCharacter>,
    body: Node<*, AssembledCharacter>,
    restWork: Node<*, BodySurfaceWork>,
    surface: StateFlow<Node<*, HeldPosedSurface>?> = MutableStateFlow(null),
): Node<*, PosedState> {
    val sources = mapOf(PosedKey.REST to held(assembly), PosedKey.SHOWN to held(body), PosedKey.REST_WORK to held(restWork))
    val surfaces = HashMap<Node<*, HeldPosedSurface>, Node<*, Held>>()
    val inputs = document.map { doc -> doc.character.parts.associate { it.id to surfaceWorkInput(doc.character, it.id) } }.distinctUntilChanged()
    return gather(
        combine(inputs, surface) { byPart, posed -> Held(byPart) to posed },
        sourcesOf = { (_, posed) -> posed?.let { sources + (PosedKey.SURFACE to surfaces.getOrPut(it) { held(it) }) } ?: sources },
        cacheSize = POSED_CACHE,
    ) { (input, held) ->
        val rest = held.getValue(PosedKey.REST).value as AssembledCharacter
        val shown = held.getValue(PosedKey.SHOWN).value as AssembledCharacter
        val work = held.getValue(PosedKey.REST_WORK).value as BodySurfaceWork
        if (shown === rest) {
            PosedState(rest, rest, work, work)
        } else {
            @Suppress("UNCHECKED_CAST")
            val byPart = input.first.value as Map<PartId, SurfaceWorkInput>
            val posed = posedSurfaceWork(work, byPart, shown, shown.carriedFrom(rest), cancellationCheck())
            val surface = held[PosedKey.SURFACE]?.value as HeldPosedSurface?
            val carry = surface?.posed?.let { carriedOn(it, work) }
            val outlined = surface?.posed?.takeIf { surface.coarse }?.surface?.field?.runs?.mapTo(HashSet()) { it.partId }
            PosedState(rest, shown, work, carry?.let { posed.withCarried(it, work) } ?: posed, outlined)
        }
    }
}

/**
 * Where [posed], a one surface in a pose, carries each point of [work] at rest that lies on one of its runs, by the point's anchor; null
 * while the pose leaves the surface at rest.
 */
private fun carriedOn(posed: PosedSurface, work: BodySurfaceWork): Map<Anchor, ResolvedPoint>? {
    val carry = posed.carry ?: return null
    val runs = posed.surface.field.runs.mapTo(HashSet()) { it.partId to it.patch.name }
    val out = HashMap<Anchor, ResolvedPoint>()
    work.parts.values.forEach { held ->
        held.placedBy { point ->
            if (point.anchor.part to point.anchor.patch in runs && point.anchor !in out) out[point.anchor] = carry(point)
            point
        }
    }
    return out
}

/**
 * This work with each point [carried] holds by its anchor taken from there; a part none of whose points it moves keeps its work as the
 * same object, as a part the pose leaves at rest does.
 */
private fun BodySurfaceWork.withCarried(carried: Map<Anchor, ResolvedPoint>, rest: BodySurfaceWork): BodySurfaceWork =
    BodySurfaceWork(parts.mapValues { (id, held) ->
        val atRest = rest.parts[id]
        val restPoints = HashMap<Anchor, ResolvedPoint>()
        atRest?.placedBy { point -> point.also { restPoints[it.anchor] = it } }
        var moved = held !== atRest
        val shown = held.placedBy { point ->
            val to = carried[point.anchor] ?: return@placedBy point.also { if (restPoints[it.anchor] != it) moved = true }
            if (restPoints[point.anchor] != to) moved = true
            to
        }
        if (moved || atRest == null) shown else atRest
    })

/** A node following [state] and [rest], what is held at rest of the kind it derives: [rest]'s own result while [state] is at rest. */
private fun <O : Any> Graph.posedFrom(
    shape: Flow<Any>,
    state: Node<*, PosedState>,
    rest: Node<*, O>,
    compute: suspend (Any, PosedState, O) -> O,
): Node<*, O> {
    val sources = mapOf(PosedKey.STATE to held(state), PosedKey.REST_HELD to held(rest))
    return gather(shape.map(::Held), sourcesOf = { sources }, cacheSize = POSED_CACHE) { (input, held) ->
        @Suppress("UNCHECKED_CAST")
        val atRest = held.getValue(PosedKey.REST_HELD).value as O
        val posed = held.getValue(PosedKey.STATE).value as PosedState
        if (posed.atRest) atRest else compute(input.value, posed, atRest)
    }
}

/**
 * The character's lines in [state]'s pose: [rest], the lines at rest, itself while the pose leaves every part at rest; outlines only
 * while [PosedState.outlined] says so. See [posedLines] and [outlinedLines].
 */
fun Graph.posedLinesNode(state: Node<*, PosedState>, rest: Node<*, BodyLines>): Node<*, BodyLines> =
    posedFrom(MutableStateFlow(Unit), state, rest) { _, posed, lines ->
        val outlined = posed.outlined
        if (outlined != null) outlinedLines(lines, posed.body, posed.moved, outlined, cancellationCheck())
        else posedLines(lines, posed.body, posed.moved, posed.work, posed::workMoved, cancellationCheck())
    }

/** Every part's fur in [state]'s pose: [rest], the fur at rest, itself while the pose leaves every part at rest. See [posedFur]. */
fun Graph.posedFurNode(document: Flow<SquashDocument>, templates: TemplateLibrary, state: Node<*, PosedState>, rest: Node<*, BodyFur>): Node<*, BodyFur> =
    posedFrom(document.map { furInputs(it.character, templates) }.distinctUntilChanged(), state, rest) { inputs, posed, fur ->
        @Suppress("UNCHECKED_CAST")
        posedFur(fur, inputs as Map<PartId, PartFurInput>, posed.work, posed.body, posed.carried, posed.moved, posed::workMoved, cancellationCheck())
    }
