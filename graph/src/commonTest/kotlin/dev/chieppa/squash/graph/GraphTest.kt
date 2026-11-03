package dev.chieppa.squash.graph

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GraphTest {
    private fun TestScope.graph() = Graph(backgroundScope, StandardTestDispatcher(testScheduler))

    @Test
    fun aNodeComputesItsFirstInputAndSettles() = runTest {
        val graph = graph()
        val input = MutableStateFlow(2)
        val node = graph.node(input) { it * 10 }
        assertFalse(graph.settled.value)
        assertNull(node.output.value)
        runCurrent()
        assertEquals(20, node.output.value)
        assertTrue(graph.settled.value)
    }

    @Test
    fun aGraphIsDirtyInsideTheCallThatSetsTheInput() = runTest {
        val graph = graph()
        val input = MutableStateFlow(1)
        graph.node(input.map { it + 1 }) { it }
        runCurrent()
        assertTrue(graph.settled.value)
        input.value = 2
        assertFalse(graph.settled.value)
        runCurrent()
        assertTrue(graph.settled.value)
    }

    @Test
    fun aNewInputCancelsTheStaleRunAndKeepsTheOldResultMeanwhile() = runTest {
        val graph = graph()
        val input = MutableStateFlow(1)
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        val finished = mutableListOf<Int>()
        val node = graph.node(input) {
            started += it
            if (it == 2) gate.await()
            finished += it
            it
        }
        runCurrent()
        assertEquals(1, node.output.value)

        input.value = 2
        runCurrent()
        assertEquals(1, node.output.value)
        assertFalse(graph.settled.value)

        input.value = 3
        runCurrent()
        assertEquals(3, node.output.value)
        assertTrue(graph.settled.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2, 3), started)
        assertEquals(listOf(1, 3), finished)
        assertEquals(3, node.output.value)
    }

    @Test
    fun aCachedInputPublishesWithoutARun() = runTest {
        val graph = graph()
        val input = MutableStateFlow(1)
        var runs = 0
        val node = graph.node(input) {
            runs++
            it * 10
        }
        runCurrent()
        input.value = 2
        runCurrent()
        assertEquals(2, runs)

        input.value = 1
        assertEquals(10, node.output.value)
        assertTrue(graph.settled.value)
        assertEquals(2, runs)
    }

    @Test
    fun theCacheForgetsItsOldestEntry() = runTest {
        val graph = graph()
        val input = MutableStateFlow(1)
        var runs = 0
        graph.node(input, cacheSize = 1) {
            runs++
            it
        }
        runCurrent()
        input.value = 2
        runCurrent()
        input.value = 1
        runCurrent()
        assertEquals(3, runs)
    }

    @Test
    fun aFailedRunSettlesKeepsTheOldResultAndClearsOnSuccess() = runTest {
        val graph = graph()
        val input = MutableStateFlow(1)
        val node = graph.node(input) {
            check(it != 2) { "No" }
            it
        }
        runCurrent()
        input.value = 2
        runCurrent()
        assertTrue(graph.settled.value)
        assertEquals(1, node.output.value)
        assertIs<IllegalStateException>(node.failure.value)

        input.value = 3
        runCurrent()
        assertEquals(3, node.output.value)
        assertNull(node.failure.value)
    }

    @Test
    fun aGraphWithTwoNodesSettlesOnlyWhenBothAre() = runTest {
        val graph = graph()
        val gate = CompletableDeferred<Unit>()
        graph.node(MutableStateFlow(1)) { it }
        graph.node(MutableStateFlow(2)) {
            gate.await()
            it
        }
        runCurrent()
        assertFalse(graph.settled.value)
        gate.complete(Unit)
        runCurrent()
        assertTrue(graph.settled.value)
    }

    @Test
    fun aFollowingNodeRecomputesFromTheResultAndTheGraphWaitsForIt() = runTest {
        val graph = graph()
        val input = MutableStateFlow(2)
        val first = graph.node(input) { it * 10 }
        val second = graph.follow(first) { it + 1 }
        assertFalse(graph.settled.value)
        runCurrent()
        assertEquals(21, second.output.value)
        assertTrue(graph.settled.value)

        input.value = 3
        assertFalse(graph.settled.value)
        assertEquals(21, second.output.value)
        runCurrent()
        assertEquals(31, second.output.value)
        assertTrue(graph.settled.value)
    }

    @Test
    fun theGraphIsNeverSettledWithAFollowingNodeBehind() = runTest {
        val graph = Graph(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        val input = MutableStateFlow(1)
        val first = graph.node(input) { it * 10 }
        val second = graph.follow(first) { it + 1 }
        runCurrent()
        backgroundScope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            graph.settled.collect { settled ->
                if (settled) assertEquals(first.output.value!! + 1, second.output.value)
            }
        }
        for (value in listOf(2, 1, 3, 1)) {
            input.value = value
            runCurrent()
            assertTrue(graph.settled.value)
            assertEquals(value * 10 + 1, second.output.value)
        }
    }

    @Test
    fun aFailureUpstreamReachesTheFollowingNodeAndItKeepsItsResult() = runTest {
        val graph = graph()
        val input = MutableStateFlow(1)
        val first = graph.node(input) { require(it < 2) { "too big" }; it }
        val second = graph.follow(first) { it + 1 }
        runCurrent()
        assertEquals(2, second.output.value)

        input.value = 2
        runCurrent()
        assertTrue(graph.settled.value)
        assertIs<IllegalArgumentException>(second.failure.value)
        assertEquals(2, second.output.value)

        input.value = 1
        runCurrent()
        assertNull(second.failure.value)
        assertEquals(2, second.output.value)
    }
}
