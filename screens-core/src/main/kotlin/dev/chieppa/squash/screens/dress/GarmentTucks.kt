package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.ChartBox
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetTuck
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.TuckChoice
import dev.chieppa.squash.document.TuckKind

/** The parameter of a foot's or hand's template that sets how many toes it builds. */
private const val TOES_PARAM = "toes"

/** The parameter of a foot's template that sets whether its toes have claws. */
private const val CLAWS_PARAM = "claws"

/** What template ids name a spike or a claw grown as a part of its own. */
private const val SPIKE_TEMPLATE = "spike"
private const val CLAW_TEMPLATE = "claw"

/**
 * One small thing inside a garment the Tuck rows offer: [kind] on [part], the leader of a pair, [tucked] or shaped; [choosable] when
 * both are possible, and [pokes] when it is shaped and pokes out through the cloth in lumps.
 */
data class TuckItem(val part: PartId, val kind: TuckKind, val tucked: Boolean, val choosable: Boolean = true) {
    val pokes: Boolean get() = !tucked && (kind == TuckKind.TOES || kind == TuckKind.CLAWS)
}

/** The parameter a tucked [kind] of digits sets to nothing. */
internal fun digitParam(kind: TuckKind): String = if (kind == TuckKind.CLAWS) CLAWS_PARAM else TOES_PARAM

/** What a part of [role] made from [template] is as a small part a garment can hold whole, or null for a part that is not one. */
fun tuckKindOf(role: Role?, template: String): TuckKind? = when (role) {
    Role.EAR -> TuckKind.EARS
    Role.TAIL -> TuckKind.TAIL
    Role.FIN -> TuckKind.FINS
    Role.HORN -> when {
        template.startsWith(SPIKE_TEMPLATE) -> TuckKind.SPIKES
        template.startsWith(CLAW_TEMPLATE) -> TuckKind.CLAWS
        else -> TuckKind.HORNS
    }
    else -> null
}

/**
 * The small things [garment] holds whole on [body], reading what its cloth holds of each part from [held]: each small part it holds
 * whole, and the toes and claws at the end of a part whose template builds toes, or the fingers of a hand that builds none, where it reaches the end. Each is tucked by the garment's choice
 * for it, else by its kind's first. Fingers are always shaped while no template can build a hand without them; claws are listed only
 * while the toes are shaped.
 */
fun tucksOf(character: Character, body: AssembledCharacter, garment: Garment, held: Map<PartId, ChartBox>): List<TuckItem> =
    itemsOf(character, body, held.mapValues { (_, box) -> Reach(box.whole, box.to >= 1f) }) { part, kind ->
        garment.tucks.firstOrNull { it.part == part && it.kind == kind }?.tucked ?: kind.tuckedFirst
    }

/**
 * The small things [garment] holds whole on [body] as its branches and changes have them now: a part it hides holds tucked, one its
 * branch covers whole shaped; toes and claws tucked where its Redraw of the part builds none.
 */
fun tucksOf(character: Character, body: AssembledCharacter, garment: Garment): List<TuckItem> {
    val reach = HashMap<PartId, Reach>()
    for (branch in garment.branches) reach[branch.part] = Reach(branch.from <= 0f && branch.to >= 1f && branch.allRound, branch.to >= 1f)
    for ((part, change) in garment.changes) if (change == BodyPartChange.Hide && character.part(part) != null) reach.putIfAbsent(part, Reach(true, true))
    return itemsOf(character, body, reach) { part, kind ->
        val change = garment.changes[part]
        if (kind.digits) (change as? BodyPartChange.Redraw)?.data?.params?.get(digitParam(kind)) == 0f else change == BodyPartChange.Hide
    }
}

/**
 * The Tuck rows [garment] shows over [body]: the small things inside it as its painted cloth holds them, or as its branches hold them
 * for one never painted, and with a part [selected], only those on it, on parts beyond it, or on the part it hangs from.
 */
fun shownTucks(character: Character, body: AssembledCharacter, garment: Garment, selected: PartId?): List<TuckItem> {
    val items = if (garment.areas.isNotEmpty()) tucksOf(character, body, garment, paintedOf(character, body, garment).boxes) else tucksOf(character, body, garment)
    val lead = selected?.let(character::part)?.let(character::leaderOf) ?: return items
    return items.filter { item -> item.part == lead.socket?.parent || character.isWithin(item.part, lead.id) }
}

/** Whether [id] is [root] or sits beyond it, socket on socket. */
private fun Character.isWithin(id: PartId, root: PartId): Boolean {
    var at = part(id)
    while (at != null) {
        if (at.id == root) return true
        at = at.socket?.parent?.let(::part)
    }
    return false
}

/** How far a garment holds a part: [whole], or to its far end, [end]. */
private class Reach(val whole: Boolean, val end: Boolean)

private fun itemsOf(character: Character, body: AssembledCharacter, reach: Map<PartId, Reach>, tucked: (PartId, TuckKind) -> Boolean): List<TuckItem> {
    val out = ArrayList<TuckItem>()
    for ((part, held) in reach) {
        val instance = character.part(part) ?: continue
        val placed = body.part(part)
        val kind = tuckKindOf(placed?.role ?: instance.role, (placed?.template?.id ?: instance.template.id).value)
        if (kind != null && held.whole) {
            out += TuckItem(part, kind, tucked(part, kind))
            continue
        }
        if (!held.end || placed == null) continue
        val params = placed.template.params.keys
        if (TOES_PARAM in params) {
            val toes = tucked(part, TuckKind.TOES)
            out += TuckItem(part, TuckKind.TOES, toes)
            if (!toes && CLAWS_PARAM in params) out += TuckItem(part, TuckKind.CLAWS, tucked(part, TuckKind.CLAWS))
        }
        if (TOES_PARAM !in params && placed.role == Role.HAND) out += TuckItem(part, TuckKind.FINGERS, tucked = false, choosable = false)
    }
    return out
}

/**
 * The command that makes [item] of [garment] [tucked] or shaped, one step: the garment's choice, then its shape written again from its
 * painted cloth, or for a garment never painted, its change to the part set directly.
 */
fun tuckCommand(character: Character, body: AssembledCharacter, garment: Garment, item: TuckItem, tucked: Boolean): DocumentCommand {
    val choice = SetTuck(garment.id, item.part, item.kind, tucked)
    val chosen = garment.copy(tucks = garment.tucks.filterNot { it.part == item.part && it.kind == item.kind } + TuckChoice(item.part, item.kind, tucked))
    if (garment.areas.isNotEmpty()) return Together(listOf(choice, shapeCommands(character, body, chosen, paintedOf(character, body, garment))))
    val held = garment.changes[item.part]
    val next: BodyPartChange? = if (!item.kind.digits) {
        BodyPartChange.Hide.takeIf { tucked }
    } else {
        val data = (held as? BodyPartChange.Redraw)?.data ?: PartData()
        val params = (data.params - digitParam(item.kind)) + if (tucked) mapOf(digitParam(item.kind) to 0f) else emptyMap()
        val redrawn = data.copy(params = params)
        if (redrawn == PartData()) null else BodyPartChange.Redraw(redrawn)
    }
    return Together(listOf(choice, SetBodyPartChange(garment.id, item.part, next)))
}
