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

/** A template's surface with nothing drawn, [mirrored] or not, at each of [lengthFactor]'s values. */
fun Graph.defaultSurfaceNode(template: PartTemplate, mirrored: Boolean, lengthFactor: Flow<Float>): Node<Float, PartSurface> = node(lengthFactor, stage = BuildStages.SURFACE) { factor ->
    surfaceOf(template, PartData(), mirrored, cancellationCheck(), factor)
}

/** The surface of an [unplaced] part that resolves to [data], built at [lengthFactor] of its length, on a socket unless it is a root. */
fun surfaceOf(unplaced: PartTemplate, data: PartData, mirrored: Boolean, checkCancelled: () -> Unit = {}, lengthFactor: Float = 1f, onSocket: Boolean = true): PartSurface {
    val template = unplaced.asBuilt(data.params, onSocket)
    val patches = PartBuilder.blended(template, data, checkCancelled, lengthFactor)
    return PartSurface.of(PartBuilder.mesh("", template, patches, checkCancelled), PartBuilder.axes(template), mirrored, checkCancelled)
}

/** What decides what a character is assembled from: its parts and its plan. */
data class AssemblyShape(val parts: List<AssemblyPart>, val plan: BodyPlan?, val lengths: LegLengths = LegLengths())

/**
 * The parts of [character], as placing reads them, under the garments worn: each on the template it wears, and a part a worn garment
 * hides left out with every part on it.
 */
fun assemblyParts(character: Character): List<AssemblyPart> {
    val hidden = hiddenUnderGarments(character)
    return character.parts.filter { it.id !in hidden }.map { part ->
        val placement = character.resolvedShape(part).params.filterKeys { PlacementParams.isPlacement(it) || HandSockets.isHandSocket(it) || GenericParams.isGeneric(it) || HandJoints.isHandJoint(it) || HandLandmarks.isHandLandmark(it) }
        AssemblyPart(part.id, character.wornPart(part).template, part.socket, part.pair, part.side, placement, part.role)
    }
}

/** The parts of [character] a worn garment hides, with every part sitting on one of them. */
fun hiddenUnderGarments(character: Character): Set<PartId> {
    if (character.worn.isEmpty()) return emptySet()
    val hidden = character.parts.filter { character.wornPart(it).hidden }.mapTo(hashSetOf()) { it.id }
    var grew = hidden.isNotEmpty()
    while (grew) grew = character.parts.filter { it.socket?.parent in hidden }.any { hidden.add(it.id) }
    return hidden
}

/** A surface the character is assembled from: a part's own, or a template's with nothing drawn. */
sealed interface AssemblySource {
    data class Part(val id: PartId) : AssemblySource

    data class Default(val template: TemplateRef, val mirrored: Boolean) : AssemblySource
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
 * part's own surface, so a child moves when its parent is redrawn, and undo does the reverse from the caches.
 */
fun Graph.assemblyNode(
    shape: Flow<AssemblyShape>,
    templates: TemplateLibrary,
    part: (PartId) -> Node<*, PartSurface>,
    default: (TemplateRef, Boolean) -> Node<*, PartSurface>,
): Node<Gathered<AssemblyShape, AssemblySource, PartSurface>, AssembledCharacter> = gather(
    shape,
    sourcesOf = { current ->
        val owned = current.parts.associate<AssemblyPart, AssemblySource, Node<*, PartSurface>> { AssemblySource.Part(it.id) to part(it.id) }
        val planned = reachableTemplates(current.parts, current.plan, templates).flatMap { template ->
            listOf(false, true).map { mirrored -> AssemblySource.Default(template.ref, mirrored) to default(template.ref, mirrored) }
        }
        owned + planned
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
    )
}

/** The character of [parts] assembled at once, for a screen with no session: each part from [data], with nothing kept. */
fun assembleNow(parts: List<AssemblyPart>, data: (PartId) -> PartData, plan: BodyPlan?, templates: TemplateLibrary): AssembledCharacter {
    val lengths = legLengths(parts, data, plan, templates)
    val defaults = HashMap<Pair<TemplateRef, Boolean>, PartSurface>()
    fun default(ref: TemplateRef, mirrored: Boolean) = defaults.getOrPut(ref to mirrored) { surfaceOf(templates[ref], PartData(), mirrored, lengthFactor = lengths.ofPlanned(ref)) }
    val surfaces = parts.associate { it.id to surfaceOf(templates[it.template], data(it.id), it.side == Side.RIGHT, lengthFactor = lengths.ofPart(it.id), onSocket = it.socket != null) }
    return assemble(parts, surfaces, plan, templates, ::default, lengths).withSocketFillets()
}
