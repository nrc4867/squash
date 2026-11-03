package dev.chieppa.squash.character.session

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.sliceRowNode
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SquashDocument
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

    private val rows = mutableMapOf<PartId, Node<PartInstance?, List<Patch>>>()

    fun send(command: DocumentCommand) = history.execute(command)

    /** The node deriving [part]'s patches; there is one per part for the session's life. */
    fun patches(part: PartId): Node<PartInstance?, List<Patch>> =
        rows.getOrPut(part) { graph.sliceRowNode(document, part, templates) }
}
