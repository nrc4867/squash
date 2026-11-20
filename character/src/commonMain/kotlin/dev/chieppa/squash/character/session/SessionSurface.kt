package dev.chieppa.squash.character.session

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.build.ShineSource
import dev.chieppa.squash.character.surface.HeldDress
import dev.chieppa.squash.character.surface.HeldPaint
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.character.surface.surfaceDressNode
import dev.chieppa.squash.character.surface.HeldSurface
import dev.chieppa.squash.character.surface.PaintInput
import dev.chieppa.squash.character.surface.HeldPosedSurface
import dev.chieppa.squash.character.surface.HeldMesh
import dev.chieppa.squash.character.surface.characterMeshNode
import dev.chieppa.squash.character.surface.characterSurfaceNode
import dev.chieppa.squash.character.surface.surfaceGarmentsNode
import dev.chieppa.squash.character.surface.shownPaintNode
import dev.chieppa.squash.character.surface.surfacePaintNode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.stretch.core.History
import kotlinx.coroutines.flow.Flow
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A session's character as one surface, made with the session so [Graph.settled] covers its nodes from the start: the [surface] meshed
 * ([mesh]) and finished from its parts [placed] on their sockets, at the coarse cell while a gesture is open, null for a character with
 * no part in the field; the [paint] laid on it, every part's color shapes from [shapes] by their coordinates and its painted eyes; the
 * [dress] it wears; and the node that shows it posed, which the rig gives through [showPosed]. The body's and the garments' meshers read
 * their fields through [reads].
 */
class SessionSurface(
    graph: Graph,
    history: History<SquashDocument>,
    placed: Node<*, AssembledCharacter>,
    shapes: (PartId) -> Node<*, HeldShapes>,
    reads: FieldReads = CpuFieldReads,
    shines: Flow<List<ShineSource>> = flowOf(emptyList()),
) {
    /** Whether a gesture is open on the history: a drag in progress. */
    private val gesture: Flow<Boolean> = combine(history.document, history.steps) { _, _ -> history.inGesture }.distinctUntilChanged()

    /** Whether a pane draws the surface's weights or its posed form; the views set it. While none does, the coarse surface of a drag is not skinned. */
    val skinShown = MutableStateFlow(true)

    /** The character meshed as one surface, before its surface is finished. */
    val mesh: Node<*, HeldMesh> = graph.characterMeshNode(
        placed,
        gesture,
        reads,
    )

    val surface: Node<*, HeldSurface> = graph.characterSurfaceNode(mesh)

    val paint: Node<*, HeldPaint> = graph.surfacePaintNode(
        surface,
        combine(history.document, shines) { doc, shine -> PaintInput(doc.character.parts.map { it.id }, doc.character.paintedEyes, shine) }.distinctUntilChanged(),
        shapes,
    )

    /** What the surface wears: each garment of the shown outfit grown from it and cut along its coverage, and the skin it hides. */
    val dress: Node<*, HeldDress> = graph.surfaceDressNode(
        surface,
        graph.surfaceGarmentsNode(mesh, history.document.map { surfaceCovers(it.character) }.distinctUntilChanged(), reads),
    )

    /** The [paint] as it shows under the [dress]: kept to the skin the garments leave showing. */
    val shownPaint: Node<*, HeldPaint> = graph.shownPaintNode(surface, paint, dress)

    private val shownPosed = MutableStateFlow<Node<*, HeldPosedSurface>?>(null)

    /** The node that gives the surface in the shown pose, null until the rig gives one. */
    val posed: StateFlow<Node<*, HeldPosedSurface>?> = shownPosed.asStateFlow()

    /** Shows the surface as [node], a node of the session's graph, poses it. */
    fun showPosed(node: Node<*, HeldPosedSurface>) {
        shownPosed.value = node
    }
}
