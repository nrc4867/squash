package dev.chieppa.squash.character.model

import dev.chieppa.squash.geom.Vec3
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val SPAN_SERIAL_NAME = "dev.chieppa.squash.character.model.Span"
const val LIMITS_SERIAL_NAME = "dev.chieppa.squash.character.model.Limits"
const val DIRECTION_SERIAL_NAME = "dev.chieppa.squash.character.model.Direction"

/** Reads a type written as a JSON array of exactly [size] numbers. */
@OptIn(ExperimentalSerializationApi::class)
abstract class FloatTupleSerializer<T>(
    serialName: String,
    private val size: Int,
    private val fromList: (List<Float>) -> T,
    private val toList: (T) -> List<Float>,
) : KSerializer<T> {
    private val list = ListSerializer(Float.serializer())
    override val descriptor: SerialDescriptor = SerialDescriptor(serialName, list.descriptor)

    override fun deserialize(decoder: Decoder): T {
        val values = list.deserialize(decoder)
        if (values.size != size) throw SerializationException("Expected $size numbers, got ${values.size}")
        return fromList(values)
    }

    override fun serialize(encoder: Encoder, value: T) = list.serialize(encoder, toList(value))
}

/** A `[start, end]` range of u or v. */
@Serializable(with = SpanSerializer::class)
data class Span(val start: Float, val end: Float)

object SpanSerializer : FloatTupleSerializer<Span>(SPAN_SERIAL_NAME, 2, { Span(it[0], it[1]) }, { listOf(it.start, it.end) })

/** A joint's `[min, max]` rotation in degrees. */
@Serializable(with = LimitsSerializer::class)
data class Limits(val min: Float, val max: Float)

object LimitsSerializer : FloatTupleSerializer<Limits>(LIMITS_SERIAL_NAME, 2, { Limits(it[0], it[1]) }, { listOf(it.min, it.max) })

/** A `[x, y, z]` direction in body space. */
@Serializable(with = DirectionSerializer::class)
data class Direction(val x: Float, val y: Float, val z: Float) {
    fun toVec3() = Vec3(x, y, z)
}

object DirectionSerializer : FloatTupleSerializer<Direction>(
    DIRECTION_SERIAL_NAME,
    3,
    { Direction(it[0], it[1], it[2]) },
    { listOf(it.x, it.y, it.z) },
)
