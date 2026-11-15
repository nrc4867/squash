package dev.chieppa.squash.graph

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A count every graph moves on at each of its changes, which a node's cleanness is read once for. Shared by all graphs, since a node may
 * follow a node of another graph, whose changes its cleanness must see.
 */
internal object Epoch {
    private val count = MutableStateFlow(0L)

    fun now(): Long = count.value

    fun move() = count.update { it + 1 }
}
