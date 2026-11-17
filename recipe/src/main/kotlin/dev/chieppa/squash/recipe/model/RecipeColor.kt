package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.document.SlotColor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlin.math.roundToInt

private const val CHANNEL_MAX = 255
private const val HEX_DIGITS = 6
private const val HEX_RADIX = 16

/** An sRGB colour, each channel 0 to 1, written as `"#rrggbb"` or `[red, green, blue]`. */
@Serializable(with = RecipeColorSerializer::class)
data class RecipeColor(val red: Float, val green: Float, val blue: Float) {
    val slotColor: SlotColor get() = SlotColor(red, green, blue)

    /** As `#rrggbb` when each channel is a whole byte over 255, else null. */
    val hex: String? get() {
        val bytes = listOf(red, green, blue).map { (it * CHANNEL_MAX).roundToInt() }
        if (bytes.zip(listOf(red, green, blue)).any { (b, c) -> b.toFloat() / CHANNEL_MAX != c }) return null
        return "#" + bytes.joinToString("") { it.toString(HEX_RADIX).padStart(2, '0') }
    }

    companion object {
        const val SERIAL_NAME = "dev.chieppa.squash.recipe.RecipeColor"

        fun of(color: SlotColor) = RecipeColor(color.red, color.green, color.blue)

        /** The colour [text] names as `#rrggbb`. */
        fun parse(text: String): RecipeColor {
            val digits = text.removePrefix("#")
            if (!text.startsWith("#") || digits.length != HEX_DIGITS || digits.any { it.digitToIntOrNull(HEX_RADIX) == null }) {
                throw SerializationException("A colour is \"#rrggbb\" or [red, green, blue] from 0 to 1, not \"$text\"")
            }
            val channels = digits.chunked(2).map { it.toInt(HEX_RADIX).toFloat() / CHANNEL_MAX }
            return RecipeColor(channels[0], channels[1], channels[2])
        }
    }
}

/** Reads a colour as `"#rrggbb"` or three numbers from 0 to 1; writes the hex form when it is exact. */
object RecipeColorSerializer : KSerializer<RecipeColor> {
    override val descriptor = PrimitiveSerialDescriptor(RecipeColor.SERIAL_NAME, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): RecipeColor {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element is JsonPrimitive && element.isString) return RecipeColor.parse(element.content)
        val channels = (element as? JsonArray)?.map { (it as? JsonPrimitive)?.floatOrNull }
        if (channels == null || channels.size != 3 || channels.any { it == null || it !in 0f..1f }) {
            throw SerializationException("A colour is \"#rrggbb\" or [red, green, blue] from 0 to 1, not $element")
        }
        return RecipeColor(channels[0]!!, channels[1]!!, channels[2]!!)
    }

    override fun serialize(encoder: Encoder, value: RecipeColor) {
        val json = encoder as JsonEncoder
        val hex = value.hex
        json.encodeJsonElement(if (hex != null) JsonPrimitive(hex) else JsonArray(listOf(value.red, value.green, value.blue).map(::JsonPrimitive)))
    }
}
