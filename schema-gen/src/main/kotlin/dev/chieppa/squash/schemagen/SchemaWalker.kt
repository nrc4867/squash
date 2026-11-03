package dev.chieppa.squash.schemagen

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DRAFT = "https://json-schema.org/draft/2020-12/schema"
private const val DEFS = "\$defs"
private const val REF = "\$ref"
private const val EXPERIMENT_KEYS = "^x-"
private const val SEALED_VALUE_ELEMENT = 1

/**
 * Writes a JSON Schema by walking a serial descriptor, the structure the parser reads, so the schema accepts what the
 * parser accepts. Sealed subclasses become `oneOf` on [discriminator]; nullability and defaults give `required`;
 * `@Range` and `@Doc` give `minimum`, `maximum` and `description`. See HLD: data-model, One source of truth.
 */
@OptIn(ExperimentalSerializationApi::class)
class SchemaWalker(private val discriminator: String, private val fragments: Map<String, JsonObject>) {
    private val defs = sortedMapOf<String, JsonObject>()

    fun schema(root: SerialDescriptor): JsonObject {
        val ref = walk(root)
        return buildJsonObject {
            put("\$schema", DRAFT)
            ref.forEach { (key, value) -> put(key, value) }
            put(DEFS, JsonObject(defs))
        }
    }

    private fun walk(descriptor: SerialDescriptor): JsonObject {
        val plain = walkPlain(descriptor)
        if (!descriptor.isNullable) return plain
        return buildJsonObject {
            put("anyOf", buildJsonArray { add(plain); add(buildJsonObject { put("type", "null") }) })
        }
    }

    private fun walkPlain(descriptor: SerialDescriptor): JsonObject {
        fragments[plainName(descriptor)]?.let { return it }
        if (descriptor.isInline) return walk(descriptor.getElementDescriptor(0))
        return when (val kind = descriptor.kind) {
            is PrimitiveKind -> primitive(kind)
            SerialKind.ENUM -> buildJsonObject {
                put("enum", JsonArray((0 until descriptor.elementsCount).map { JsonPrimitive(descriptor.getElementName(it)) }))
            }
            StructureKind.LIST -> buildJsonObject {
                put("type", "array")
                put("items", walk(descriptor.getElementDescriptor(0)))
            }
            StructureKind.MAP -> buildJsonObject {
                put("type", "object")
                put("propertyNames", walk(descriptor.getElementDescriptor(0)))
                put("additionalProperties", walk(descriptor.getElementDescriptor(1)))
            }
            StructureKind.CLASS, StructureKind.OBJECT -> define(descriptor) { classSchema(descriptor, null) }
            PolymorphicKind.SEALED -> define(descriptor) { sealedSchema(descriptor) }
            else -> error("No schema for ${descriptor.serialName} of kind $kind")
        }
    }

    private fun primitive(kind: PrimitiveKind): JsonObject = buildJsonObject {
        when (kind) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> put("type", "string")
            PrimitiveKind.BOOLEAN -> put("type", "boolean")
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> put("type", "number")
            else -> put("type", "integer")
        }
    }

    private fun define(descriptor: SerialDescriptor, build: () -> JsonObject): JsonObject {
        val name = shortName(descriptor)
        if (name !in defs) {
            defs[name] = JsonObject(emptyMap())
            defs[name] = build()
        }
        return buildJsonObject { put(REF, "#/$DEFS/$name") }
    }

    private fun sealedSchema(descriptor: SerialDescriptor): JsonObject {
        val subclasses = descriptor.getElementDescriptor(SEALED_VALUE_ELEMENT).elementDescriptors.toList()
        return buildJsonObject {
            put("oneOf", JsonArray(subclasses.map { sub -> define(sub) { classSchema(sub, sub.serialName) } }))
        }
    }

    private fun classSchema(descriptor: SerialDescriptor, kindTag: String?): JsonObject {
        val names = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        val required = names.filterIndexed { i, _ -> !descriptor.isElementOptional(i) }
        return buildJsonObject {
            docOf(descriptor.annotations)?.let { put("description", it) }
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    if (kindTag != null) put(discriminator, buildJsonObject { put("const", kindTag) })
                    names.forEachIndexed { i, name -> put(name, property(descriptor, i)) }
                },
            )
            val requiredKeys = listOfNotNull(discriminator.takeIf { kindTag != null }) + required
            if (requiredKeys.isNotEmpty()) put("required", JsonArray(requiredKeys.map(::JsonPrimitive)))
            put("patternProperties", buildJsonObject { put(EXPERIMENT_KEYS, JsonObject(emptyMap())) })
            put("additionalProperties", false)
        }
    }

    private fun property(descriptor: SerialDescriptor, index: Int): JsonObject {
        val annotations = descriptor.getElementAnnotations(index)
        val range = annotations.filterIsInstance<Range>().firstOrNull()
        val base = walk(descriptor.getElementDescriptor(index)).let { if (range != null) bounded(it, range) else it }
        val doc = docOf(annotations) ?: return base
        return JsonObject(mapOf("description" to JsonPrimitive(doc)) + base)
    }

    /** Puts [range] on every number or integer branch of [schema]. */
    private fun bounded(schema: JsonObject, range: Range): JsonObject {
        val type = (schema["type"] as? JsonPrimitive)?.content
        if (type == "number" || type == "integer") {
            return buildJsonObject {
                schema.forEach { (key, value) -> put(key, value) }
                if (!range.min.isNaN()) put("minimum", range.min)
                if (!range.max.isNaN()) put("maximum", range.max)
            }
        }
        return JsonObject(
            schema.mapValues { (key, value): Map.Entry<String, JsonElement> ->
                if ((key == "oneOf" || key == "anyOf") && value is JsonArray) {
                    JsonArray(value.map { bounded(it as JsonObject, range) })
                } else {
                    value
                }
            },
        )
    }

    private fun docOf(annotations: List<Annotation>): String? = annotations.filterIsInstance<Doc>().firstOrNull()?.text

    private fun plainName(descriptor: SerialDescriptor) = descriptor.serialName.removeSuffix("?")

    private fun shortName(descriptor: SerialDescriptor) = plainName(descriptor).substringAfterLast('.').substringAfter(' ')
        .let { name -> if (name.startsWith("kotlinx")) name else name.replaceFirstChar { it.uppercase() } }
}
