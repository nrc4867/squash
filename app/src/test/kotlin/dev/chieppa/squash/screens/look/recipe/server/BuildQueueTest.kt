package dev.chieppa.squash.screens.look.recipe.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class BuildQueueTest {
    private fun TestScope.queue(limit: Int = 2) = BuildQueue(backgroundScope, StandardTestDispatcher(testScheduler), limit, testScheduler.timeSource)

    /** A build that runs until its [gate] is completed, with its exit status. */
    private class Gated(val name: String, size: Int = 1, key: String = name, timeout: Duration = 10.minutes) {
        val gate = CompletableDeferred<Int>()
        var abandoned = false
        val build = QueuedBuild(name, size, key, timeout, abandon = { abandoned = true }) { gate.await() }
    }

    private fun BuildQueue.runningNames() = status().running.map { it.name }

    @Test
    fun `runs at most its limit at once and starts the next when one ends`() = runTest {
        val queue = queue(limit = 2)
        val (a, b, c) = listOf(Gated("a"), Gated("b"), Gated("c"))
        val outcomes = listOf(a, b, c).map { checkNotNull(queue.submit(it.build)) }
        runCurrent()
        assertEquals(listOf("a", "b"), queue.runningNames())
        assertEquals(1, queue.status().waiting)
        a.gate.complete(0)
        runCurrent()
        assertEquals(BuildOutcome.Done(0), outcomes[0].await())
        assertEquals(listOf("b", "c"), queue.runningNames())
    }

    @Test
    fun `the smallest waiting build starts first, then the oldest`() = runTest {
        val queue = queue(limit = 1)
        val first = Gated("first")
        val large = Gated("large", size = 2)
        val medium = Gated("medium", size = 1)
        val small = Gated("small", size = 0)
        listOf(first, large, medium, small).forEach { queue.submit(it.build) }
        runCurrent()
        first.gate.complete(0)
        runCurrent()
        assertEquals(listOf("small"), queue.runningNames())
        small.gate.complete(0)
        runCurrent()
        assertEquals(listOf("medium"), queue.runningNames())
    }

    @Test
    fun `two builds writing one folder never run at once`() = runTest {
        val queue = queue(limit = 2)
        val one = Gated("one", key = "out/fox")
        val two = Gated("two", key = "out/fox")
        val other = Gated("other", key = "out/dog")
        listOf(one, two, other).forEach { queue.submit(it.build) }
        runCurrent()
        assertEquals(listOf("one", "other"), queue.runningNames())
        one.gate.complete(0)
        runCurrent()
        assertEquals(listOf("other", "two"), queue.runningNames())
    }

    @Test
    fun `a build that throws ends alone and the next one runs`() = runTest {
        val queue = queue(limit = 1)
        val failing = queue.submit(QueuedBuild("bad", 1, "bad", 10.minutes) { error("no device") })!!
        val next = queue.submit(QueuedBuild("good", 1, "good", 10.minutes) { 0 })!!
        advanceUntilIdle()
        assertIs<BuildOutcome.Failed>(failing.await())
        assertEquals(BuildOutcome.Done(0), next.await())
    }

    @Test
    fun `a build past its timeout is abandoned and its place given to the next`() = runTest {
        val queue = queue(limit = 1)
        val stuck = Gated("stuck", timeout = 5.minutes)
        val after = Gated("after")
        val outcome = queue.submit(stuck.build)!!
        queue.submit(after.build)
        runCurrent()
        advanceTimeBy(5.minutes.inWholeMilliseconds + 1)
        runCurrent()
        assertEquals(BuildOutcome.TimedOut, outcome.await())
        assertTrue(stuck.abandoned)
        assertEquals(listOf("after"), queue.runningNames())
    }

    @Test
    fun `the status times each running build by the clock`() = runTest {
        val queue = queue()
        queue.submit(Gated("timed").build)
        runCurrent()
        advanceTimeBy(90_000)
        assertEquals(90, queue.status().running.single().running.inWholeSeconds)
    }

    @Test
    fun `a closed queue takes no more builds and drains once those it took are done`() = runTest {
        val queue = queue(limit = 1)
        val a = Gated("a")
        val b = Gated("b")
        queue.submit(a.build)
        queue.submit(b.build)
        queue.close()
        assertNull(queue.submit(Gated("late").build))
        var drained = false
        backgroundScope.launch {
            queue.drained()
            drained = true
        }
        runCurrent()
        a.gate.complete(0)
        runCurrent()
        assertEquals(false, drained)
        b.gate.complete(0)
        runCurrent()
        assertTrue(drained)
        assertEquals(false, queue.status().accepting)
    }
}
