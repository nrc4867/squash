package dev.chieppa.squash.graph

import dev.chieppa.stretch.core.Settled
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val DEFAULT_CACHE_SIZE = 64

/** What a gathered node reads: the [shape] its sources came from and each source's latest result. */
data class Gathered<S, K, V>(val shape: S, val values: Map<K, V>)

/** The derived-data graph. Settled when no node is dirty or running. Runs compute on [dispatcher], inside [scope]. */
class Graph(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Settled {
    private val changes = MutableStateFlow(0L)
    private val nodes = MutableStateFlow<List<Node<*, *>>>(emptyList())

    override val settled: StateFlow<Boolean> = DerivedStateFlow({ nodes.value.all { it.isClean } }, changes)

    /**
     * A node that recomputes from each value of [input]. [input] must emit its current value on collection, as a
     * state flow and its maps do. The node is dirty from the moment a new value is set, before anything runs.
     */
    fun <I, O : Any> node(input: Flow<I>, cacheSize: Int = DEFAULT_CACHE_SIZE, compute: suspend (I) -> O): Node<I, O> {
        val node = Node(scope, dispatcher, cacheSize, compute, ::changed)
        nodes.update { it + node }
        node.start(input)
        return node
    }

    /**
     * A node that recomputes from each result of [upstream]. The graph stays unsettled until that result has reached this
     * node and run. A failure of [upstream] becomes this node's failure, keeping its last result.
     */
    fun <I : Any, O : Any> follow(upstream: Node<*, I>, cacheSize: Int = DEFAULT_CACHE_SIZE, compute: suspend (I) -> O): Node<I, O> {
        val node = Node(scope, dispatcher, cacheSize, compute, ::changed)
        nodes.update { it + node }
        node.follow(upstream)
        return node
    }

    /**
     * A node that reads a set of nodes that changes with [shape]: for each value of it, [sourcesOf] names them, and the node
     * recomputes from that value and their results. The graph stays unsettled until every result has reached this node and
     * run. A failure of any source becomes this node's, keeping its last result. [shape] must emit its current value on
     * collection, as a state flow and its maps do.
     */
    fun <S, K, V : Any, O : Any> gather(
        shape: Flow<S>,
        sourcesOf: (S) -> Map<K, Node<*, V>>,
        cacheSize: Int = DEFAULT_CACHE_SIZE,
        compute: suspend (Gathered<S, K, V>) -> O,
    ): Node<Gathered<S, K, V>, O> {
        val node = Node(scope, dispatcher, cacheSize, compute, ::changed)
        nodes.update { it + node }
        node.gather(shape, sourcesOf, ::Gathered)
        return node
    }

    private fun changed() {
        changes.update { it + 1 }
    }
}
