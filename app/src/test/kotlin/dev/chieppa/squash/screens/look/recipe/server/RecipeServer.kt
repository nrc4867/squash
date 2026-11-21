package dev.chieppa.squash.screens.look.recipe.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chieppa.squash.screens.look.recipe.FileChanges
import dev.chieppa.squash.screens.look.recipe.RecipeArgs
import dev.chieppa.squash.screens.look.recipe.RecipeRun
import dev.chieppa.squash.screens.look.recipe.RecipeWatch
import dev.chieppa.squash.screens.look.recipe.ThreadOutput
import dev.chieppa.squash.screens.look.recipe.reportingRecipeErrors
import dev.chieppa.squash.screens.look.recipe.runCommand
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The exit status of a build that outlived its timeout, as the `timeout` command gives. */
private const val TIMED_OUT = 124

/** The engine a server runs: its [version], the snapshot's name, and the checkout's [commit] it was compiled in. */
class EngineInfo(val version: String?, val commit: String)

/**
 * Serves recipe builds over HTTP on 127.0.0.1 only, on [options]' port, through [queue]:
 * - `POST /build` takes a [BuildRequest] and answers `{"code", "files", "log", "millis"}`, or with `"stream": true` one JSON event a line:
 *   `{"out": line}` and `{"err": line}` as the build prints them, then `{"done": {"code", "files", "millis"}}`;
 * - `POST /watch` takes a recipe and builds it again on each save, streaming as `/build` does, each build's end a `{"built": ...}`, until
 *   the client goes or the server drains, which ends it with `{"done": {"code": 0, "reconnect": true}}`;
 * - `GET /status` answers the engine, the commit, whether it takes builds, and what runs and waits;
 * - `GET /health` answers `{"ok": true, "engine"}`.
 * A request for another engine than [engine]'s is refused with 409, and any request once the server drains with 503, so the client builds
 * in its own process instead. A build runs [build] with its output routed into its own log (see [ThreadOutput]).
 */
class RecipeServer(
    private val options: ServerOptions,
    private val engine: EngineInfo,
    private val queue: BuildQueue,
    private val build: (RecipeArgs) -> Int = ::runCommand,
) : AutoCloseable {
    private val http: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), options.port), 0)
    private val handlers: ExecutorService = Executors.newCachedThreadPool { Thread(it, "recipe-server-request").apply { isDaemon = true } }
    private val exchanges = MutableStateFlow(0)
    private val watches = mutableSetOf<FileChanges>()
    private val ids = AtomicLong()

    private val stopping = MutableStateFlow(false)
    private val draining: Boolean get() = stopping.value

    /** The port it listens on: the one asked for, or for port 0 the one the system gave. */
    val port: Int get() = http.address.port

    /** Starts listening. */
    fun start() {
        http.executor = handlers
        http.createContext("/build") { exchange -> handle(exchange) { build(it, watch = false) } }
        http.createContext("/watch") { exchange -> handle(exchange) { build(it, watch = true) } }
        http.createContext("/status") { exchange -> handle(exchange) { respond(it, 200, status()) } }
        http.createContext("/health") { exchange ->
            handle(exchange) { respond(it, 200, buildJsonObject { put("ok", true); put("engine", engine.version) }) }
        }
        http.start()
    }

    /** Stops taking builds and ends the watches after their current build; the queued builds still run. */
    fun drain() {
        stopping.value = true
        queue.close()
        synchronized(watches) { watches.toList() }.forEach(FileChanges::close)
    }

    /** Waits until [drain] is called and every build and answer is done. */
    suspend fun drained() {
        stopping.first { it }
        queue.drained()
        exchanges.first { it == 0 }
    }

    override fun close() {
        http.stop(0)
        handlers.shutdownNow()
    }

    private fun handle(exchange: HttpExchange, block: (HttpExchange) -> Unit) {
        exchanges.update { it + 1 }
        try {
            block(exchange)
        } catch (e: Exception) {
            System.err.println("recipe server: ${exchange.requestMethod} ${exchange.requestURI.path} failed: $e")
        } finally {
            exchange.close()
            exchanges.update { it - 1 }
        }
    }

    private fun status(): JsonObject {
        val held = queue.status()
        return buildJsonObject {
            put("engine", engine.version)
            put("commit", engine.commit)
            put("accepting", held.accepting && !draining)
            put("limit", held.limit)
            put("waiting", held.waiting)
            put("running", buildJsonArray {
                held.running.forEach { run ->
                    add(buildJsonObject {
                        put("id", run.id)
                        put("name", run.name)
                        put("seconds", run.running.inWholeSeconds)
                    })
                }
            })
        }
    }

    private fun build(exchange: HttpExchange, watch: Boolean) {
        if (exchange.requestMethod != "POST") return respond(exchange, 405, error("POST a build"))
        val request = try {
            BuildRequest.parse(exchange.requestBody.readAllBytes().toString(Charsets.UTF_8), watch)
        } catch (e: BadRequest) {
            return respond(exchange, 400, error(e.message ?: "bad request"))
        }
        if (request.engine != null && request.engine != engine.version) {
            return respond(exchange, 409, buildJsonObject { put("error", "this server runs another engine"); put("engine", engine.version) })
        }
        if (draining) return respond(exchange, 503, error("the server is stopping for a new engine"))
        if (watch) watch(exchange, request) else once(exchange, request)
    }

    private fun once(exchange: HttpExchange, request: BuildRequest) {
        val id = ids.incrementAndGet()
        val events = if (request.stream) Events(exchange) else null
        val log = BuildLog { stream, line -> events?.send(buildJsonObject { put(stream.key, line) }) }
        val started = TimeSource.Monotonic.markNow()
        val ready = CompletableDeferred<Unit>()
        val outcome = queue.submit(queued(request, log, ready) { build(request.options) })
            ?: return respond(exchange, 503, error("the server is stopping for a new engine"))
        events?.open()
        ready.complete(Unit)
        val code = runBlocking { codeOf(outcome.await(), log, request) }
        val millis = started.elapsedNow().inWholeMilliseconds
        println("recipe server: build $id ${request.name} exited $code in $millis ms")
        val done = buildJsonObject {
            put("code", code)
            put("files", files(log.files()))
            put("millis", millis)
        }
        if (events != null) return events.send(buildJsonObject { put("done", done) })
        respond(exchange, 200, buildJsonObject {
            done.forEach { (key, value) -> put(key, value) }
            put("log", buildJsonArray { log.lines().forEach { (stream, line) -> add(buildJsonObject { put(stream.key, line) }) } })
        })
    }

    private fun watch(exchange: HttpExchange, request: BuildRequest) {
        val events = Events(exchange).open()
        val log = BuildLog { stream, line -> events.send(buildJsonObject { put(stream.key, line) }) }
        val code = ThreadOutput.into(log.stream(LogStream.OUT), log.stream(LogStream.ERR)) {
            reportingRecipeErrors {
                RecipeRun(request.options).use { run ->
                    run.readRecipe()
                    FileChanges(run.file).use { changes ->
                        synchronized(watches) { watches += changes }
                        if (draining) changes.close()
                        try {
                            RecipeWatch(run::readRecipe, { text -> watched(request, log, events) { run.run(text) } }, { !draining && log.isOpen && changes.next() }, run.file.name).run()
                        } finally {
                            synchronized(watches) { watches -= changes }
                        }
                    }
                }
                0
            }
        }
        events.send(buildJsonObject { put("done", buildJsonObject { put("code", code); put("reconnect", draining) }) })
    }

    /** One build of a watch through the queue, its end sent as a `built` event; its exit status. */
    private fun watched(request: BuildRequest, log: BuildLog, events: Events, body: () -> Int): Int {
        val from = log.size
        val outcome = queue.submit(queued(request, log, CompletableDeferred(Unit), body)) ?: return 0
        val code = runBlocking { codeOf(outcome.await(), log, request) }
        log.alongside { events.send(buildJsonObject { put("built", buildJsonObject { put("code", code); put("files", files(log.files(from))) }) }) }
        return code
    }

    /** A queued build of [request] that waits for [ready], then runs [body] with its output in [log]. */
    private fun queued(request: BuildRequest, log: BuildLog, ready: Deferred<Unit>, body: () -> Int): QueuedBuild {
        val thread = AtomicReference<Thread?>()
        return QueuedBuild(
            name = request.name,
            size = request.size,
            key = request.key,
            timeout = request.timeout ?: options.timeout,
            abandon = { thread.get()?.interrupt() },
        ) {
            ready.await()
            thread.set(Thread.currentThread())
            ThreadOutput.into(log.stream(LogStream.OUT), log.stream(LogStream.ERR)) { reportingRecipeErrors(body) }
        }
    }

    private fun codeOf(outcome: BuildOutcome, log: BuildLog, request: BuildRequest): Int = when (outcome) {
        is BuildOutcome.Done -> outcome.code
        is BuildOutcome.Failed -> {
            System.err.println("recipe server: ${request.name} failed")
            outcome.error.printStackTrace()
            log.add(LogStream.ERR, "build failed: ${outcome.error}")
            1
        }
        BuildOutcome.TimedOut -> {
            log.add(LogStream.ERR, "build timed out after ${(request.timeout ?: options.timeout).inWholeMinutes} min")
            log.close()
            TIMED_OUT
        }
    }

    private fun error(message: String) = buildJsonObject { put("error", message) }

    private fun files(paths: List<String>) = buildJsonArray { paths.forEach { add(JsonPrimitive(it)) } }

    private fun respond(exchange: HttpExchange, code: Int, body: JsonObject) {
        val bytes = body.toString().toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    /** A streamed answer: one JSON event a line, flushed as it is sent. */
    private class Events(private val exchange: HttpExchange) {
        private val out = PrintStream(exchange.responseBody, false, Charsets.UTF_8)

        fun open(): Events {
            exchange.responseHeaders.add("Content-Type", "application/x-ndjson; charset=utf-8")
            exchange.sendResponseHeaders(200, 0)
            return this
        }

        @Synchronized
        fun send(event: JsonObject) {
            out.print(event.toString())
            out.print("\n")
            out.flush()
            if (out.checkError()) throw java.io.IOException("the client went away")
        }
    }
}
