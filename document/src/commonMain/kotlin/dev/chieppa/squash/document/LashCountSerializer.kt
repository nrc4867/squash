package dev.chieppa.squash.document

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** The most lash flicks an eye draws. */
const val MOST_LASHES = 3

/** An eye's lash count, written as a number; a file holding a flag reads true as one flick and false as none. */
object LashCountSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("dev.chieppa.squash.document.LashCount", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val held = decoder.decodeJsonElement().jsonPrimitive
        val count = held.booleanOrNull?.let { if (it) 1 else 0 } ?: held.int
        return count.coerceIn(0, MOST_LASHES)
    }
}
