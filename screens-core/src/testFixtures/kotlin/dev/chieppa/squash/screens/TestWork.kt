package dev.chieppa.squash.screens

import dev.chieppa.squash.character.session.CharacterSession
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher

/**
 * Workers for a test: the graph, the art and file reads queue on one scheduler and run only when the test [drain]s it, on the
 * test's own thread. [context] joins the scope, for a handler that collects failures. [TestWorkCloser] closes every one when its
 * test ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestWork private constructor(val dispatcher: TestDispatcher, context: CoroutineContext) :
    Workers(CoroutineScope(SupervisorJob() + dispatcher + context), dispatcher, dispatcher), AutoCloseable {
    init {
        TestWorkCloser.opened(this)
    }

    constructor(context: CoroutineContext = EmptyCoroutineContext) : this(StandardTestDispatcher(TestCoroutineScheduler()), context)

    /** Runs everything queued, and everything that queues while it runs. */
    fun drain() = dispatcher.scheduler.advanceUntilIdle()

    /** Runs everything queued and checks that [session] has settled. */
    fun settle(session: CharacterSession) {
        drain()
        assertTrue(session.settled, "the graph settles once its work has run")
    }

    /** Cancels everything this work started, the sessions of an [AppState] built on it among them. */
    override fun close() = scope.cancel()
}

/** Whether nothing in the session's graph or view graph is dirty or running. */
val CharacterSession.settled: Boolean get() = graph.settled.value && viewGraph.settled.value
