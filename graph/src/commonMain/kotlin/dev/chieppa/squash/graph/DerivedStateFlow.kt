package dev.chieppa.squash.graph

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A [StateFlow] computed on read, so its value is never behind the state it reads. [changes] emits when that moves. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class DerivedStateFlow<T>(private val read: () -> T, private val changes: Flow<*>) : StateFlow<T> {
    override val value: T get() = read()
    override val replayCache: List<T> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        changes.map { read() }.distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}
