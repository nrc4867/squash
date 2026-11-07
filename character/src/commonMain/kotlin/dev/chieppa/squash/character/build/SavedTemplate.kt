package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Socket
import dev.chieppa.squash.character.load.TemplateJson
import dev.chieppa.squash.character.model.ParamSpec
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.TEMPLATE_FORMAT
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.TemplateId
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

private const val ID_SEPARATOR = "-"
private const val SAVED_VERSION = 1
private val NOT_IN_ID = Regex("[^a-z0-9]+")

/** How a saved template's file is written: the template grammar, indented to read. */
@OptIn(ExperimentalSerializationApi::class)
private val SavedJson = Json(TemplateJson) {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** The id a template saved as [name] takes: its words in lower case joined by dashes, or null when it has none. */
fun savedTemplateId(name: String): TemplateId? =
    name.lowercase().replace(NOT_IN_ID, ID_SEPARATOR).trim(ID_SEPARATOR[0]).takeIf { it.isNotEmpty() }?.let(::TemplateId)

/**
 * A part as a template of its own, called [name] with [id], version 1, taking [role]: [unplaced], the template the part pins, as the
 * part with [params] builds it on a socket ([asBuilt]), with its parameters' values as the defaults, a generic part's section,
 * views, rings and segments fixed, and the joints, sockets and landmarks placed on it by hand as blocks named by their numbers. It
 * carries no drawing, as no template does. See HLD: data-model, Save as template.
 */
fun savedTemplate(unplaced: PartTemplate, params: Map<String, Float>, role: Role?, id: TemplateId, name: String): PartTemplate {
    val built = unplaced.placedBy(params).shapedBy(params).withHandAnatomy(params, ::fixedName)
    val values = unplaced.defaults + params
    val blocks = built.blocks + HandSockets.specs(params).map { socket ->
        Socket(fixedName(socket.name), u = socket.u, v = socket.v, pair = socket.pair, tilt = socket.tilt)
    }
    return built.copy(
        schemaPath = null,
        format = TEMPLATE_FORMAT,
        id = id,
        version = SAVED_VERSION,
        name = name,
        role = role,
        axis = built.axis.copy(up = null),
        generic = null,
        params = unplaced.params.mapValues { (key, spec) -> values[key]?.let { spec.holding(it) } ?: spec },
        blocks = blocks.sortedBy { block -> if (block.joints().isEmpty()) 0 else 1 },
    )
}

/** The file text of [template]. */
fun savedTemplateText(template: PartTemplate): String = SavedJson.encodeToString(PartTemplate.serializer(), template) + "\n"

/** A hand-made joint, socket or landmark's name as a template names its own, `joint.1` as `joint1`. */
private fun fixedName(name: String) = name.replace(".", "")

/** This parameter with [value] as its default, its limits widened to hold it; one that takes only listed values keeps its default. */
private fun ParamSpec.holding(value: Float): ParamSpec =
    if (values != null) this else copy(default = default.copy(value = value), min = min?.let { minOf(it, value) }, max = max?.let { maxOf(it, value) })
