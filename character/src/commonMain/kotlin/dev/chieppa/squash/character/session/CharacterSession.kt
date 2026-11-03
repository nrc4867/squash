package dev.chieppa.squash.character.session

import dev.chieppa.squash.character.build.PartBuildInput
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.partMeshNode
import dev.chieppa.squash.character.build.sliceRowNode
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.stretch.core.History
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

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

    private val rows = mutableMapOf<PartId, Node<PartBuildInput?, List<Patch>>>()
    private val meshes = mutableMapOf<PartId, Node<List<Patch>, PartMesh>>()

    fun send(command: DocumentCommand) = history.execute(command)

    /** The node deriving [part]'s patches; there is one per part for the session's life. */
    fun patches(part: PartId): Node<PartBuildInput?, List<Patch>> =
        rows.getOrPut(part) { graph.sliceRowNode(document, part, templates) }

    /**
     * The node deriving [part]'s mesh, in the part's own axes with its socket plane at a = 0; one per part for the
     * session's life. Its `output` keeps the last mesh while a newer run is pending or cancelled, and `failure` holds why
     * the latest build failed. A part not in the document has an empty mesh.
     */
    fun mesh(part: PartId): Node<List<Patch>, PartMesh> = meshes.getOrPut(part) {
        graph.partMeshNode(patches(part), part) {
            document.value.character.part(part)?.let { templates[it.template] }
        }
    }
}
