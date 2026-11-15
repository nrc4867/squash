package dev.chieppa.squash.screens.look.drag

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.rig.SessionPose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * A character opened as the app opens it: a session and its pose on the app's own dispatcher, every node of the graph the app makes for
 * it, with each named node's results watched, so a step can say which nodes ran and when each finished.
 */
class DragSession(document: SquashDocument, templates: TemplateLibrary) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val session = CharacterSession(document, templates, scope, Dispatchers.Default)
    val pose = SessionPose(session)

    /** When each watched node last gave a result, in nanoseconds of [System.nanoTime], since [mark]. */
    private val finished = ConcurrentHashMap<String, Long>()
    private var watching = false

    init {
        watched().forEach { (name, node) -> watch(name, node) }
        scope.launch(Dispatchers.Unconfined) { session.oneSurface.posed.collect { node -> node?.let { watch("posed surface", it) } } }
        settle()
    }

    /** Forgets the results seen so far, so [ran] holds only the nodes that give one after now. */
    fun mark() {
        finished.clear()
        watching = true
    }

    /** The watched nodes that gave a result since [mark], each with when it last did, in ms after [from]. */
    fun ran(from: Long): Map<String, Double> = finished.mapValues { (_, at) -> (at - from) / 1e6 }

    /** Returns once nothing in the session's graphs is dirty or running. */
    fun settle() = runBlocking { combine(session.graph.settled, session.viewGraph.settled) { a, b -> a && b }.first { it } }

    private fun watch(name: String, node: Node<*, *>) {
        scope.launch(Dispatchers.Unconfined) {
            var first = true
            node.output.collect {
                if (!first && watching) finished[name] = System.nanoTime()
                first = false
            }
        }
    }

    /** The session's nodes a step is reported by, by name; each part's own nodes are named with the part. */
    private fun watched(): List<Pair<String, Node<*, *>>> = buildList {
        add("placed" to session.placed)
        add("assembly" to session.assembly)
        add("surface mesh" to session.oneSurface.mesh)
        add("surface" to session.characterSurface)
        add("paint" to session.surfacePaint)
        add("dress" to session.oneSurface.dress)
        add("shown paint" to session.oneSurface.shownPaint)
        add("surface work" to session.bodySurfaceWork)
        add("lines" to session.bodyLines)
        add("color" to session.bodyColor)
        add("fur" to session.bodyFur)
        add("posed body" to session.posedBody)
        add("posed surface work" to session.posedSurfaceWork)
        add("posed lines" to session.posedLines)
        add("posed fur" to session.posedFur)
        add("rig" to pose.rig.rig)
        add("posed parts" to pose.posed)
        add("skin" to pose.surface.skin)
        for (part in session.document.value.character.parts) {
            add("part slices" to session.patches(part.id))
            add("part mesh" to session.mesh(part.id))
            add("part surface" to session.surface(part.id))
            add("part color" to session.heldColor(part.id))
        }
    }.groupBy({ it.first }, { it.second }).flatMap { (name, nodes) -> if (nodes.size == 1) listOf(name to nodes[0]) else nodes.mapIndexed { i, n -> "$name ${i + 1}" to n } }

    override fun close() = scope.cancel()
}
