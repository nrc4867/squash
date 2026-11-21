package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.CurveFit
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.catalog.guessRole
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SetPartBind
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.document.SetSocketBlend
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.recipe.model.RecipePart
import kotlin.math.roundToInt

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
    shape(path, ids.left, template, part, sockets?.first()?.let(::rowScale) ?: 1f)
    part.blend?.let { blend -> ids.all.forEach { send("$path.blend", SetSocketBlend(it, blend)) } }
    if (part.bind != null || part.stiff != null) {
        if (garment == null) fail(if (part.bind != null) "$path.bind" else "$path.stiff", "only a part a garment owns is bound to the body; give it a garment")
        send(if (part.bind != null) "$path.bind" else "$path.stiff", SetPartBind(ids.left, part.bind, part.stiff))
    }
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

/** The size a part placed on [socket] starts at: its row's share for a socket of a row, else 1. */
private fun BuildContext.rowScale(socket: SocketRef): Float {
    val instance = document.character.part(socket.parent) ?: return 1f
    val spec = socketsOn(plan, templateOf(socket.parent), document.character.resolvedShape(instance).params).firstOrNull { it.name == socket.socket }
    return if (spec?.copies == null) spec?.scale ?: 1f else 1f
}

/** The names of the sockets [part] offers as it stands: its plan's or template's, its hand-made ones and its digits' tips. */
internal fun BuildContext.offeredSockets(part: PartId): List<String> {
    val instance = checkNotNull(document.character.part(part))
    return socketsOn(plan, templateOf(part), document.character.resolvedShape(instance).params).map { it.name }
}

/**
 * Sends [part]'s hand-made sockets, parameters and outlines to its left side, which a pair shares; with no size of its own it takes
 * [scale], the size a part on its socket starts at.
 */
private fun BuildContext.shape(path: String, id: PartId, template: PartTemplate, part: RecipePart, scale: Float) {
    val size = part.size ?: scale.takeIf { it != 1f }
    val params = presetParams(path, template, part.preset) + shapeParams(size, part.lean, part.outward, anatomyParams(path, part) + part.params)
    val own = plan.socketsOn(template)
    part.sockets.forEachIndexed { i, s -> send("$path.sockets[$i]", socketCommand("$path.sockets[$i]", id, s, params, part.sockets) { name -> own.firstOrNull { it.name == name } }) }
    checkParams(path, template, params)
    if (params.isNotEmpty()) send("$path.params", SetParameters(id, params))
    val outlines = if (part.outlines.isNotEmpty()) curves(path, part.outlines) else emptyMap()
    if (outlines.isNotEmpty()) send("$path.outlines", SetOutlines(id, outlines))
    checkCurve(path, template, part, params, outlines)
}

/** The values of [template]'s preset [name], none without one. */
private fun BuildContext.presetParams(path: String, template: PartTemplate, name: String?): Map<String, Float> {
    if (name == null) return emptyMap()
    return template.presets[name]
        ?: fail("$path.preset", "template ${template.id.value} has no preset \"$name\"; it has ${template.presets.keys.joinToString().ifEmpty { "none" }}")
}

/** Warns where [part]'s own curve is ignored by its template, or bends tighter than the part is thick so its rings narrow. */
private fun BuildContext.checkCurve(path: String, template: PartTemplate, part: RecipePart, params: Map<String, Float>, outlines: Map<View, OutlineCurve>) {
    val fit = CurveFit.of(template, params, outlines) ?: return
    val at = if (part.spiral != null) "$path.spiral" else "$path.curve"
    if (fit.ignored) {
        warn(at, "template ${template.id.value} does not sweep along its axis (it has a skull, a field, digits or a membrane), so its curve is ignored; templates.md says which templates take curve")
    } else if (fit.clipped) {
        warn(at, "the curve bends tighter than the part is thick, so its rings narrow to ${(fit.hold * 100f).roundToInt()}% there; spread the places or the coil, or thin the part")
    }
}
