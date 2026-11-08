package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.Vec3
import kotlin.concurrent.Volatile

/**
 * Every loaded template by its pinned id and version, and the body plans that place them. Templates are only ever added, each under
 * a ref no loaded template has, so nothing read from the library before an [add] reads differently after it.
 */
class TemplateLibrary(templates: List<PartTemplate>, plans: List<BodyPlan> = emptyList()) {
    private class Loaded(templates: List<PartTemplate>) {
        val byRef = templates.associateBy { it.ref }
        val byId = templates.groupBy { it.id }
        val sinks = byRef.mapValues { (_, template) -> lazy { template.socketSink() } }
        val mounts = byRef.mapValues { (_, template) -> lazy { template.mountOffset() } }
    }

    @Volatile
    private var loaded = Loaded(templates)
    private val plansByRef = plans.associateBy { it.ref }

    operator fun get(ref: TemplateRef): PartTemplate =
        requireNotNull(loaded.byRef[ref]) { "No template ${ref.id.value} version ${ref.version}" }

    /** The template pinned by [ref], if it is loaded. */
    fun find(ref: TemplateRef): PartTemplate? = loaded.byRef[ref]

    /** The newest loaded template with this id, if any: what an empty socket would take. */
    fun latest(id: TemplateId): PartTemplate? = loaded.byId[id]?.maxByOrNull { it.version }

    /** How far a part pinned to [ref] sits inside what it is on. See [socketSink]. */
    fun socketSink(ref: TemplateRef): Float = requireNotNull(loaded.sinks[ref]) { "No template ${ref.id.value} version ${ref.version}" }.value

    /** How far a part pinned to [ref] is carried back from its socket so its mount sits there. See [mountOffset]. */
    fun mountOffset(ref: TemplateRef): Vec3 = requireNotNull(loaded.mounts[ref]) { "No template ${ref.id.value} version ${ref.version}" }.value

    fun plan(ref: BodyPlanRef): BodyPlan? = plansByRef[ref]

    /** Loads [template], a template saved while the library is open; one whose ref is already loaded is ignored. */
    fun add(template: PartTemplate) {
        val current = loaded
        if (template.ref in current.byRef) return
        loaded = Loaded(current.byRef.values + template)
    }
}
