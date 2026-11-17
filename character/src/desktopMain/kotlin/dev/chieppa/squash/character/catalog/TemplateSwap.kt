package dev.chieppa.squash.character.catalog

import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.model.SwapFamily
import dev.chieppa.squash.character.model.swapsWith
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.document.SetSocket
import dev.chieppa.squash.document.SetTemplate
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.Together
import kotlin.math.abs
import kotlin.math.min

/** How far along a part, and how far around it, a socket of the new template may lie from the one a child leaves and still take it. */
const val SEAT_REACH = 0.25f

/**
 * Where the parts on [part], and on the other side of its pair, sit once it has [to] in place of [from]: the socket names that change,
 * each to the one it becomes. A socket [to] also has by name keeps its parts. Where [to] swaps with [from] ([swapsWith]), one it lacks
 * hands its parts to the free socket of [to] nearest where it was, within [SEAT_REACH] along and around the part and mirrored as it
 * was, so a paw at a leg's end moves to a bird leg's foot socket there. Null when some socket finds none, and [part] cannot take [to]
 * keeping what it holds.
 */
fun Character.swapSeats(part: PartInstance, from: PartTemplate, to: PartTemplate, plan: BodyPlan?): Map<String, String>? {
    val sides = setOfNotNull(part.id, twinOf(part)?.id)
    val held = parts.mapNotNull { it.socket }.filter { it.parent in sides }.map { it.socket }.distinct().sorted()
    if (held.isEmpty()) return emptyMap()
    val params = resolvedShape(part).params
    val before = socketsOn(plan, from, params).associateBy { it.name }
    val after = socketsOn(plan, to, params)
    val kept = held.filter { name -> after.any { it.name == name } }.toSet()
    val taken = kept.toMutableSet()
    val moves = mutableMapOf<String, String>()
    if (kept.size < held.size && !from.swapsWith(to)) return null
    for (name in held - kept) {
        val was = before[name] ?: return null
        val seat = after.filter { it.name !in taken && (it.pair == null) == (was.pair == null) }
            .map { it to was.reachTo(it) }
            .filter { (_, reach) -> reach.first <= SEAT_REACH && reach.second <= SEAT_REACH }
            .minByOrNull { (_, reach) -> reach.first + reach.second }?.first ?: return null
        taken += seat.name
        moves[name] = seat.name
    }
    return moves
}

/** How far [other] lies from this socket along the part and around it, the long way round the seam never counted. */
private fun SocketSpec.reachTo(other: SocketSpec): Pair<Float, Float> {
    val around = abs(v - other.v) % 1f
    return abs(u - other.u) to min(around, 1f - around)
}

/**
 * The step that gives [part], and the other side of its pair, [to] in place of [from], keeping its drawing, with the parts on it seated
 * as [swapSeats] says and where the Move tool put a socket they leave carried to the one they take. The part keeps a role in [to]'s
 * [SwapFamily], so a four-legged body's front leg stays an arm as a bird leg; otherwise a typed [to] gives its own. Null when the parts
 * on it cannot all be seated.
 */
fun Character.swapTemplateCommand(part: PartInstance, from: PartTemplate, to: PartTemplate, plan: BodyPlan?): DocumentCommand? {
    val seats = swapSeats(part, from, to, plan) ?: return null
    val sides = setOfNotNull(part.id, twinOf(part)?.id)
    val moves = parts.mapNotNull { child ->
        val on = child.socket?.takeIf { it.parent in sides } ?: return@mapNotNull null
        seats[on.socket]?.let { SetSocket(child.id, SocketRef(on.parent, it)) }
    }
    val family = SwapFamily.of(to.role)
    val role = if (family != null && SwapFamily.of(part.role) == family) null else to.role?.let { SetRole(part.id, it) }
    val placed = movedPlaces(part, seats)
    return Together(listOfNotNull(SetTemplate(part.id, to.ref), role, placed) + moves)
}

/** The step that carries where the Move tool put each socket of [part] named in [seats] to the socket it becomes, or null for none. */
private fun Character.movedPlaces(part: PartInstance, seats: Map<String, String>): DocumentCommand? {
    val params = resolvedShape(part).params
    val values = buildMap<String, Float?> {
        for ((old, new) in seats) {
            for (side in listOf(null, Side.LEFT, Side.RIGHT)) {
                for ((key, place) in listOf(PlacementParams::socketU, PlacementParams::socketV).map { it(old, side) to it }) {
                    val value = params[key] ?: continue
                    put(key, null)
                    put(place(new, side), value)
                }
            }
        }
    }
    return if (values.isEmpty()) null else SetParameters(part.id, values)
}
