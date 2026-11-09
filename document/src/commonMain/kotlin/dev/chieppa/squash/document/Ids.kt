package dev.chieppa.squash.document

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class CharacterId(val value: String)

@Serializable
@JvmInline
value class PartId(val value: String)

@Serializable
@JvmInline
value class PairId(val value: String)

@Serializable
@JvmInline
value class TemplateId(val value: String)

@Serializable
@JvmInline
value class BodyPlanId(val value: String)

/** A part template pinned to one version. */
@Serializable
data class TemplateRef(val id: TemplateId, val version: Int)

/** A body plan pinned to one version. */
@Serializable
data class BodyPlanRef(val id: BodyPlanId, val version: Int)

/** A named socket on a parent part. */
@Serializable
data class SocketRef(val parent: PartId, val socket: String)

@Serializable
@JvmInline
value class MarkId(val value: String)

@Serializable
@JvmInline
value class MarkGroupId(val value: String)

@Serializable
@JvmInline
value class RegionId(val value: String)

@Serializable
@JvmInline
value class BrushStrokeId(val value: String)

@Serializable
@JvmInline
value class LineWeightId(val value: String)

@Serializable
@JvmInline
value class SeamStrokeId(val value: String)

@Serializable
@JvmInline
value class ExpressionId(val value: String)

/** The name of a palette slot; parts, regions, paint and template color fields hold this, never a color. */
@Serializable
@JvmInline
value class SlotName(val value: String)

/** The name of a range of camera angles a mark has a version for. */
@Serializable
@JvmInline
value class CameraBin(val value: String)

@Serializable
@JvmInline
value class GarmentId(val value: String)

/** The id of a wardrobe slot, a place on the body an outfit fills with one garment; apart from a palette slot's [SlotName]. */
@Serializable
@JvmInline
value class GarmentSlotId(val value: String)

@Serializable
@JvmInline
value class OutfitId(val value: String)

@Serializable
@JvmInline
value class LooseEndId(val value: String)
