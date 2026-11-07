package dev.chieppa.squash.screens

import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.catalog.startingData
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartData

/** The sockets of the open character with nothing on them. */
fun AppState.openSocketsNow(): List<OpenSocket> {
    val document = session.value?.document?.value ?: return emptyList()
    val plan = templates.plan(document.character.bodyPlan) ?: return emptyList()
    return openSockets(document, plan, templates)
}

/**
 * Adds the type chosen in Add part's popover on its socket, both sides of a pair in one step, selects it and closes the popover.
 * Only a type the socket takes is added.
 */
fun AppState.addPart() {
    val opened = session.value ?: return
    val socket = addingSocket.value ?: return
    val template = addingType.value?.takeIf { it in socket.takes }?.let { templates.template(it) } ?: return
    val start = templates.plan(opened.document.value.character.bodyPlan)?.startingData() ?: PartData()
    val document = opened.document.value
    val parts = placeOnSocket(socket, template, document, start, templates.roles(document.character))
    opened.send(AddParts(parts, pairs = parts.mapNotNull { it.pair }.associateWith { start }))
    closeAddPart()
    selectPart(parts.first().id)
}
