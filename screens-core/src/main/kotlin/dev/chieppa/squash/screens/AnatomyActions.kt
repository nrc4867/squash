package dev.chieppa.squash.screens

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.character.build.placedBy
import dev.chieppa.squash.character.build.savedTemplate
import dev.chieppa.squash.character.build.savedTemplateId
import dev.chieppa.squash.character.catalog.newest
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.load.freeId
import dev.chieppa.squash.character.load.save
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.document.BreakPair
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.JoinPair
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.document.SetTemplate
import dev.chieppa.squash.document.Together

private const val FLAG_ON = 1f

/** The readings that place a part's outlines in its axes, which a template a part changes to must give to the same views. */
private val AXIS_READINGS = setOf(Sets.HEIGHT, Sets.WIDTH)

private fun AppState.part(id: PartId): PartInstance? = session.value?.document?.value?.character?.part(id)

/**
 * Gives [part] [direction] to rest in, or its template's with null, as one undo step. A part whose role is a guess has it guessed
 * again from the way it now rests, in the same step, so a tube turned back on the body's rear end reads tail. See HLD: data-model,
 * Placing parts.
 */
fun AppState.setRest(part: PartId, direction: RestDirection?) {
    val opened = session.value ?: return
    val character = opened.document.value.character
    val instance = character.part(part) ?: return
    val thisSide = thisSide && instance.pair != null
    val rest = RestDirection.restIn(part, direction, thisSide)
    val roles = templates.roles(character)
    val guessing = instance.role == null || instance.roleGuessed
    val template = templates.template(instance.template)
    if (!guessing || template == null || instance.socket == null) {
        opened.send(rest)
        return
    }
    val params = character.resolvedShape(instance).params - PlacementParams.REST + rest.values.mapNotNull { (k, v) -> v?.let { k to it } }
    opened.send(Together(listOf(rest, SetRole(part, roles.guess(instance, template.placedBy(params)), guessed = true))))
}

/**
 * The parts [part] can pair with: unpaired parts of the same template on the same parent, or on the other side of a paired parent
 * on a socket of the same name, which is how a pair sits across the middle.
 */
fun AppState.pairCandidates(part: PartId): List<PartInstance> {
    val character = session.value?.document?.value?.character ?: return emptyList()
    val own = character.part(part)?.takeIf { it.pair == null } ?: return emptyList()
    val socket = own.socket ?: return emptyList()
    val parentTwin = character.part(socket.parent)?.let(character::twinOf)?.id
    return character.parts.filter { other ->
        val on = other.socket ?: return@filter false
        other.id != part && other.pair == null && other.template == own.template &&
            (on.parent == socket.parent || (on.parent == parentTwin && on.socket == socket.socket))
    }
}

/**
 * Makes [part] and [other] a pair as one undo step: [part] is the left side, whose outlines, parameters and surface work the pair
 * shares, and [other] the right, keeping what it held of its own as its overrides. On one parent the right side moves onto the
 * left's socket, which a hand-made socket marks as paired, and a hand-made socket it leaves empty goes. See HLD: data-model, Pairs
 * by hand.
 */
fun AppState.pairWith(part: PartId, other: PartId) {
    val opened = session.value ?: return
    val left = part(part)?.takeIf { it.pair == null } ?: return
    val right = part(other)?.takeIf { it.pair == null } ?: return
    val socket = left.socket ?: return
    val from = right.socket ?: return
    val oneParent = from.parent == socket.parent
    val commands = buildList<DocumentCommand> {
        add(JoinPair(PairId("${left.id.value}+${right.id.value}"), left.id, right.id, if (oneParent) socket else from))
        if (oneParent) HandSockets.idOf(socket.socket)?.let { add(SetParameter(socket.parent, HandSockets.pair(it), FLAG_ON)) }
        val emptied = oneParent && from != socket && opened.document.value.character.parts.none { it.socket == from && it.id != right.id }
        if (emptied) HandSockets.idOf(from.socket)?.let { add(HandSockets.delete(from.parent, it)) }
    }
    opened.send(Together(commands))
}

/** Breaks [part]'s pair, one undo step: each side keeps what it shows. */
fun AppState.unpair(part: PartId) {
    val pair = part(part)?.pair ?: return
    session.value?.send(BreakPair(pair))
}

/**
 * The templates [part] can change to, keeping its drawing: each newest loaded template but its own whose views set height and width
 * as its own do, and that holds every socket of its own a child sits on. The root keeps its plan's template. See HLD: parts,
 * Re-templating keeps the drawing.
 */
fun AppState.templateChoices(part: PartId): List<PartTemplate> {
    val document = session.value?.document?.value ?: return emptyList()
    val instance = document.character.part(part)?.takeIf { it.socket != null } ?: return emptyList()
    val own = templates.template(instance.template) ?: return emptyList()
    val plan = templates.plan(document.character.bodyPlan)
    val held = document.character.parts.mapNotNull { it.socket }.filter { it.parent == part && HandSockets.idOf(it.socket) == null }.map { it.socket }
    fun axisViews(template: PartTemplate) = template.views.filterValues { it in AXIS_READINGS }
    return templates.newest.filter { template ->
        val sockets = (plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }).map { it.name }
        template.id != own.id && axisViews(template) == axisViews(own) && sockets.containsAll(held)
    }
}

/** Gives [part] and its pair's other side [template], keeping outlines, parameters and surface work, one undo step; a typed template gives its role. */
fun AppState.changeTemplate(part: PartId, template: PartTemplate) {
    val role = template.role?.let { SetRole(part, it) }
    session.value?.send(Together(listOfNotNull(SetTemplate(part, template.ref), role)))
}

/**
 * Writes [part] as a template called [name] into the saved templates' folder, with its role, its parameters' values, its shape
 * choices and its joints, sockets and landmarks; the template, or null when there is no folder, the name gives no id or the
 * template would not lint clean. It loads with the catalog from the next start. See HLD: data-model, Save as template.
 */
fun AppState.saveAsTemplate(part: PartId, name: String): PartTemplate? {
    val character = session.value?.document?.value?.character ?: return null
    val instance = character.part(part) ?: return null
    val unplaced = templates.template(instance.template) ?: return null
    val id = savedTemplateId(name)?.let(templates::freeId) ?: return null
    val role = templates.roles(character).resolved(instance)
    val template = savedTemplate(unplaced, character.resolvedShape(instance).params, role, id, name.trim())
    if (TemplateLint.lint(template).isNotEmpty()) return null
    return template.takeIf { templates.save(it) != null }
}
