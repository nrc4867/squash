package dev.chieppa.squash.character.load

import dev.chieppa.squash.character.lint.BodyPlanLint
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.TemplateId
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val EXPERIMENT_PREFIX = "x-"
private val offsetInMessage = Regex("""at offset (\d+)""")
private val offsetPrefix = Regex("""^Unexpected JSON token at offset -?\d+: """)
private val pathInMessage = Regex("""at path: (\$[^\s,]*)""")

/** The file grammar: strict keys, tagged on `kind`, comments and trailing commas allowed. */
@OptIn(ExperimentalSerializationApi::class)
val TemplateJson: Json = Json {
    classDiscriminator = "kind"
    allowComments = true
    allowTrailingComma = true
}

/** A template or plan file that does not load, named by [source], JSON [path] and [line]. */
class TemplateLoadException(val source: String, val path: String, val line: Int, detail: String) :
    Exception("$source:$line at $path: $detail")

/**
 * Parses template and plan files strictly: an unknown key or kind is an error, except keys starting with `x-`, which are
 * reserved for experiments and ignored. See HLD: data-model, Part template file format.
 */
object TemplateLoader {
    /** Parses [text] and lints it, throwing the first problem. */
    fun loadPart(source: String, text: String): PartTemplate {
        val template = decode(source, text, PartTemplate.serializer())
        failOnFindings(source, text, TemplateLint.lint(template))
        return template
    }

    /** Parses [text] and lints it against the [templates] that ship with it, throwing the first problem. */
    fun loadPlan(source: String, text: String, templates: Set<TemplateId>): BodyPlan {
        val plan = decode(source, text, BodyPlan.serializer())
        failOnFindings(source, text, BodyPlanLint.lint(plan, templates))
        return plan
    }

    /** The file as a tree with experiment keys removed. */
    fun parseTree(source: String, text: String): JsonElement {
        try {
            return TemplateJson.parseToJsonElement(text).withoutExperiments()
        } catch (e: SerializationException) {
            val offset = offsetInMessage.find(e.message.orEmpty())?.groupValues?.get(1)?.toInt() ?: 0
            throw TemplateLoadException(source, "$", JsonLines(text).lineAt(offset), summary(e))
        }
    }

    /** Decodes the tree as text, because only the text decoder reports the full path of a failure. */
    private fun <T> decode(source: String, text: String, strategy: DeserializationStrategy<T>): T {
        val canonical = TemplateJson.encodeToString(JsonElement.serializer(), parseTree(source, text))
        try {
            return TemplateJson.decodeFromString(strategy, canonical)
        } catch (e: IllegalArgumentException) {
            val path = pathInMessage.find(e.message.orEmpty())?.groupValues?.get(1) ?: "$"
            throw TemplateLoadException(source, path, JsonLines(text).lineOf(path), summary(e))
        }
    }

    private fun failOnFindings(source: String, text: String, findings: List<Finding>) {
        val first = findings.firstOrNull() ?: return
        throw TemplateLoadException(source, first.path, JsonLines(text).lineOf(first.path), first.message)
    }

    private fun summary(e: Exception): String =
        e.message.orEmpty().lineSequence().first().replace(offsetPrefix, "")

    private fun JsonElement.withoutExperiments(): JsonElement = when (this) {
        is JsonObject -> JsonObject(filterKeys { !it.startsWith(EXPERIMENT_PREFIX) }.mapValues { it.value.withoutExperiments() })
        is JsonArray -> JsonArray(map { it.withoutExperiments() })
        else -> this
    }
}
