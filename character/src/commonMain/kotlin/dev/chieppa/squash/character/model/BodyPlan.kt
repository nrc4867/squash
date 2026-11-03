package dev.chieppa.squash.character.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.TemplateId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlanRoot(val template: TemplateId, val rest: Direction)

@Serializable
data class PlanSocket(
    val name: String,
    val on: TemplateId,
    @Range(min = 0.0, max = 1.0)
    val u: Float,
    @Range(min = 0.0, max = 1.0)
    val v: Float,
    @Doc("Gives this socket a mirrored twin.")
    val pair: Mirror? = null,
    val takes: List<TemplateId>,
    val optional: Boolean = false,
)

@Serializable
data class PlanSlot(
    val name: String,
    val on: TemplateId,
    val takes: List<TemplateId>,
    val default: TemplateId? = null,
)

/** A graph of sockets from a root spine, in a shipped, versioned file. */
@Serializable
@Doc("A body plan: a root part and the sockets and slots hung from it.")
data class BodyPlan(
    @SerialName("\$schema")
    val schemaPath: String? = null,
    @Doc("The grammar version of the file.") @Range(min = 1.0, max = 1.0)
    val format: Int,
    val id: BodyPlanId,
    @Range(min = 1.0)
    val version: Int,
    val name: String,
    val root: PlanRoot,
    val sockets: List<PlanSocket>,
    val slots: List<PlanSlot> = emptyList(),
) {
    val ref: BodyPlanRef get() = BodyPlanRef(id, version)
}
