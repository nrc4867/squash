package dev.chieppa.squash.schemagen

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.DIRECTION_SERIAL_NAME
import dev.chieppa.squash.character.model.LIMITS_SERIAL_NAME
import dev.chieppa.squash.character.model.PARAM_PATTERN
import dev.chieppa.squash.character.model.ParamDefaultSerializer
import dev.chieppa.squash.character.model.ParamRefSerializer
import dev.chieppa.squash.character.model.ParamSerializer
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SPAN_SERIAL_NAME
import dev.chieppa.squash.character.model.SectionSpecSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DISCRIMINATOR = "kind"
private const val POINT_SIZE = 2
private const val DIRECTION_SIZE = 3
private const val SPAN_SIZE = 2

/** The schema files under schema/, generated from the Kotlin model. */
object Schemas {
    private val parameterReference = buildJsonObject {
        put("type", "string")
        put("pattern", PARAM_PATTERN)
    }

    private fun anyOfTypes(vararg schemas: JsonObject) = buildJsonObject { put("oneOf", JsonArray(schemas.toList())) }

    private fun numbers(size: Int, min: Double? = null, max: Double? = null) = buildJsonObject {
        put("type", "array")
        put("minItems", size)
        put("maxItems", size)
        put(
            "items",
            buildJsonObject {
                put("type", "number")
                min?.let { put("minimum", it) }
                max?.let { put("maximum", it) }
            },
        )
    }

    /** The types with their own serializers, which a descriptor cannot describe. */
    private val fragments: Map<String, JsonObject> = mapOf(
        ParamSerializer.SERIAL_NAME to anyOfTypes(buildJsonObject { put("type", "number") }, parameterReference),
        ParamRefSerializer.SERIAL_NAME to parameterReference,
        ParamDefaultSerializer.SERIAL_NAME to anyOfTypes(
            buildJsonObject { put("type", "number") },
            buildJsonObject { put("type", "boolean") },
        ),
        SPAN_SERIAL_NAME to numbers(SPAN_SIZE, min = 0.0, max = 1.0),
        LIMITS_SERIAL_NAME to numbers(SPAN_SIZE),
        DIRECTION_SERIAL_NAME to numbers(DIRECTION_SIZE),
        SectionSpecSerializer.SERIAL_NAME to anyOfTypes(
            buildJsonObject { put("enum", JsonArray(SectionSpecSerializer.profileNames.map(::JsonPrimitive))) },
            buildJsonObject {
                put("type", "array")
                put("items", numbers(POINT_SIZE))
            },
        ),
    )

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private val files: Map<String, SerialDescriptor> = mapOf(
        "part-template.schema.json" to PartTemplate.serializer().descriptor,
        "body-plan.schema.json" to BodyPlan.serializer().descriptor,
    )

    /** Every schema file's name and text. */
    fun render(): Map<String, String> = files.mapValues { (_, descriptor) ->
        val schema: JsonElement = SchemaWalker(DISCRIMINATOR, fragments).schema(descriptor)
        json.encodeToString(JsonElement.serializer(), schema) + "\n"
    }
}
