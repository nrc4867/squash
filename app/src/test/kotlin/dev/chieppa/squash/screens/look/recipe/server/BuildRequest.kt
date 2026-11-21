package dev.chieppa.squash.screens.look.recipe.server

import dev.chieppa.squash.screens.look.recipe.RecipeArgs
import java.io.File
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** A request the server cannot take, with what is wrong with it. */
class BadRequest(message: String) : Exception(message)

/** The argument keys that name what a request builds; a request gives exactly one. */
private val COMMANDS = listOf("recipe", "export", "probe", "crop")

/** Argument keys only the server or its launcher set, never a request. */
private val RESERVED = setOf("status", "serve", "watch", "port", "limit")

/** The options that make a build render the most, which waits behind smaller ones. */
private val LARGE = setOf("outfits" to "all", "poses" to "all")

/**
 * A build asked of the server: [args] as the recipe command takes them (`key=value`, spaces and percent signs percent-encoded, as
 * `tools/recipe.sh` writes them), [engine] the engine version the client runs, [stream] whether the log is sent as it comes, and a
 * [timeout] of its own.
 */
class BuildRequest(val args: List<String>, val engine: String?, val stream: Boolean, val timeout: Duration?) {
    val options = RecipeArgs(args.toTypedArray())

    /** The file the request names: the recipe, character file or sheet. */
    val file: File = COMMANDS.firstNotNullOf { key ->
        when (key) {
            "recipe" -> options.recipe
            "export" -> options.export
            "probe" -> options.probe
            else -> options.crop
        }
    }

    /** The named file's name, all the status shows of it. */
    val name: String get() = file.name

    /** 0 for a build that renders nothing, 2 for one that renders every outfit or pose or a reference compare, 1 for the rest. */
    val size: Int = when {
        !options.renders -> 0
        options.reference != null || options.entries.any { it in LARGE } -> 2
        else -> 1
    }

    /** The folder the build writes, or the file it reads when it names none, as one string per place on disk. */
    val key: String = (options.out ?: file).absoluteFile.normalize().path.let { if (File.separatorChar == '\\') it.lowercase(Locale.ROOT) else it }

    companion object {
        /** Reads a request body, `{"args": [...], "engine": "...", "stream": true, "timeout": <minutes>}`, for a build or a [watch]. */
        fun parse(body: String, watch: Boolean = false): BuildRequest {
            val json = try {
                Json.parseToJsonElement(body) as? JsonObject
            } catch (e: IllegalArgumentException) {
                throw BadRequest("the body is not JSON: ${e.message}")
            } ?: throw BadRequest("the body is not a JSON object")
            val args = (json["args"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw BadRequest("args holds strings only") }
                ?: throw BadRequest("give args, a list of key=value strings")
            for (arg in args) {
                if ('=' !in arg || arg.startsWith('=')) throw BadRequest("$arg is not key=value")
                if (arg.any(Char::isWhitespace)) throw BadRequest("$arg holds a space; encode it as %20")
                val key = arg.substringBefore('=')
                if (key in RESERVED) throw BadRequest("$key= is set by the server, not a request")
            }
            val keys = args.map { it.substringBefore('=') }
            val root = args.lastOrNull { it.startsWith("root=") }?.substringAfter('=') ?: throw BadRequest("give root=<checkout>")
            if (!File(root.replace("%20", " ").replace("%25", "%")).isAbsolute) throw BadRequest("root= must be an absolute path")
            val named = COMMANDS.filter { it in keys }
            if (named.size != 1) throw BadRequest("name one of ${COMMANDS.joinToString("=, ")}=")
            if (watch && named.single() != "recipe") throw BadRequest("a watch takes recipe=")
            val engine = (json["engine"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val stream = (json["stream"] as? JsonPrimitive)?.booleanOrNull ?: false
            val timeout = (json["timeout"] as? JsonPrimitive)?.let { it.doubleOrNull?.takeIf { m -> m > 0 } ?: throw BadRequest("timeout is minutes, above 0") }?.minutes
            return BuildRequest(args, engine, stream || watch, timeout)
        }
    }
}
