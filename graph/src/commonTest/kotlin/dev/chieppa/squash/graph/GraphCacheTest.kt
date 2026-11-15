package dev.chieppa.squash.graph

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GraphCacheTest {
    @Test
    fun aCacheHoldsTheResultsMostRecentlyMadeOrTaken() = runTest {
        var keeping = true
        val graph = Graph(backgroundScope, StandardTestDispatcher(testScheduler)) { keeping }
        val input = MutableStateFlow(1)
        val runs = mutableListOf<Int>()
        graph.node(input, cacheSize = 2) { runs += it; it * 10 }
        runCurrent()
        input.value = 2
        runCurrent()
        keeping = false
        input.value = 3
        runCurrent()
        keeping = true
        input.value = 1
        runCurrent()
        input.value = 3
        runCurrent()
        input.value = 4
        runCurrent()
        input.value = 2
        runCurrent()
        input.value = 4
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 2), runs)
    }

    @Test
    fun theLatestResultMadeWhileNotKeepingIsTakenWhenItsInputComesAgain() = runTest {
        var keeping = false
        val graph = Graph(backgroundScope, StandardTestDispatcher(testScheduler)) { keeping }
        val input = MutableStateFlow(1)
        val runs = mutableListOf<Int>()
        graph.node(input, cacheSize = 2) { runs += it; it * 10 }
        runCurrent()
        input.value = 2
        runCurrent()
        keeping = true
        input.value = 1
        runCurrent()
        input.value = 2
        runCurrent()
        input.value = 1
        runCurrent()
        assertEquals(listOf(1, 2, 1), runs)
    }
}
