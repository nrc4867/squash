package dev.chieppa.squash.screens

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Where the app's background work runs: the [scope] it lives in, the dispatcher the graph derives data and renders art on, and
 * the one files are read and written on. A test gives its own, so that work runs only when the test runs it.
 */
open class Workers(
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val compute: CoroutineDispatcher = Dispatchers.Default,
    val files: CoroutineDispatcher = Dispatchers.IO,
)
