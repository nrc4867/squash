package dev.chieppa.squash.graph

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * One step of derived data. A new input cancels the run in progress and starts another, unless the cache already holds
 * that input's result. [output] keeps the previous result until the new one is ready.
 */
class Node<I, O : Any> internal constructor(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val cacheSize: Int,
    private val compute: suspend (I) -> O,
    private val changed: () -> Unit,
) {
    private class Published<O>(val generation: Long, val value: O?, val failure: Throwable?)

    private val latest = MutableStateFlow(0L)
    private val completed = MutableStateFlow(0L)
    private val published = MutableStateFlow(Published<O>(0L, null, null))
    private val cache = MutableStateFlow<Map<I, O>>(emptyMap())
    private var running: Job? = null

    val output: StateFlow<O?> = DerivedStateFlow({ published.value.value }, published)

    /** What the latest run threw, or null if it succeeded. */
    val failure: StateFlow<Throwable?> = DerivedStateFlow({ published.value.failure }, published)

    internal val isClean: Boolean get() = completed.value >= latest.value

    /** Collects unconfined, so a new input marks the node dirty inside the call that set it. */
    internal fun start(input: Flow<I>) {
        scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { input.collect(::accept) }
    }

    private fun accept(value: I) {
        val generation = latest.updateAndGet { it + 1 }
        changed()
        running?.cancel()
        val cached = cache.value[value]
        if (cached != null) {
            finish(generation, cached, null)
            return
        }
        running = scope.launch(dispatcher) {
            try {
                val result = compute(value)
                remember(value, result)
                finish(generation, result, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finish(generation, null, e)
            }
        }
    }

    private fun remember(input: I, result: O) {
        cache.update { old ->
            val next = LinkedHashMap(old)
            next[input] = result
            if (next.size > cacheSize) next.remove(next.keys.first())
            next
        }
    }

    /** Generations only move forward, so a stale run that finishes late never replaces a newer result. */
    private fun finish(generation: Long, value: O?, failure: Throwable?) {
        published.update { old ->
            if (old.generation > generation) old else Published(generation, value ?: old.value, failure)
        }
        completed.update { maxOf(it, generation) }
        changed()
    }
}
