package dev.chieppa.squash.screens.look.recipe.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The port the server listens on and the client sends to unless told otherwise; `tools/recipe.sh` and the kit's client share it. */
const val DEFAULT_PORT = 47830

/** How many builds run at once: two keep the CPU busy while the other renders on the shared device. */
private const val DEFAULT_LIMIT = 2

/** How long a build may run before its place is given up, unless the server or the request says otherwise. */
private val DEFAULT_TIMEOUT = 20.minutes

/** What the process exits with when the engine changed under it and it stopped to be started again on the new one. */
const val ENGINE_CHANGED = 3

/** The server's own options, from `port=`, `limit=` and `timeout=<minutes>` after `serve=true`. */
class ServerOptions(entries: List<Pair<String, String>>) {
    private val values = entries.toMap()

    val port: Int = values["port"]?.toIntOrNull() ?: DEFAULT_PORT
    val limit: Int = values["limit"]?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_LIMIT
    val timeout: Duration = values["timeout"]?.toDoubleOrNull()?.takeIf { it > 0 }?.minutes ?: DEFAULT_TIMEOUT
}
