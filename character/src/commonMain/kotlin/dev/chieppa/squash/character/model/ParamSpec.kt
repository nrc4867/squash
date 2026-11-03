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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull

private const val FLAG_ON = 1f
private const val FLAG_OFF = 0f

/** A parameter's default: a number, or a flag held as 1 or 0. */
@Serializable(with = ParamDefaultSerializer::class)
data class ParamDefault(val value: Float, val isFlag: Boolean = false)

object ParamDefaultSerializer : KSerializer<ParamDefault> {
    const val SERIAL_NAME = "dev.chieppa.squash.character.model.ParamDefault"

    override val descriptor = PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ParamDefault {
        val primitive = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Expected a number or a boolean")
        if (primitive.isString) throw SerializationException("Expected a number or a boolean, got $primitive")
        primitive.booleanOrNull?.let { return ParamDefault(if (it) FLAG_ON else FLAG_OFF, isFlag = true) }
        return ParamDefault(primitive.floatOrNull ?: throw SerializationException("Expected a number, got $primitive"))
    }

    override fun serialize(encoder: Encoder, value: ParamDefault) {
        val primitive = if (value.isFlag) JsonPrimitive(value.value == FLAG_ON) else JsonPrimitive(value.value)
        (encoder as JsonEncoder).encodeJsonElement(primitive)
    }
}

/** A parameter the Part drawer exposes. A new parameter's default reproduces the old build. */
@Serializable
data class ParamSpec(
    val default: ParamDefault,
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val unit: String? = null,
)
