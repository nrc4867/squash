package dev.chieppa.squash.graph

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * One step of derived data. A new input cancels the run in progress and starts another, unless the cache already holds
 * that input's result. [output] keeps the previous result until the new one is ready. The cache holds the results most recently made
 * or taken from it, of runs that started while [keeps] said so; of the others it holds only the latest, which joins the rest if its
 * input comes again.
 */
class Node<I, O : Any> internal constructor(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val cacheSize: Int,
    internal val stage: String?,
    private val compute: suspend (I) -> O,
    private val changed: () -> Unit,
    private val keeps: () -> Boolean = { true },
    private val epoch: () -> Long = { NO_EPOCH },
) {
    private class Published<O>(val generation: Long, val value: O?, val failure: Throwable?)

    private val latest = MutableStateFlow(0L)
    private val completed = MutableStateFlow(0L)
    private val published = MutableStateFlow(Published<O>(0L, null, null))
    private val cache = MutableStateFlow<Map<I, O>>(emptyMap())
    private val passing = MutableStateFlow<Pair<I, O>?>(null)
    private var running: Job? = null
    private var upstream: Node<*, *>? = null
    private val delivered = MutableStateFlow(0L)
    private val gathering = MutableStateFlow<Gathering?>(null)

    /** The nodes a gathered node reads, and the generations of theirs its latest pull has seen. */
    private class Gathering(val sources: Map<*, Node<*, *>>) {
        val generations: List<Long> get() = sources.values.map { it.published.value.generation }
        val seen = MutableStateFlow<List<Long>?>(null)
    }

    val output: StateFlow<O?> = DerivedStateFlow({ published.value.value }, published)

    /** What the latest run threw, or null if it succeeded. A following node also holds what the node it follows threw. */
    val failure: StateFlow<Throwable?> = DerivedStateFlow({ published.value.failure }, published)

    /** [isClean] as last read, with the [epoch] it was read at: the epoch shifted left by one, the lowest bit set when clean. */
    @Volatile
    private var cleanRead = NO_EPOCH

    /**
     * Clean only once the node it follows, if any, is clean and its latest result has reached this one. Read once for each [epoch],
     * which a change in any graph moves on.
     */
    internal val isClean: Boolean
        get() {
            val now = epoch()
            val read = cleanRead
            if (now != NO_EPOCH && read != NO_EPOCH && read shr 1 == now) return read and 1L == 1L
            val clean = readClean()
            if (now != NO_EPOCH) cleanRead = (now shl 1) or (if (clean) 1L else 0L)
            return clean
        }

    /** [isClean] read afresh for this node, its sources read as last read in this epoch. */
    internal fun readClean(): Boolean {
        val followed = upstream
        val caughtUp = followed == null || (followed.isClean && delivered.value == followed.published.value.generation)
        val gathered = gathering.value
        val gatheredAll = gathered == null || (gathered.sources.values.all { it.isClean } && gathered.seen.value == gathered.generations)
        return caughtUp && gatheredAll && completed.value >= latest.value
    }

    /** Collects unconfined, so a new input marks the node dirty inside the call that set it. */
    internal fun start(input: Flow<I>) {
        scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { input.collect(::accept) }
    }

    /** Takes [source]'s results as input. Its failure passes through, and this node keeps its own last result. */
    internal fun follow(source: Node<*, I & Any>) {
        upstream = source
        scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            source.published.collect { result ->
                val failure = result.failure
                val value = result.value
                when {
                    failure != null -> reject(failure)
                    value != null -> accept(value)
                }
                delivered.value = result.generation
                changed()
            }
        }
    }

    /**
     * Takes as input what [inputOf] makes of each value of [shape] and the results of the nodes [sourcesOf] names for it. It
     * runs once all of them have a result, and again when any has a new one; what it makes while any of them is still dirty or
     * running is not kept in its cache. The first failure among them becomes this node's, keeping its last result. Results reach one
     * loop in turn, whichever thread each source finishes on.
     */
    internal fun <S, K, V : Any> gather(shape: Flow<S>, sourcesOf: (S) -> Map<K, Node<*, V>>, inputOf: (S, Map<K, V>) -> I) {
        val wake = Channel<Unit>(Channel.CONFLATED)
        val current = MutableStateFlow<Pair<S, Map<K, Node<*, V>>>?>(null)
        var lastInput: I? = null
        var lastFailure: Throwable? = null
        fun pull() {
            val (value, sources) = current.value ?: return
            val gathered = gathering.value ?: return
            val results = sources.mapValues { it.value.published.value }
            val failure = results.values.firstNotNullOfOrNull { it.failure }
            val values = results.mapValues { it.value.value }
            when {
                failure != null -> if (failure !== lastFailure) {
                    lastFailure = failure
                    lastInput = null
                    reject(failure)
                }
                values.values.all { it != null } -> {
                    @Suppress("UNCHECKED_CAST")
                    val input = inputOf(value, values as Map<K, V>)
                    if (input != lastInput) accept(input, settled = sources.values.all { it.readClean() })
                    lastInput = input
                    lastFailure = null
                }
            }
            gathered.seen.value = results.values.map { it.generation }
            changed()
        }
        scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { for (signal in wake) pull() }
        scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            var watchers: Job? = null
            shape.collect { value ->
                watchers?.cancel()
                val sources = sourcesOf(value)
                gathering.value = Gathering(sources)
                current.value = value to sources
                changed()
                watchers = launch(start = CoroutineStart.UNDISPATCHED) {
                    sources.values.forEach { source ->
                        launch(start = CoroutineStart.UNDISPATCHED) { source.published.collect { wake.trySend(Unit) } }
                    }
                }
                wake.trySend(Unit)
            }
        }
    }

    private fun reject(failure: Throwable) {
        running?.cancel()
        finish(latest.updateAndGet { it + 1 }, null, failure)
    }

    /** Takes [value] as input; a result made from it is kept in the cache only when it is [settled], made from sources none of which was dirty. */
    private fun accept(value: I, settled: Boolean = true) {
        val generation = latest.updateAndGet { it + 1 }
        changed()
        running?.cancel()
        val cached = cache.value[value] ?: passing.value?.takeIf { it.first == value }?.second
        if (cached != null) {
            remember(value, cached)
            finish(generation, cached, null)
            return
        }
        val keep = settled && keeps()
        running = scope.launch(dispatcher) {
            try {
                val result = compute(value)
                if (keep) remember(value, result) else if (settled) passing.value = value to result
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
            next.remove(input)
            next[input] = result
            if (next.size > cacheSize) next.remove(next.keys.first())
            next
        }
    }

    /**
     * Generations only move forward, so a stale run that finishes late never replaces a newer result. The run counts as done before its
     * result is published, so a node the result wakes reads this one as clean.
     */
    private fun finish(generation: Long, value: O?, failure: Throwable?) {
        completed.update { maxOf(it, generation) }
        published.update { old ->
            if (old.generation > generation) old else Published(generation, value ?: old.value, failure)
        }
        changed()
    }
}

/** The epoch of a node made with no graph to read it from, whose cleanness is read afresh each time. */
private const val NO_EPOCH = -1L
