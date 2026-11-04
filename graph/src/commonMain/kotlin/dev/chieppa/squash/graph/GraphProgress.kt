package dev.chieppa.squash.graph

/** How many of the nodes behind a stage have finished: [done] of [total]. */
data class StageCount(val done: Int, val total: Int) {
    val finished: Boolean get() = done >= total
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total
}

/**
 * How far the graph is through what it has to redo: of the nodes dirtied since it last settled, how many are clean again,
 * in all and by the stage each was named for. Counts nodes, not time, so it steps as runs finish. Empty when settled.
 */
data class GraphProgress(val done: Int, val total: Int, val stages: Map<String, StageCount> = emptyMap()) {
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total

    /** The first of [order] with a node still to finish. */
    fun working(order: List<String>): String? = order.firstOrNull { stages[it]?.finished == false }

    companion object {
        val SETTLED = GraphProgress(0, 0)
    }
}
