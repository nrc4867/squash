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

    private fun changed() {
        changes.update { it + 1 }
    }
}
