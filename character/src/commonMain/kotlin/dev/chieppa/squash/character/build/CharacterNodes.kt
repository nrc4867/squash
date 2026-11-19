package dev.chieppa.squash.character.build

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.chieppa.squash.geom.bent
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.isWorn
import dev.chieppa.squash.document.worn
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.graph.Gathered
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow

/** The surface of a mesh node's part with axes [axes] on the side [side] follows, following it; a part joining a pair takes a side. */
fun Graph.partSurfaceNode(meshes: Node<*, PartMesh>, axes: PartAxes, side: Flow<Side?>, joints: Flow<Map<String, Float>> = flowOf(emptyMap()), template: PartTemplate? = null): Node<*, PartSurface> = gather(
    combine(joints, side) { held, on -> held to on }.distinctUntilChanged(),
    sourcesOf = { mapOf(Unit to meshes) },
    stage = BuildStages.SURFACE,
) { (shown, built) ->
    val (held, on) = shown
    val mesh = built.getValue(Unit)
    val own = mesh.axes ?: axes
    PartSurface.of(if (template == null) mesh else mesh.bent(bendsOf(held, template, own)), own, on == Side.RIGHT, cancellationCheck())
}

/** A template's surface with nothing drawn, [mirrored] or not, leaning [spread] degrees out as its plan places it, at each of [lengthFactor]'s values. */
fun Graph.defaultSurfaceNode(template: PartTemplate, mirrored: Boolean, lengthFactor: Flow<Float>, spread: Float = 0f): Node<Float, PartSurface> = node(lengthFactor, stage = BuildStages.SURFACE) { factor ->
    PlannedSurfaces.of(template, mirrored, spread, factor, cancellationCheck())
}

/** What a part the plan would place starts from: nothing drawn, leaning [spread] degrees out. */
fun plannedData(spread: Float): PartData = if (spread == 0f) PartData() else PartData(params = mapOf(PlacementParams.OUTWARD to spread))

/** The surface of an [unplaced] part that resolves to [data], built at [lengthFactor] of its length, on a socket unless it is a root. */
fun surfaceOf(unplaced: PartTemplate, data: PartData, mirrored: Boolean, checkCancelled: () -> Unit = {}, lengthFactor: Float = 1f, onSocket: Boolean = true): PartSurface {
    val template = unplaced.asBuilt(data.params, onSocket)
    val patches = PartBuilder.build(template, data, checkCancelled, lengthFactor)
    return PartSurface.of(PartBuilder.mesh("", template, patches, checkCancelled), PartBuilder.axes(template), mirrored, checkCancelled)
}

/** What decides what a character is assembled from: its parts and its plan. */
data class AssemblyShape(val parts: List<AssemblyPart>, val plan: BodyPlan?, val lengths: LegLengths = LegLengths())

/**
 * The parts of [character], as placing reads them, under the garments worn: each on the template it wears, a part a worn garment
 * hides left out with every part on it, and a part a garment owns only while that garment is worn.
 */
fun assemblyParts(character: Character): List<AssemblyPart> {
    val hidden = hiddenUnderGarments(character)
    return character.parts.filter { it.id !in hidden }.map { part ->
        val placement = character.resolvedShape(part).params.filterKeys { PlacementParams.isPlacement(it) || HandSockets.isHandSocket(it) || GenericParams.isGeneric(it) || HandJoints.isHandJoint(it) || HandLandmarks.isHandLandmark(it) || JoinParams.isJoin(it) }
        val worn = character.wornPart(part)
        val wornSockets = worn.data.params.filter { (key, value) -> (PlacementParams.isSocket(key) || HandSockets.isHandSocket(key)) && placement[key] != value }
        AssemblyPart(part.id, worn.template, part.socket, part.pair, part.side, placement, part.role, wornSockets, part.garment, part.blend, character.resolved(part).surface.shades)
    }
}

/** The parts of [character] a worn garment hides or that a garment not worn owns, with every part sitting on one of them. */
fun hiddenUnderGarments(character: Character): Set<PartId> {
    val unworn = character.parts.filter { !character.isWorn(it) }.mapTo(hashSetOf()) { it.id }
    if (character.worn.isEmpty() && unworn.isEmpty()) return emptySet()
    val hidden = character.parts.filter { character.wornPart(it).hidden }.mapTo(unworn) { it.id }
    var grew = hidden.isNotEmpty()
    while (grew) grew = character.parts.filter { it.socket?.parent in hidden }.any { hidden.add(it.id) }
    return hidden
}

/** A surface the character is assembled from: a part's own, or a template's with nothing drawn. */
sealed interface AssemblySource {
    data class Part(val id: PartId) : AssemblySource

    data class Default(val template: TemplateRef, val mirrored: Boolean) : AssemblySource

    /** The surface a part's sockets are read from while a garment reshapes it, see [skeletonRowNode]. */
    data class Skeleton(val id: PartId) : AssemblySource
}

/** Every template [plan] can reach from what [parts] hold. */
fun reachableTemplates(parts: List<AssemblyPart>, plan: BodyPlan?, templates: TemplateLibrary): Set<PartTemplate> {
    val reached = linkedSetOf<PartTemplate>()
    fun visit(template: PartTemplate) {
        if (!reached.add(template)) return
        val sockets = plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }
        sockets.forEach { spec -> spec.takes.firstNotNullOfOrNull { templates.latest(it) }?.let(::visit) }
    }
    parts.forEach { part -> templates.find(part.template)?.let(::visit) }
    return reached
}

/**
 * The whole character from every part's surface, following the document's shape. A part's redraw reaches it through that
 * part's own surface, so a child moves when its parent is redrawn, and undo does the reverse from the caches. A garment's change
 * to a part reaches its children through the part's [skeleton] instead, which holds them where the bare part does.
 */
fun Graph.assemblyNode(
    shape: Flow<AssemblyShape>,
    templates: TemplateLibrary,
    part: (PartId) -> Node<*, PartSurface>,
    default: (TemplateRef, Boolean) -> Node<*, PartSurface>,
    skeleton: (PartId) -> Node<*, PartSurface>? = { null },
): Node<Gathered<AssemblyShape, AssemblySource, PartSurface>, AssembledCharacter> = gather(
    shape,
    sourcesOf = { current ->
        val owned = current.parts.associate<AssemblyPart, AssemblySource, Node<*, PartSurface>> { AssemblySource.Part(it.id) to part(it.id) }
        val skeletons = current.parts.mapNotNull { placed -> skeleton(placed.id)?.let { AssemblySource.Skeleton(placed.id) to it } }
        val planned = reachableTemplates(current.parts, current.plan, templates).flatMap { template ->
            listOf(false, true).map { mirrored -> AssemblySource.Default(template.ref, mirrored) to default(template.ref, mirrored) }
        }
        owned + skeletons + planned
    },
    stage = BuildStages.ASSEMBLY,
) { (current, values) ->
    assemble(
        current.parts,
        current.parts.associate { it.id to values.getValue(AssemblySource.Part(it.id)) },
        current.plan,
        templates,
        { ref, mirrored -> values[AssemblySource.Default(ref, mirrored)] },
        current.lengths,
        current.parts.mapNotNull { placed -> values[AssemblySource.Skeleton(placed.id)]?.takeIf { it.patches.isNotEmpty() }?.let { placed.id to it } }.toMap(),
    )
}

/** The character of [parts] assembled at once, for a screen with no session: each part from [data], with nothing kept. */
fun assembleNow(parts: List<AssemblyPart>, data: (PartId) -> PartData, plan: BodyPlan?, templates: TemplateLibrary): AssembledCharacter {
    val lengths = legLengths(parts, data, plan, templates)
    val defaults = HashMap<Pair<TemplateRef, Boolean>, PartSurface>()
    fun default(ref: TemplateRef, mirrored: Boolean) = defaults.getOrPut(ref to mirrored) {
        PlannedSurfaces.of(templates[ref], mirrored, plan?.spreadFor(ref.id) ?: 0f, lengths.ofPlanned(ref))
    }
    val surfaces = parts.associate { it.id to surfaceOf(templates[it.template], data(it.id), it.side == Side.RIGHT, lengthFactor = lengths.ofPart(it.id), onSocket = it.socket != null) }
    return assemble(parts, surfaces, plan, templates, ::default, lengths)
}
