package dev.chieppa.squash.character.session

import dev.chieppa.squash.character.viewkeys.ViewKeyCache
import dev.chieppa.squash.character.viewkeys.viewKeysNode
import dev.chieppa.squash.character.build.builtTemplate
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.character.model.PartTemplate

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
import dev.chieppa.squash.character.build.shineSources
import dev.chieppa.squash.character.build.shinedColorNode
import dev.chieppa.squash.character.build.bodyLinesNode
import dev.chieppa.squash.character.build.bodyFurNode
import dev.chieppa.squash.character.build.bodySurfaceWorkNode
import dev.chieppa.squash.character.build.defaultSurfaceNode
import dev.chieppa.squash.character.build.furViewNode
import dev.chieppa.squash.character.build.held
import dev.chieppa.squash.character.build.partColorInputs
import dev.chieppa.squash.character.build.partColorNode
import dev.chieppa.squash.character.build.partShapesNode
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.build.partMeshNode
import dev.chieppa.squash.character.build.partSurfaceNode
import dev.chieppa.squash.character.build.partSurfaceWorkNode
import dev.chieppa.squash.character.build.plannedLengthFactor
import dev.chieppa.squash.character.build.posedBodyNode
import dev.chieppa.squash.character.build.wornOver
import dev.chieppa.squash.character.build.posedFurNode
import dev.chieppa.squash.character.build.posedLinesNode
import dev.chieppa.squash.character.build.posedStateNode
import dev.chieppa.squash.character.build.shownJoints
import dev.chieppa.squash.character.build.skeletonRowNode
import dev.chieppa.squash.character.build.sliceRowNode
import dev.chieppa.squash.character.build.dressedNode
import dev.chieppa.squash.character.build.membraneColors
import dev.chieppa.squash.character.build.membranesNode
import dev.chieppa.squash.character.hair.LockBuilds
import dev.chieppa.squash.character.hair.LockHair
import dev.chieppa.squash.character.hair.LOCK_TEMPLATE
import dev.chieppa.squash.character.hair.lockColors
import dev.chieppa.squash.character.hair.lockGroupsNode
import dev.chieppa.squash.character.hair.lockLayersNode
import dev.chieppa.squash.character.hair.shellSpecs
import dev.chieppa.squash.document.shownMembranes
import dev.chieppa.squash.character.surface.HeldSurface
import dev.chieppa.squash.character.surface.fieldWorkNode
import dev.chieppa.squash.character.surface.HeldPaint
import dev.chieppa.squash.character.build.dressingOf
import dev.chieppa.squash.character.build.dressedColorNode
import dev.chieppa.squash.character.build.garmentColors
import dev.chieppa.squash.character.build.GarmentView
import dev.chieppa.squash.character.build.seenAs
import kotlinx.coroutines.flow.combine
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.ColorDetail
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.stretch.core.History
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import dev.chieppa.squash.character.mouth.speaking
import dev.chieppa.squash.document.MouthShapeName
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * One open character: its document with undo, and the graph that derives from it, run on [dispatcher], its one surface's fields read
 * through [reads]. Views read this and send commands.
 */
class CharacterSession(
    initial: SquashDocument,
    val templates: TemplateLibrary,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    val reads: FieldReads = CpuFieldReads,
) {
    val history = History(initial)

    /** The graph deriving the character; its nodes keep in their caches only what is made while no gesture is open. */
    val graph = Graph(scope, dispatcher) { !history.inGesture }

    /**
     * The nodes that read a view's camera, which an orbit reruns every frame. They are apart from [graph], whose settled and
     * progress say the character is being derived; a test that reads a view waits on both.
     */
    val viewGraph = Graph(scope, dispatcher)
    val document: StateFlow<SquashDocument> get() = history.document

    /**
     * The spoken shape every mouth is shown saying in place of its controls, null to show the document's: a shape picked in the Face stage
     * or a recipe's `set=`, which is no edit. The views set it.
     */
    val mouthPreview = MutableStateFlow<MouthShapeName?>(null)

    /** The document as the face shows it: [document] with every mouth saying [mouthPreview]. What the jaw, surface work and color read. */
    private val shownFace: Flow<SquashDocument> = combine(history.document, mouthPreview) { doc, shape -> doc.speaking(shape, templates) }

    private val lengths = LegLengthsOf(templates)
    private val rows = mutableMapOf<PartId, Node<PartBuildInput?, PartBuild>>()
    private val meshes = mutableMapOf<PartId, Node<PartBuild, PartMesh>>()
    private val surfaces = mutableMapOf<PartId, Node<*, PartSurface>>()
    private val skeletons = mutableMapOf<PartId, Node<*, PartSurface>>()
    private val defaults = mutableMapOf<Pair<TemplateRef, Boolean>, Node<*, PartSurface>>()
    private val works = mutableMapOf<PartId, Node<*, ResolvedSurfaceWork>>()
    private val shapes = mutableMapOf<PartId, Node<*, HeldShapes>>()
    private val colors = mutableMapOf<PartId, Node<*, HeldColor>>()
    private val colorStep = MutableStateFlow(ColorDetail.START)
    private val colorDetail: Node<*, ColorSource> = graph.node(colorStep, stage = BuildStages.SURFACE_WORK) { ColorSource.Detail(it) }

    /** How the views show the garments; the views set it, and [assembly] follows it. */
    val garmentView = MutableStateFlow(GarmentView())

    /**
     * The whole character, each part on its socket with the plan's empty sockets filled from their templates, wearing what its shown
     * outfit wears. Made with the session, so [Graph.settled] covers every part from the start. Its `output` keeps the last character while a newer
     * run is pending, and `failure` holds why a part's build failed.
     */
    val placed: Node<*, AssembledCharacter> = graph.assemblyNode(
        document.map { AssemblyShape(assemblyParts(it.character), templates.plan(it.character.bodyPlan), lengths.of(it.character)) }.distinctUntilChanged(),
        templates,
        ::surface,
        ::defaultSurface,
        ::skeleton,
    )

    /** The lock groups as built, kept from one build to the next so an edit builds only the locks and groups it changed. */
    val lockBuilds = LockBuilds(templates.latest(TemplateId(LOCK_TEMPLATE)))

    /** [assembly] without its lock groups: what an edit of the hair leaves as it was, so what derives from it alone does not run again. */
    val bodyAssembly: Node<*, AssembledCharacter> = graph.membranesNode(
        graph.dressedNode(
            placed,
            combine(document.map { dressingOf(it.character) }.distinctUntilChanged(), garmentView) { dressing, view -> dressing.seenAs(view) }.distinctUntilChanged(),
        ),
        document.map { it.character.shownMembranes }.distinctUntilChanged(),
    )

    val assembly: Node<*, AssembledCharacter> = graph.lockGroupsNode(
        bodyAssembly,
        document.map { LockHair(it.character.lockGroups, it.character.scalpFlows, shellSpecs(it.character)) }.distinctUntilChanged(),
        lockBuilds,
    )

    /** The hair volumes whose band of shine shows on the hair, on its own meshes and on the one surface. */
    private val shines = document.map { shineSources(it.character, templates) }.distinctUntilChanged()

    /** The character as one surface, its paint and its posed form; see [SessionSurface]. */
    val sessionSurface = SessionSurface(graph, history, placed, ::heldShapes, reads, shines)

    /** The character's body as one surface; null for a character with no part in the field. */
    val characterSurface: Node<*, HeldSurface> get() = sessionSurface.surface

    /** What is painted on [characterSurface]; null without one. */
    val surfacePaint: Node<*, HeldPaint> get() = sessionSurface.paint

    /**
     * Every part's surface work as positions in body space, through [assembly]'s placement, landed on the one surface's field. Made with the session, so [Graph.settled] covers it from the start. Its `output` keeps the last result
     * while a newer run is pending.
     */
    val bodySurfaceWork: Node<*, BodySurfaceWork> = graph.fieldWorkNode(
        graph.bodySurfaceWorkNode(
            document.map { doc -> doc.character.parts.map { it.id } }.distinctUntilChanged(),
            assembly,
            ::surfaceWork,
        ),
        sessionSurface.mesh,
    )

    /**
     * The character as its lines read it: what a view finds its contours and creases in and a surface tool picks on. Made with the
     * session, so [Graph.settled] covers it from the start. Its `output` keeps the last result while a newer run is pending.
     */
    val bodyLines: Node<*, BodyLines> = graph.bodyLinesNode(assembly, bodySurfaceWork)

    /**
     * Every part's color, cut to the mesh in each part's surface space, and each worn garment's. Made with the session, so
     * [Graph.settled] covers it from the start. Its `output` keeps the last result while a newer run is pending. See HLD: look, Color is vector.
     */
    val bodyColor: Node<*, BodyColor> = graph.lockLayersNode(
        graph.dressedColorNode(
            graph.shinedColorNode(
                graph.bodyColorNode(
                    document.map { doc -> doc.character.parts.map { it.id } }.distinctUntilChanged(),
                    ::heldColor,
                ),
                assembly,
                shines,
            ),
            document.map { garmentColors(it.character) + membraneColors(it.character) + lockColors(it.character) }.distinctUntilChanged(),
        ),
        assembly,
    )

    /** Every part's fur strokes with the grain and the slot under each, camera aside. Made with the session, so [Graph.settled] covers it from the start. */
    val bodyFur: Node<*, BodyFur> = graph.bodyFurNode(document, templates, assembly, bodySurfaceWork)

    /**
     * The character's head cheats that follow the camera, built once each assembly at rest, made when first asked for so a session no
     * view turns freely never builds them. See HLD: look, View keys.
     */
    val viewKeys: Node<*, ViewKeyCache> by lazy { graph.viewKeysNode(document, assembly, characterSurface) }

    private val pose = MutableStateFlow<Node<*, Held>?>(null)

    /**
     * The character in the pose [showPose] gave: [assembly] with each part the pose moves on its posed mesh, or [assembly]'s own result
     * while the pose leaves every part at rest. What follows a pose reads this, so `settled` covers it. See HLD: data-model, The rig.
     */
    val posedBody: Node<*, AssembledCharacter> = graph.posedBodyNode(assembly, pose, document.map { wornOver(it.character) }.distinctUntilChanged())

    private val posedState: Node<*, PosedState> = graph.posedStateNode(document, assembly, posedBody, bodySurfaceWork, sessionSurface.posed)

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

    /** The template [part] builds with, as the document holds it now: turned by Move, shaped by a generic part's choices. See [asBuilt]. */
    fun builtTemplate(part: PartInstance): PartTemplate = document.value.character.builtTemplate(part, templates[part.template])

    /** The node deriving [part]'s patches; there is one per part for the session's life. */
    fun patches(part: PartId): Node<PartBuildInput?, PartBuild> =
        rows.getOrPut(part) { graph.sliceRowNode(document, part, templates, lengths) }

    /**
     * The node deriving [part]'s mesh, in the part's own axes with its socket plane at a = 0; one per part for the
     * session's life. Its `output` keeps the last mesh while a newer run is pending or cancelled, and `failure` holds why
     * the latest build failed. A part not in the document has an empty mesh.
     */
    fun mesh(part: PartId): Node<PartBuild, PartMesh> = meshes.getOrPut(part) { graph.partMeshNode(patches(part), part) }

    /** What [part] shows on its side, derived from its mesh; one per part for the session's life, following its side. */
    fun surface(part: PartId): Node<*, PartSurface> = surfaces.getOrPut(part) {
        val instance = document.value.character.part(part)
        val template = instance?.let { templates.find(it.template) }
        val axes = template?.let(PartBuilder::axes) ?: PartAxes.of(Vec3.X)
        val joints = shownFace.map { doc -> template?.let { shownJoints(doc.character, part, it) }.orEmpty() }
        graph.partSurfaceNode(mesh(part), axes, document.map { it.character.part(part)?.side }, joints, template)
    }

    /** What [part]'s sockets are read from while a garment reshapes it, empty otherwise; one per part for the session's life. */
    private fun skeleton(part: PartId): Node<*, PartSurface> = skeletons.getOrPut(part) {
        val axes = document.value.character.part(part)?.let { templates.find(it.template) }?.let(PartBuilder::axes) ?: PartAxes.of(Vec3.X)
        val row = graph.skeletonRowNode(document, part, templates, lengths)
        graph.partSurfaceNode(graph.partMeshNode(row, part), axes, document.map { it.character.part(part)?.side })
    }

    /**
     * [part]'s surface work as positions in surface space, each point in the space of the part its anchor names; one per part
     * for the session's life. A part not in the document holds nothing.
     */
    fun surfaceWork(part: PartId): Node<*, ResolvedSurfaceWork> =
        works.getOrPut(part) { graph.partSurfaceWorkNode(shownFace, part, ::surface, templates) }

    /** [part]'s color shapes where they land, before they are cut; one per part for the session's life. */
    fun heldShapes(part: PartId): Node<*, HeldShapes> = shapes.getOrPut(part) {
        graph.partShapesNode(partColorInputs(shownFace, part, templates), part, surfaceWork(part), colorDetail, ::surface)
    }

    /** [part]'s color shapes, cut to the surfaces they land on; one per part for the session's life. */
    fun heldColor(part: PartId): Node<*, HeldColor> = colors.getOrPut(part) { graph.partColorNode(heldShapes(part)) }

    private fun defaultSurface(template: TemplateRef, mirrored: Boolean): Node<*, PartSurface> =
        defaults.getOrPut(template to mirrored) { graph.defaultSurfaceNode(
            templates[template],
            mirrored,
            plannedLengthFactor(document, template, templates, lengths),
            templates.plan(document.value.character.bodyPlan)?.spreadFor(template.id) ?: 0f,
        )
    }
}
