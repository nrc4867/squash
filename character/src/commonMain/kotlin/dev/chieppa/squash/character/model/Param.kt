package dev.chieppa.squash.character.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlin.jvm.JvmInline

private const val PARAM_PREFIX = "$"

/** The JSON Schema pattern of a parameter reference. */
const val PARAM_PATTERN = "^\\$[a-z][a-zA-Z0-9]*$"

private val paramRegex = Regex(PARAM_PATTERN)

/** A number in a template file, or a `"$name"` reference to one of the template's parameters. */
@Serializable(with = ParamSerializer::class)
@JvmInline
value class Param(val raw: String) {
    val refName: String? get() = if (raw.startsWith(PARAM_PREFIX)) raw.removePrefix(PARAM_PREFIX) else null

    fun resolve(values: Map<String, Float>): Float {
        val name = refName ?: return raw.toFloat()
        return requireNotNull(values[name]) { "No parameter $name" }
    }

    companion object {
        fun of(number: Float) = Param(number.toString())
    }
}

object ParamSerializer : KSerializer<Param> {
    const val SERIAL_NAME = "dev.chieppa.squash.character.model.Param"

    override val descriptor = PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Param {
        val primitive = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Expected a number or a \$parameter")
        if (!primitive.isString) {
            primitive.floatOrNull ?: throw SerializationException("Expected a number or a \$parameter, got $primitive")
            return Param(primitive.content)
        }
        if (!paramRegex.matches(primitive.content)) {
            throw SerializationException("'${primitive.content}' is not a \$parameter reference")
        }
        return Param(primitive.content)
    }

    override fun serialize(encoder: Encoder, value: Param) {
        val primitive = if (value.refName != null) JsonPrimitive(value.raw) else JsonPrimitive(value.raw.toFloat())
        (encoder as JsonEncoder).encodeJsonElement(primitive)
    }
}

/** A `"$name"` reference to a boolean parameter, as a block's `if` holds. */
@Serializable(with = ParamRefSerializer::class)
@JvmInline
value class ParamRef(val raw: String) {
    val name: String get() = raw.removePrefix(PARAM_PREFIX)
}

object ParamRefSerializer : KSerializer<ParamRef> {
    const val SERIAL_NAME = "dev.chieppa.squash.character.model.ParamRef"

    override val descriptor = PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ParamRef {
        val text = decoder.decodeString()
        if (!paramRegex.matches(text)) throw SerializationException("'$text' is not a \$parameter reference")
        return ParamRef(text)
    }

    override fun serialize(encoder: Encoder, value: ParamRef) = encoder.encodeString(value.raw)
}
