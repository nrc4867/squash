package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.graph.Gathered
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The surface of a mesh node's part with axes [axes] on [side], following it. */
fun Graph.partSurfaceNode(meshes: Node<*, PartMesh>, axes: PartAxes, side: Side?): Node<PartMesh, PartSurface> = follow(meshes, stage = BuildStages.SURFACE) { mesh ->
    PartSurface.of(mesh, axes, side == Side.RIGHT, cancellationCheck())
}

/** A template's surface with nothing drawn, [mirrored] or not. */
fun Graph.defaultSurfaceNode(template: PartTemplate, mirrored: Boolean): Node<PartTemplate, PartSurface> = node(MutableStateFlow(template), stage = BuildStages.SURFACE) { recipe ->
    surfaceOf(recipe, PartData(), mirrored, cancellationCheck())
}

/** The surface of a [template] part that resolves to [data]. */
fun surfaceOf(template: PartTemplate, data: PartData, mirrored: Boolean, checkCancelled: () -> Unit = {}): PartSurface {
    val patches = PartBuilder.build(template, data, checkCancelled)
    return PartSurface.of(PartBuilder.mesh("", template, patches, checkCancelled), PartBuilder.axes(template), mirrored, checkCancelled)
}

/** What decides what a character is assembled from: its parts and its plan. */
data class AssemblyShape(val parts: List<AssemblyPart>, val plan: BodyPlan?)

/** The parts of [character], as placing reads them. */
fun assemblyParts(character: Character): List<AssemblyPart> =
    character.parts.map { AssemblyPart(it.id, it.template, it.socket, it.pair, it.side) }

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
    ) { ref, mirrored -> values[AssemblySource.Default(ref, mirrored)] }
}

/** The character of [parts] assembled at once, for a screen with no session: each part from [data], with nothing kept. */
fun assembleNow(parts: List<AssemblyPart>, data: (PartId) -> PartData, plan: BodyPlan?, templates: TemplateLibrary): AssembledCharacter {
    val defaults = HashMap<Pair<TemplateRef, Boolean>, PartSurface>()
    fun default(ref: TemplateRef, mirrored: Boolean) = defaults.getOrPut(ref to mirrored) { surfaceOf(templates[ref], PartData(), mirrored) }
    val surfaces = parts.associate { it.id to surfaceOf(templates[it.template], data(it.id), it.side == Side.RIGHT) }
    return assemble(parts, surfaces, plan, templates, ::default)
}
