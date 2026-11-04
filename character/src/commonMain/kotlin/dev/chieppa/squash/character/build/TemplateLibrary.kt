package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef

/** Every loaded template by its pinned id and version, and the body plans that place them. */
class TemplateLibrary(templates: List<PartTemplate>, plans: List<BodyPlan> = emptyList()) {
    private val byRef = templates.associateBy { it.ref }
    private val byId = templates.groupBy { it.id }
    private val plansByRef = plans.associateBy { it.ref }

    operator fun get(ref: TemplateRef): PartTemplate =
        requireNotNull(byRef[ref]) { "No template ${ref.id.value} version ${ref.version}" }

    /** The template pinned by [ref], if it is loaded. */
    fun find(ref: TemplateRef): PartTemplate? = byRef[ref]

    /** The newest loaded template with this id, if any: what an empty socket would take. */
    fun latest(id: TemplateId): PartTemplate? = byId[id]?.maxByOrNull { it.version }

    fun plan(ref: BodyPlanRef): BodyPlan? = plansByRef[ref]
}
