package dev.chieppa.squash.character.session

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.build.AssemblyShape
import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.ColorSource
import dev.chieppa.squash.character.build.HeldColor
import dev.chieppa.squash.character.build.LegLengthsOf
import dev.chieppa.squash.character.build.PartBuild
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PartBuildInput
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assemblyNode
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.bodyColorNode
import dev.chieppa.squash.character.build.bodySurfaceWorkNode
import dev.chieppa.squash.character.build.defaultSurfaceNode
import dev.chieppa.squash.character.build.partColorInputs
import dev.chieppa.squash.character.build.partColorNode
import dev.chieppa.squash.character.build.partMeshNode
import dev.chieppa.squash.character.build.partSurfaceNode
import dev.chieppa.squash.character.build.partSurfaceWorkNode
import dev.chieppa.squash.character.build.plannedLengthFactor
import dev.chieppa.squash.character.build.sliceRowNode
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.ColorDetail
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.stretch.core.History
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One open character: its document with undo, and the graph that derives from it. Views read this and send commands. */
class CharacterSession(
    initial: SquashDocument,
    val templates: TemplateLibrary,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val history = History(initial)
    val graph = Graph(scope, dispatcher)
    val document: StateFlow<SquashDocument> get() = history.document

    private val lengths = LegLengthsOf(templates)
    private val rows = mutableMapOf<PartId, Node<PartBuildInput?, PartBuild>>()
    private val meshes = mutableMapOf<PartId, Node<PartBuild, PartMesh>>()
    private val surfaces = mutableMapOf<PartId, Node<*, PartSurface>>()
    private val defaults = mutableMapOf<Pair<TemplateRef, Boolean>, Node<*, PartSurface>>()
    private val works = mutableMapOf<PartId, Node<*, ResolvedSurfaceWork>>()
    private val colors = mutableMapOf<PartId, Node<*, HeldColor>>()
    private val colorStep = MutableStateFlow(ColorDetail.START)
    private val colorDetail: Node<*, ColorSource> = graph.node(colorStep, stage = BuildStages.SURFACE_WORK) { ColorSource.Detail(it) }

    /**
     * The whole character, each part on its socket with the plan's empty sockets filled from their templates. Made with
     * the session, so [Graph.settled] covers every part from the start. Its `output` keeps the last character while a newer
     * run is pending, and `failure` holds why a part's build failed.
     */
    val assembly: Node<*, AssembledCharacter> = graph.assemblyNode(
        document.map { AssemblyShape(assemblyParts(it.character), templates.plan(it.character.bodyPlan), lengths.of(it.character)) }.distinctUntilChanged(),
        templates,
        ::surface,
        ::defaultSurface,
    )

    /**
     * Every part's surface work as positions in body space, through [assembly]'s placement. Made with the session, so
     * [Graph.settled] covers it from the start. Its `output` keeps the last result while a newer run is pending.
     */
    val bodySurfaceWork: Node<*, BodySurfaceWork> = graph.bodySurfaceWorkNode(
        document.map { doc -> doc.character.parts.map { it.id } }.distinctUntilChanged(),
        assembly,
        ::surfaceWork,
    )

    /**
     * Every part's color, cut to the mesh in each part's surface space. Made with the session, so [Graph.settled] covers it from
     * the start. Its `output` keeps the last result while a newer run is pending. See HLD: look, Color is vector.
     */
    val bodyColor: Node<*, BodyColor> = graph.bodyColorNode(
        document.map { doc -> doc.character.parts.map { it.id } }.distinctUntilChanged(),
        ::heldColor,
    )

    /** Says how many pixels a body unit spans on the largest view of color, which re-flattens its curves when that has changed enough. */
    fun showColorAt(pixelsPerUnit: Float) {
        colorStep.value = ColorDetail.stepFor(pixelsPerUnit, colorStep.value)
    }

    fun send(command: DocumentCommand) = history.execute(command)

    /** The node deriving [part]'s patches; there is one per part for the session's life. */
    fun patches(part: PartId): Node<PartBuildInput?, PartBuild> =
        rows.getOrPut(part) { graph.sliceRowNode(document, part, templates, lengths) }

    /**
     * The node deriving [part]'s mesh, in the part's own axes with its socket plane at a = 0; one per part for the
     * session's life. Its `output` keeps the last mesh while a newer run is pending or cancelled, and `failure` holds why
     * the latest build failed. A part not in the document has an empty mesh.
     */
    fun mesh(part: PartId): Node<PartBuild, PartMesh> = meshes.getOrPut(part) { graph.partMeshNode(patches(part), part) }

    /** What [part] shows on its side, derived from its mesh; one per part for the session's life, since a part keeps its side. */
    fun surface(part: PartId): Node<*, PartSurface> = surfaces.getOrPut(part) {
        val instance = document.value.character.part(part)
        val axes = instance?.let { templates.find(it.template) }?.let(PartBuilder::axes) ?: PartAxes.of(Vec3.X)
        graph.partSurfaceNode(mesh(part), axes, instance?.side)
    }

    /**
     * [part]'s surface work as positions in surface space, each point in the space of the part its anchor names; one per part
     * for the session's life. A part not in the document holds nothing.
     */
    fun surfaceWork(part: PartId): Node<*, ResolvedSurfaceWork> =
        works.getOrPut(part) { graph.partSurfaceWorkNode(document, part, ::surface) }

    /** [part]'s color shapes, cut to the surfaces they land on; one per part for the session's life. */
    fun heldColor(part: PartId): Node<*, HeldColor> = colors.getOrPut(part) {
        graph.partColorNode(partColorInputs(document, part, templates), part, surfaceWork(part), colorDetail, ::surface)
    }

    private fun defaultSurface(template: TemplateRef, mirrored: Boolean): Node<*, PartSurface> =
        defaults.getOrPut(template to mirrored) { graph.defaultSurfaceNode(templates[template], mirrored, plannedLengthFactor(document, template, templates, lengths)) }
}
