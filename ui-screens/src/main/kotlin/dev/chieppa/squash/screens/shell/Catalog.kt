package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.Outward
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef

const val PAIR_SIZE = 2

/** The loaded template with this id, if any. */
fun TemplateFolder.template(id: TemplateId): PartTemplate? = parts.firstOrNull { it.id == id }

/** The loaded template pinned by [ref], if any. */
fun TemplateFolder.template(ref: TemplateRef): PartTemplate? = parts.firstOrNull { it.ref == ref }

/** The loaded body plan pinned by [ref], if any. */
fun TemplateFolder.plan(ref: BodyPlanRef): BodyPlan? = plans.firstOrNull { it.ref == ref }

/** The sockets a part with [template] offers: its body plan's and its own, by name. */
fun BodyPlan.socketsOn(template: PartTemplate): List<SocketSpec> {
    val planned = sockets.filter { it.on == template.id }.map { SocketSpec(it.name, it.u, it.v, Outward.AXIS, it.pair, it.takes) }
    val own = template.blocks.flatMap { it.sockets() }
    return (planned + own).distinctBy { it.name }
}

/** The first template [spec] takes that is loaded; a socket with none is not offered. */
fun TemplateFolder.firstTaken(spec: SocketSpec): PartTemplate? = spec.takes.firstNotNullOfOrNull { template(it) }

/** Every part of a whole character on this plan, one to a socket, a mirrored pair counting once. */
fun BodyPlan.partCount(templates: TemplateFolder): Int {
    fun under(template: PartTemplate, seen: Set<TemplateId>): Int =
        socketsOn(template).sumOf { spec ->
            val child = templates.firstTaken(spec)
            if (child == null || child.id in seen) 0 else 1 + under(child, seen + child.id)
        }
    val root = templates.template(root.template) ?: return 0
    return 1 + under(root, setOf(root.id))
}

/** The loaded templates in the order this plan reaches them from its root; any it never reaches come last. */
fun BodyPlan.templateOrder(templates: TemplateFolder): List<PartTemplate> {
    val reached = linkedSetOf<PartTemplate>()
    fun visit(template: PartTemplate) {
        if (!reached.add(template)) return
        socketsOn(template).forEach { spec -> templates.firstTaken(spec)?.let(::visit) }
    }
    templates.template(root.template)?.let(::visit)
    return reached.toList() + (templates.parts - reached)
}

/** A template and how many instances of it a plan places directly: what New character lists as chips. */
data class PlanChip(val name: String, val count: Int)

/** The root and the plan's own sockets, grouped by the template each takes, in plan order. */
fun BodyPlan.chips(templates: TemplateFolder): List<PlanChip> {
    val placed = buildList {
        templates.template(root.template)?.let { add(it to 1) }
        for (socket in sockets) {
            val child = socket.takes.firstNotNullOfOrNull { templates.template(it) } ?: continue
            add(child to if (socket.pair != null) PAIR_SIZE else 1)
        }
    }
    return placed.groupBy({ it.first.name }, { it.second }).map { (name, counts) -> PlanChip(name, counts.sum()) }
}
