package dev.chieppa.squash.character.lint

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.PlanSocket
import dev.chieppa.squash.character.model.TEMPLATE_FORMAT
import dev.chieppa.squash.document.TemplateId

private const val V_TOP = 0f
private const val V_UNDER = 0.5f

/** The semantic checks on a body plan against the [templates] that ship with it. */
object BodyPlanLint {
    fun lint(plan: BodyPlan, parts: List<PartTemplate>): List<Finding> = buildList {
        val templates = parts.map { it.id }.toSet()
        if (plan.format != TEMPLATE_FORMAT) add(Finding("$.format", "format ${plan.format} is not $TEMPLATE_FORMAT"))
        if (plan.root.template !in templates) add(Finding("$.root.template", "no template '${plan.root.template.value}'"))
        val root = parts.newest(plan.root.template)
        if (root != null && root.axis.rest != plan.root.rest) {
            add(Finding("$.root.rest", "the plan's rest is not its root template's, and a root part is not turned"))
        }
        plan.sockets.forEachIndexed { i, socket ->
            addAll(socket(socket, "$.sockets[$i]", templates))
            val on = parts.newest(socket.on)
            if (on != null && on.blocks.none { it.reaches(socket.u) }) {
                add(Finding("$.sockets[$i].u", "no run of '${socket.on.value}' reaches u = ${socket.u}"))
            }
        }
        plan.slots.forEachIndexed { i, slot ->
            if (slot.on !in templates) add(Finding("$.slots[$i].on", "no template '${slot.on.value}'"))
        }
        val named = plan.palette.map { it.name }
        named.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { add(Finding("$.palette", "two slots named '$it'")) }
        if (plan.base != null && plan.base !in named) add(Finding("$.base", "no palette slot '${plan.base}'"))
        plan.sockets.groupBy { it.name }.filter { it.value.size > 1 }.keys.forEach {
            add(Finding("$.sockets", "two sockets named '$it'"))
        }
    }

    private fun socket(socket: PlanSocket, path: String, templates: Set<TemplateId>): List<Finding> = buildList {
        if (socket.on !in templates) add(Finding("$path.on", "no template '${socket.on.value}'"))
        if (socket.u !in 0f..1f) add(Finding("$path.u", "${socket.u} is outside 0 to 1"))
        if (socket.v !in 0f..1f) add(Finding("$path.v", "${socket.v} is outside 0 to 1"))
        if (socket.pair != null && (socket.v == V_TOP || socket.v == V_UNDER || socket.v == 1f)) {
            add(Finding("$path.v", "a paired socket at v = ${socket.v} coincides with its twin"))
        }
        if (!socket.optional) {
            socket.takes.filter { it !in templates }.forEach { add(Finding("$path.takes", "no template '${it.value}'")) }
        }
    }
}

/** The newest of [parts] with [id], which a part placed on the plan takes. */
private fun List<PartTemplate>.newest(id: TemplateId): PartTemplate? = filter { it.id == id }.maxByOrNull { it.version }
