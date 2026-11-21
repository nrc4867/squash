package dev.chieppa.squash.screens.look.recipe.server

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One build for the [BuildQueue]: [run] is the build, its exit status; [size] orders the waiting builds, smaller first; two builds of
 * one [key] (the folder they write) never run at once; [abandon] is called when the build outlives [timeout].
 */
class QueuedBuild(
    val name: String,
    val size: Int,
    val key: String,
    val timeout: Duration,
    val abandon: () -> Unit = {},
    val run: suspend () -> Int,
)

/** How a queued build ended. */
sealed interface BuildOutcome {
    /** It ran to the end with exit status [code]. */
    data class Done(val code: Int) : BuildOutcome

    /** It threw [error]; the queue and the other builds run on. */
    data class Failed(val error: Throwable) : BuildOutcome

    /** It outlived its timeout; its place was given up. */
    data object TimedOut : BuildOutcome
}

/** A build running now, for the status: its [id], [name] and how long it has run. */
data class RunningBuild(val id: Long, val name: String, val running: Duration)

/** What the queue holds: the builds [running], how many are [waiting], and whether it still [accepting] builds. */
data class QueueStatus(val running: List<RunningBuild>, val waiting: Int, val accepting: Boolean, val limit: Int)

/**
 * Builds run at most [limit] at a time, the smallest waiting one first, then in the order they came. A build runs on [worker]; the queue's
 * own bookkeeping runs in [scope]. A build that throws or outlives its timeout ends alone, and the next one starts. [clock] times the
 * running builds for the status.
 */
class BuildQueue(
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher,
    private val limit: Int,
    private val clock: TimeSource = TimeSource.Monotonic,
) {
    private class Entry(val id: Long, val build: QueuedBuild, val outcome: CompletableDeferred<BuildOutcome>) {
        var started: TimeMark? = null
    }

    private val lock = Any()
    private val waiting = mutableListOf<Entry>()
    private val running = mutableListOf<Entry>()
    private var accepting = true
    private var next = 0L
    private val busy = MutableStateFlow(0)

    /** How many builds are waiting or running. */
    val held: StateFlow<Int> = busy.asStateFlow()

    /** Queues [build]: its outcome, or null once the queue is closed. */
    fun submit(build: QueuedBuild): Deferred<BuildOutcome>? {
        val entry = synchronized(lock) {
            if (!accepting) return null
            Entry(next++, build, CompletableDeferred()).also { waiting += it }
        }
        pump()
        return entry.outcome
    }

    /** Stops taking builds; those queued already still run. */
    fun close() = synchronized(lock) {
        accepting = false
        publish()
    }

    /** Waits until nothing is queued or running. */
    suspend fun drained() {
        busy.first { it == 0 }
    }

    fun status(): QueueStatus = synchronized(lock) {
        QueueStatus(running.map { RunningBuild(it.id, it.build.name, it.started?.elapsedNow() ?: Duration.ZERO) }, waiting.size, accepting, limit)
    }

    private fun pump() {
        val starting = mutableListOf<Entry>()
        synchronized(lock) {
            while (running.size < limit) {
                val free = waiting.filter { w -> running.none { it.build.key == w.build.key } && starting.none { it.build.key == w.build.key } }
                val chosen = free.minWithOrNull(compareBy<Entry>({ it.build.size }, { it.id })) ?: break
                waiting -= chosen
                running += chosen
                chosen.started = clock.markNow()
                starting += chosen
            }
            publish()
        }
        starting.forEach(::start)
    }

    private fun start(entry: Entry) {
        scope.launch {
            val work = scope.async(worker) { runCatching { entry.build.run() } }
            val result = withTimeoutOrNull(entry.build.timeout) { work.await() }
            val outcome = when {
                result == null -> {
                    runCatching { entry.build.abandon() }
                    BuildOutcome.TimedOut
                }
                result.isSuccess -> BuildOutcome.Done(result.getOrThrow())
                else -> BuildOutcome.Failed(result.exceptionOrNull()!!)
            }
            synchronized(lock) {
                running -= entry
                publish()
            }
            entry.outcome.complete(outcome)
            pump()
        }
    }

    private fun publish() {
        busy.value = waiting.size + running.size
    }
}
