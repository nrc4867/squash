package dev.chieppa.squash.graph

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GraphEpochTest {
    @Test
    fun aNodeFollowingAnotherGraphSettlesWhenThatGraphDoesWithNothingNewForIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = Graph(backgroundScope, dispatcher)
        val second = Graph(backgroundScope, dispatcher)
        val input = MutableStateFlow(1)
        val parity = first.follow(first.node(input) { it }) { it % 2 }
        val gathered = first.gather(MutableStateFlow(Unit), { mapOf(Unit to parity) }) { it.values.getValue(Unit) }
        second.follow(gathered) { it }
        runCurrent()
        assertTrue(second.settled.value)
        input.value = 3
        assertFalse(second.settled.value, "the node it follows waits on the new input")
        runCurrent()
        assertTrue(first.settled.value)
        assertTrue(second.settled.value, "the same parity gives the gathered node nothing new to pass on")
    }
}
