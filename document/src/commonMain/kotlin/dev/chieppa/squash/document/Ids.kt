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
