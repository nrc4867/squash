package dev.chieppa.squash.screens

import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Where the app's background work runs: the [scope] it lives in, the dispatcher the graph derives data and renders art on, the one
 * files are read and written on, where a character's fields are read, and the low-priority pool that builds and renders wardrobe
 * thumbnails behind everything else. A test gives its own, so that work runs only when the test runs it.
 */
open class Workers(
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val compute: CoroutineDispatcher = Dispatchers.Default,
    val files: CoroutineDispatcher = Dispatchers.IO,
    val fieldReads: FieldReads = CpuFieldReads,
    val thumbnails: CoroutineDispatcher = Background.dispatcher,
)

/**
 * Threads at the lowest priority, half the processors, shared by every [Workers], so background renders take only time the app leaves
 * free and a build's parallel steps still spread across them.
 */
private object Background {
    val dispatcher: CoroutineDispatcher by lazy {
        val count = AtomicInteger()
        Executors.newFixedThreadPool((Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)) { work ->
            Thread(work, "squash-thumbnails-${count.incrementAndGet()}").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }.asCoroutineDispatcher()
    }
}
