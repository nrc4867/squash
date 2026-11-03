package dev.chieppa.squash.graph

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A check that plain, non-suspending work calls between its steps: it throws the cancellation of the run that asked for
 * it, so a stale run stops there.
 */
suspend fun cancellationCheck(): () -> Unit {
    val context = currentCoroutineContext()
    return { context.ensureActive() }
}
