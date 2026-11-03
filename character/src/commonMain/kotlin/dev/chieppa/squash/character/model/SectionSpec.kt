package dev.chieppa.squash.character.model

import dev.chieppa.squash.geom.ClosedSpline
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.Vec2
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive

private const val POINT_SIZE = 2

/** A cross-section in a template file: a built-in profile's name, or `[x, y]` control points. */
@Serializable(with = SectionSpecSerializer::class)
sealed interface SectionSpec {
    /** The spline with [segments] control points. */
    fun spline(segments: Int): ClosedSpline

    /** How many control points this spec fixes, or null if it fits any count. */
    val controlPointCount: Int?

    data class Named(val profile: SectionProfile) : SectionSpec {
        override fun spline(segments: Int) = profile.spline(segments)
        override val controlPointCount: Int? get() = null
    }

    data class Points(val points: List<Vec2>) : SectionSpec {
        override fun spline(segments: Int) = ClosedSpline(points)
        override val controlPointCount: Int get() = points.size
    }
}

object SectionSpecSerializer : KSerializer<SectionSpec> {
    const val SERIAL_NAME = "dev.chieppa.squash.character.model.SectionSpec"

    /** The profile names a file may use. */
    val profileNames: List<String> = SectionProfile.entries.map { it.name.lowercase() }

    override val descriptor = PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SectionSpec =
        when (val element = (decoder as JsonDecoder).decodeJsonElement()) {
            is JsonPrimitive -> SectionSpec.Named(
                SectionProfile.entries.firstOrNull { it.name.lowercase() == element.content }
                    ?: throw SerializationException("Unknown section '${element.content}'"),
            )
            is JsonArray -> SectionSpec.Points(
                element.map { point ->
                    val pair = point as? JsonArray ?: throw SerializationException("Expected an [x, y] control point")
                    if (pair.size != POINT_SIZE) throw SerializationException("Expected an [x, y] control point")
                    Vec2(pair[0].jsonPrimitive.float, pair[1].jsonPrimitive.float)
                },
            )
            else -> throw SerializationException("Expected a section name or control points")
        }

    override fun serialize(encoder: Encoder, value: SectionSpec) {
        val element = when (value) {
            is SectionSpec.Named -> JsonPrimitive(value.profile.name.lowercase())
            is SectionSpec.Points -> buildJsonArray {
                value.points.forEach { p -> add(buildJsonArray { add(JsonPrimitive(p.x)); add(JsonPrimitive(p.y)) }) }
            }
        }
        (encoder as JsonEncoder).encodeJsonElement(element)
    }
}
