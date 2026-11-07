package dev.chieppa.squash.character.catalog

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef

const val PAIR_SIZE = 2

/** The newest loaded template with this id, if any: what a new part of that type takes. */
fun TemplateFolder.template(id: TemplateId): PartTemplate? = parts.filter { it.id == id }.maxByOrNull { it.version }

/** The newest loaded version of each template, in the order of their files. */
val TemplateFolder.newest: List<PartTemplate> get() = parts.map { it.id }.distinct().mapNotNull(::template)

/** The loaded template pinned by [ref], if any. */
fun TemplateFolder.template(ref: TemplateRef): PartTemplate? = parts.firstOrNull { it.ref == ref }

/** The loaded body plan pinned by [ref], if any. */
fun TemplateFolder.plan(ref: BodyPlanRef): BodyPlan? = plans.firstOrNull { it.ref == ref }

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

/** The newest loaded templates in the order this plan reaches them from its root; any it never reaches come last. */
fun BodyPlan.templateOrder(templates: TemplateFolder): List<PartTemplate> {
    val reached = linkedSetOf<PartTemplate>()
    fun visit(template: PartTemplate) {
        if (!reached.add(template)) return
        socketsOn(template).forEach { spec -> templates.firstTaken(spec)?.let(::visit) }
    }
    templates.template(root.template)?.let(::visit)
    return reached.toList() + (templates.newest - reached)
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

/**
 * A part's name: its type's, so a head on a neck socket reads Head, led by the place its socket names before the type, as front-leg
 * does, when it reads Front leg.
 */
fun partName(part: PartInstance, templates: TemplateFolder): String = named(part.socket?.socket, part, templates) ?: typeName(part, templates)

/**
 * A part's name taking the place its [parent]'s socket names when its own names none: a paw on a front leg reads Front paw, as the
 * front-leg socket names the leg's place.
 */
fun partNameOn(part: PartInstance, parent: PartInstance?, templates: TemplateFolder): String =
    named(part.socket?.socket, part, templates) ?: named(parent?.socket?.socket, part, templates) ?: typeName(part, templates)

/** [part]'s type's name led by the place [socket] names, or null when it names none. */
private fun named(socket: String?, part: PartInstance, templates: TemplateFolder): String? {
    val place = placeOf(socket) ?: return null
    return displayName(place) + " " + typeName(part, templates).lowercase()
}

/** The place a socket's name gives before what it takes, front in front-leg, or null when it names none. */
fun placeOf(socket: String?): String? = socket?.substringBeforeLast(PLACE_SEPARATOR, "")?.takeIf { it.isNotEmpty() }

private fun typeName(part: PartInstance, templates: TemplateFolder): String = templates.template(part.template)?.name ?: displayName(part.template.id.value)

/** What separates the place a socket's name gives from the rest, as in front-leg. */
private const val PLACE_SEPARATOR = '-'
