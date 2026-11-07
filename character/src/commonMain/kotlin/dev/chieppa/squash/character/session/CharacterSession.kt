package dev.chieppa.squash.character.session

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.build.AssemblyShape
import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.BodyFur
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.ColorSource
import dev.chieppa.squash.character.build.FurCamera
import dev.chieppa.squash.character.build.FurCameras
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.build.Held
import dev.chieppa.squash.character.build.HeldColor
import dev.chieppa.squash.character.build.LegLengthsOf
import dev.chieppa.squash.character.build.PartBuild
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PartBuildInput
import dev.chieppa.squash.character.build.PosedParts
import dev.chieppa.squash.character.build.PosedState
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assemblyNode
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.bodyColorNode
import dev.chieppa.squash.character.build.bodyLinesNode
import dev.chieppa.squash.character.build.bodyFurNode
import dev.chieppa.squash.character.build.bodySurfaceWorkNode
import dev.chieppa.squash.character.build.defaultSurfaceNode
import dev.chieppa.squash.character.build.furViewNode
import dev.chieppa.squash.character.build.held
import dev.chieppa.squash.character.build.partColorInputs
import dev.chieppa.squash.character.build.partColorNode
import dev.chieppa.squash.character.build.partMeshNode
import dev.chieppa.squash.character.build.partSurfaceNode
import dev.chieppa.squash.character.build.partSurfaceWorkNode
import dev.chieppa.squash.character.build.plannedLengthFactor
import dev.chieppa.squash.character.build.posedBodyNode
import dev.chieppa.squash.character.build.posedFurNode
import dev.chieppa.squash.character.build.posedLinesNode
import dev.chieppa.squash.character.build.posedStateNode
import dev.chieppa.squash.character.build.shownJoints
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

    /**
     * The nodes that read a view's camera, which an orbit reruns every frame. They are apart from [graph], whose settled and
     * progress say the character is being derived; a test that reads a view waits on both.
     */
    val viewGraph = Graph(scope, dispatcher)
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
     * The character as its lines read it: what a view finds its contours and creases in and a surface tool picks on. Made with the
     * session, so [Graph.settled] covers it from the start. Its `output` keeps the last result while a newer run is pending.
     */
    val bodyLines: Node<*, BodyLines> = graph.bodyLinesNode(assembly, bodySurfaceWork)

    /**
     * Every part's color, cut to the mesh in each part's surface space. Made with the session, so [Graph.settled] covers it from
     * the start. Its `output` keeps the last result while a newer run is pending. See HLD: look, Color is vector.
     */
    val bodyColor: Node<*, BodyColor> = graph.bodyColorNode(
        document.map { doc -> doc.character.parts.map { it.id } }.distinctUntilChanged(),
        ::heldColor,
    )

    /** Every part's fur strokes with the grain and the slot under each, camera aside. Made with the session, so [Graph.settled] covers it from the start. */
    val bodyFur: Node<*, BodyFur> = graph.bodyFurNode(document, templates, assembly, bodySurfaceWork)

    private val pose = MutableStateFlow<Node<*, Held>?>(null)

    /**
     * The character in the pose [showPose] gave: [assembly] with each part the pose moves on its posed mesh, or [assembly]'s own result
     * while the pose leaves every part at rest. What follows a pose reads this, so `settled` covers it. See HLD: data-model, The rig.
     */
    val posedBody: Node<*, AssembledCharacter> = graph.posedBodyNode(assembly, pose)

    private val posedState: Node<*, PosedState> = graph.posedStateNode(document, assembly, posedBody, bodySurfaceWork)

    /** [bodySurfaceWork] resolved on [posedBody]'s grids: its own result while the pose leaves every part at rest. */
    val posedSurfaceWork: Node<*, BodySurfaceWork> = graph.follow(posedState, cacheSize = 1) { it.work }

    /** [bodyLines] on [posedBody], with [posedSurfaceWork]: its own result while the pose leaves every part at rest. */
    val posedLines: Node<*, BodyLines> = graph.posedLinesNode(posedState, bodyLines)

    /** [bodyFur] rooted on [posedBody], with [posedSurfaceWork]: its own result while the pose leaves every part at rest. */
    val posedFur: Node<*, BodyFur> = graph.posedFurNode(document, templates, posedState, bodyFur)

    /** Shows the character in the pose [posed] derives, a node of [graph]; the rig gives it. */
    fun showPose(posed: Node<*, out PosedParts>) {
        pose.value = graph.held(posed)
    }

    private val furCameras = FurCameras()
    private val furViews = mutableMapOf<Pair<String, Boolean>, Node<*, FurView>>()

    /**
     * What fur shows in the view named [view], through the camera [showFurFrom] last gave it, on the posed body when [posed]; one node
     * per view for the session's life.
     */
    fun furView(view: String, posed: Boolean = false): Node<*, FurView> =
        furViews.getOrPut(view to posed) { viewGraph.furViewNode(furCameras.of(view), if (posed) posedFur else bodyFur) }

    /** Says what camera the view named [view] looks through. */
    fun showFurFrom(view: String, camera: FurCamera) {
        furCameras.of(view).value = camera
        furView(view)
    }

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
        val template = instance?.let { templates.find(it.template) }
        val axes = template?.let(PartBuilder::axes) ?: PartAxes.of(Vec3.X)
        val joints = document.map { doc -> template?.let { shownJoints(doc.character, part, it) }.orEmpty() }
        graph.partSurfaceNode(mesh(part), axes, instance?.side, joints, template)
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
