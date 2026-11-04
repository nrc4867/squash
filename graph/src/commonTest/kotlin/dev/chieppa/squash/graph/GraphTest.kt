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

    @Test
    fun progressCountsTheNodesARunDirtied() = runTest {
        val graph = graph()
        val input = MutableStateFlow(1)
        val unrelated = graph.node(MutableStateFlow(9), stage = "other") { it }
        val gate = CompletableDeferred<Unit>()
        val first = graph.node(input, stage = "first") { it }
        val second = graph.follow(first, stage = "second") {
            if (it == 2) gate.await()
            it
        }
        runCurrent()
        assertEquals(GraphProgress.SETTLED, graph.progress.value)
        assertEquals(9, unrelated.output.value)

        input.value = 2
        assertEquals(GraphProgress(0, 2, mapOf("first" to StageCount(0, 1), "second" to StageCount(0, 1))), graph.progress.value)
        assertEquals("first", graph.progress.value.working(listOf("first", "second")))
        runCurrent()
        assertEquals(1, graph.progress.value.done, "the first node is done and the second waits")
        assertEquals(0.5f, graph.progress.value.fraction)
        assertEquals("second", graph.progress.value.working(listOf("first", "second")))
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, second.output.value)
        assertEquals(GraphProgress.SETTLED, graph.progress.value)

        input.value = 3
        assertEquals(2, graph.progress.value.total, "the next run counts from nothing again")
        runCurrent()
    }

    @Test
    fun aGatheredNodeWaitsForEverySourceAndRerunsWithAny() = runTest {
        val graph = graph()
        val a = MutableStateFlow(1)
        val b = MutableStateFlow(2)
        val left = graph.node(a) { it * 10 }
        val right = graph.node(b) { it * 10 }
        val sum = graph.gather(MutableStateFlow("both"), { mapOf("a" to left, "b" to right) }) { (_, values) -> values.values.sum() }
        assertFalse(graph.settled.value)
        assertNull(sum.output.value)
        runCurrent()
        assertEquals(30, sum.output.value)
        assertTrue(graph.settled.value)
        b.value = 5
        assertFalse(graph.settled.value, "dirty from the moment a source is")
        runCurrent()
        assertEquals(60, sum.output.value)
        assertTrue(graph.settled.value)
    }

    @Test
    fun aGatheredNodeFollowsItsShapeAndDropsWhatLeftIt() = runTest {
        val graph = graph()
        val shape = MutableStateFlow(listOf("a"))
        val sources = mutableMapOf<String, Node<*, Int>>()
        val inputs = mapOf("a" to MutableStateFlow(1), "b" to MutableStateFlow(2))
        val sum = graph.gather(shape, { names -> names.associateWith { name -> sources.getOrPut(name) { graph.node(inputs.getValue(name)) { it } } } }) { (_, values) -> values.values.sum() }
        runCurrent()
        assertEquals(1, sum.output.value)
        shape.value = listOf("a", "b")
        assertFalse(graph.settled.value)
        runCurrent()
        assertEquals(3, sum.output.value)
        shape.value = listOf("b")
        runCurrent()
        assertEquals(2, sum.output.value)
        inputs.getValue("a").value = 100
        runCurrent()
        assertEquals(2, sum.output.value, "a source no longer named reaches nothing")
        assertTrue(graph.settled.value)
    }

    @Test
    fun aGatheredNodeTakesASourcesFailureKeepsItsResultAndRecovers() = runTest {
        val graph = graph()
        val input = MutableStateFlow(1)
        val source = graph.node(input) { if (it < 0) error("negative") else it }
        val gathered = graph.gather(MutableStateFlow(Unit), { mapOf("s" to source) }) { (_, values) -> values.getValue("s") * 2 }
        runCurrent()
        assertEquals(2, gathered.output.value)
        input.value = -1
        runCurrent()
        assertIs<IllegalStateException>(gathered.failure.value)
        assertEquals(2, gathered.output.value)
        assertTrue(graph.settled.value)
        input.value = 4
        runCurrent()
        assertNull(gathered.failure.value)
        assertEquals(8, gathered.output.value)
    }
}
