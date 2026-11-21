package dev.chieppa.squash.screens.look.recipe.server

import dev.chieppa.squash.screens.look.recipe.FileChanges
import dev.chieppa.squash.screens.look.recipe.RecipeArgs
import dev.chieppa.squash.screens.look.recipe.ThreadOutput
import dev.chieppa.squash.screens.look.recipe.engine.EngineLease
import dev.chieppa.squash.screens.look.recipe.engine.EngineSnapshot
import dev.chieppa.squash.screens.look.recipe.engine.POINTER_PROPERTY
import dev.chieppa.squash.screens.smoke.FrameSnapshots
import java.io.File
import java.net.BindException
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

/** What the server exits with when its port is taken. */
private const val PORT_TAKEN = 1

/**
 * Serves recipe builds to clients on this machine (see [RecipeServer]) from one JVM and one GPU device, until stopped, or until the
 * checkout's engine pointer names another engine than this one: then it finishes every build it took and exits with [ENGINE_CHANGED], for
 * `tools/recipe.sh serve` to start it again on the new engine.
 */
fun serve(options: RecipeArgs): Int {
    ThreadOutput.install()
    val settings = ServerOptions(options.entries)
    val workers = Executors.newCachedThreadPool { Thread(it, "recipe-build").apply { isDaemon = true } }
    val bookkeeping = Executors.newSingleThreadExecutor { Thread(it, "recipe-queue").apply { isDaemon = true } }
    val queue = BuildQueue(CoroutineScope(SupervisorJob() + bookkeeping.asCoroutineDispatcher()), workers.asCoroutineDispatcher(), settings.limit)
    val engine = EngineInfo(EngineLease.version, commitOf(options.root))
    val server = try {
        RecipeServer(settings, engine, queue)
    } catch (_: BindException) {
        System.err.println("recipe server: port ${settings.port} is taken; another server may run there (port=<n> picks another)")
        return PORT_TAKEN
    }
    return FrameSnapshots.keep {
        server.use {
            server.start()
            println("recipe server: http://127.0.0.1:${settings.port}, engine ${engine.version ?: "from Gradle"} at ${engine.commit}, ${settings.limit} builds at a time")
            System.getProperty(POINTER_PROPERTY)?.let { pointer -> watchEngine(Path.of(pointer), engine.version, server) }
            runBlocking { server.drained() }
            println("recipe server: the engine changed; stopping once its builds are done")
            ENGINE_CHANGED
        }
    }
}

/** Drains [server] once [pointer] names another engine than [version], watching the file with no polling. */
private fun watchEngine(pointer: Path, version: String?, server: RecipeServer) {
    val snapshot = EngineSnapshot(pointer.parent, pointer)
    val changes = FileChanges(pointer.toFile())
    thread(isDaemon = true, name = "recipe-engine-watch") {
        changes.use {
            do {
                if (snapshot.current() != version) {
                    server.drain()
                    return@thread
                }
            } while (changes.next())
        }
    }
}

/** The commit [root] is checked out at, for the status. */
private fun commitOf(root: File): String = runCatching {
    val process = ProcessBuilder("git", "-C", root.path, "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
    process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
}.getOrNull()?.takeIf { it.isNotEmpty() && ' ' !in it } ?: "unknown"
