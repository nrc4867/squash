package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.catalog.guessRole
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.document.SetSocketBlend
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.recipe.model.RecipePart

/** Places [part], recipe entry [index], on what it sits on and gives it its shape; its surface work comes after every part is placed. */
internal fun BuildContext.placePart(index: Int, part: RecipePart): PartIds {
    val path = "$.parts[$index]"
    val ids = name(path, part)
    val template = template("$path.template", part.template, part.version)
    val garment = part.garment?.let { id ->
        if (document.character.wardrobe.garment(GarmentId(id)) == null) fail("$path.garment", "no garment \"$id\" in the wardrobe")
        GarmentId(id)
    }
    val sockets = sockets(path, part, ids)
    val role = part.role ?: template.role
    val instances = ids.all.mapIndexed { side, id ->
        PartInstance(
            id, template.ref, sockets?.get(side),
            pair = part.pair?.let { PairId(part.id) }, side = if (part.pair == null) null else if (side == 0) Side.LEFT else Side.RIGHT,
            role = role, roleGuessed = role == null, garment = garment,
        )
    }
    send(path, AddParts(instances))
    shape(path, ids.left, template, part)
    part.blend?.let { blend -> ids.all.forEach { send("$path.blend", SetSocketBlend(it, blend)) } }
    if (role == null) ids.all.forEach { id ->
        val placed = checkNotNull(document.character.part(id))
        send("$path.role", SetRole(id, guessRole(document.character, placed, templates), guessed = true))
    }
    return ids
}

/** The socket each side sits on, checked against the sockets its parent offers; null for the root. */
private fun BuildContext.sockets(path: String, part: RecipePart, ids: PartIds): List<SocketRef>? {
    val on = part.on
    if (on == null) {
        if (part.socket != null) fail("$path.on", "a part with a socket names the part it sits on")
        if (document.character.parts.any { it.socket == null && it.garment == null }) fail("$path.on", "the character has a root part already; name the part this one sits on")
        return null
    }
    val socket = part.socket ?: fail("$path.socket", "name the socket on $on this part sits on")
    val parents = idsOf("$path.on", on)
    if (part.pair == null && parents.right != null) fail("$path.on", "\"$on\" is a pair; name one side, ${parents.left.value} or ${parents.right.value}")
    val offered = offeredSockets(parents.left)
    if (socket !in offered) fail("$path.socket", "${parents.left.value} has no socket \"$socket\"; it offers ${offered.joinToString().ifEmpty { "none" }}")
    return ids.all.mapIndexed { side, _ -> SocketRef(if (side == 1 && parents.right != null) parents.right else parents.left, socket) }
}

/** The names of the sockets [part] offers as it stands: its plan's or template's, its hand-made ones and its digits' tips. */
internal fun BuildContext.offeredSockets(part: PartId): List<String> {
    val instance = checkNotNull(document.character.part(part))
    return socketsOn(plan, templateOf(part), document.character.resolvedShape(instance).params).map { it.name }
}

/** Sends [part]'s hand-made sockets, parameters and outlines to its left side, which a pair shares. */
private fun BuildContext.shape(path: String, id: PartId, template: PartTemplate, part: RecipePart) {
    part.sockets.forEachIndexed { i, s -> send("$path.sockets[$i]", HandSockets.add(id, s.n, s.u, s.v, s.outward, s.mirrored, s.sink)) }
    val params = shapeParams(part.size, part.lean, part.outward, part.params)
    checkParams(path, template, params)
    if (params.isNotEmpty()) send("$path.params", SetParameters(id, params))
    if (part.outlines.isNotEmpty()) send("$path.outlines", SetOutlines(id, curves(path, part.outlines)))
}
