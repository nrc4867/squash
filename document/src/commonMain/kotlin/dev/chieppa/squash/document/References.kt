package dev.chieppa.squash.document

import kotlin.io.encoding.Base64
import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
@JvmInline
value class ImageId(val value: String)

/** An image file's bytes as it was added, in its own encoding. Plain JSON holds them as base64; a package holds them as a file of their own. */
@Serializable(with = ImageBytesSerializer::class)
class ImageBytes(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is ImageBytes && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ImageBytes(${bytes.size})"
}

object ImageBytesSerializer : KSerializer<ImageBytes> {
    override val descriptor = PrimitiveSerialDescriptor("dev.chieppa.squash.ImageBytes", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ImageBytes) = encoder.encodeString(Base64.encode(value.bytes))

    override fun deserialize(decoder: Decoder): ImageBytes = ImageBytes(Base64.decode(decoder.decodeString()))
}

/** Where an image sits on the References board: its top-left corner and its width, in board units, its height following its own shape. */
@Serializable
data class BoardPlace(val x: Float, val y: Float, val width: Float)

/**
 * An image kept for reference: its [name], its file's [data] and [width] by [height] pixels, where it sits on the board, and the
 * palette slots [picked] from it. It never reaches a render.
 */
@Serializable
data class ReferenceImage(
    val id: ImageId,
    val name: String,
    val data: ImageBytes,
    val width: Int,
    val height: Int,
    val board: BoardPlace,
    val picked: List<SlotName> = emptyList(),
) {
    init {
        require(width > 0 && height > 0) { "An image has pixels" }
    }
}

/** The References board: every image kept for reference, in the order they draw, the last on top. */
@Serializable
data class References(val images: List<ReferenceImage> = emptyList()) {
    fun image(id: ImageId): ReferenceImage? = images.firstOrNull { it.id == id }
}

/** Whether a trace image draws behind the character or over it. */
@Serializable
enum class TraceOrder {
    @SerialName("behind") BEHIND,
    @SerialName("over") OVER,
}

/**
 * A reference image placed to draw over in one of the character's views. [fitX] and [fitY] are where its centre first sat, across
 * and up the view in body units, and [fitUnits] the body units one of its pixels first spanned. [scale] multiplies that size, [x]
 * and [y] move it from there in its own pixels at that first size, and [rotation] turns it, in degrees, before [flip] mirrors it
 * across. It shows at [opacity], [order] the character, while [shown]; a [locked] trace does not move.
 */
@Serializable
data class Trace(
    val image: ImageId,
    val fitX: Float,
    val fitY: Float,
    val fitUnits: Float,
    val scale: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Float = 0f,
    val flip: Boolean = false,
    val opacity: Float = DEFAULT_TRACE_OPACITY,
    val order: TraceOrder = TraceOrder.OVER,
    val shown: Boolean = true,
    val locked: Boolean = true,
) {
    init {
        require(fitUnits > 0f && scale > 0f) { "A trace has a size" }
        require(opacity in 0f..1f) { "Opacity $opacity" }
    }

    /** Body units one of the image's pixels spans. */
    val units: Float get() = fitUnits * scale

    /** The image's centre across and up the view, in body units. */
    val centerX: Float get() = fitX + x * fitUnits
    val centerY: Float get() = fitY + y * fitUnits

    companion object {
        const val DEFAULT_TRACE_OPACITY = 0.45f
    }
}
