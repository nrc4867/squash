package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.document.KeyFeature
import dev.chieppa.squash.document.KeyMove
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.ViewKey
import dev.chieppa.squash.document.ViewKeys

/** One thing a key moves: a whole [part], with everything on it, or its [run] or piece by name, or one of its areas, [area]. */
data class KeyElement(val part: PartId, val run: String? = null, val area: String? = null)

/** What the key tracks need to know of a character: its heads, what a feature names on each, the head a part belongs to and its twin. */
interface KeyLookup {
    /** Every head of the character. */
    val heads: List<PartId>

    /** What [feature] names on [head]; empty where the head has none. */
    fun feature(head: PartId, feature: KeyFeature): List<KeyElement>

    /** The head [part] is, or the nearest head it sits on, or null for a part on no head. */
    fun headOf(part: PartId): PartId?

    /** The other side of [part]'s pair, or null for a part in the middle. */
    fun twinOf(part: PartId): PartId?
}

/** [move] as the camera on the head's right sees it: its sideways slide, yaw, roll and twist turned the other way. */
fun KeyMove.mirrored(): KeyMove = copy(left = -left, yaw = -yaw, roll = -roll, twist = -twist)

/** A move on an element, and the head whose directions it is read in. */
data class ElementMove(val element: KeyElement, val head: PartId, val move: KeyMove)

/**
 * The moves at each of [KEY_DIRECTIONS] for a character [lookup] describes under [keys]: with [ViewKeys.auto], each head's [defaults]
 * on it; then the keys authored for either side, mirrored for the right with each paired part swapped for its twin; then the keys
 * authored for that side alone. A move on a part replaces a feature's move on the same element, and a later move replaces an earlier,
 * so an authored key replaces the default for what it moves and leaves the rest. A move on a part on no head moves nothing.
 */
fun keyTracks(keys: ViewKeys, lookup: KeyLookup, defaults: (PartId) -> List<ViewKey> = { DEFAULT_VIEW_KEYS }): List<List<ElementMove>> =
    KEY_DIRECTIONS.map { direction ->
        val chosen = LinkedHashMap<KeyElement, ElementMove>()
        fun ViewKey.holds(side: Side?) = view == direction.view && this.side == side
        val defaulted = if (!keys.auto) emptyList() else lookup.heads.flatMap { head ->
            defaults(head).filter { it.holds(null) }.flatMap { key -> key.moves.map { Triple(it, direction.mirrored, head) } }
        }
        val authored = (keys.keys.filter { it.holds(null) }.map { it to direction.mirrored } +
            (if (direction.side != null) keys.keys.filter { it.holds(direction.side) }.map { it to false } else emptyList()))
            .flatMap { (key, mirror) -> key.moves.map { Triple(it, mirror, null as PartId?) } }
        val (features, parts) = authored.partition { it.first.feature != null }
        for ((move, mirror, only) in defaulted + features + parts) {
            val shown = if (mirror) move.mirrored() else move
            for ((element, head) in elementsOf(move, mirror, lookup, only)) chosen[element] = ElementMove(element, head, shown)
        }
        chosen.values.toList()
    }

/**
 * What [move] moves, each with its head: a feature on every head, or on [only], or the part, its twin when [mirror] holds and it has one.
 */
private fun elementsOf(move: KeyMove, mirror: Boolean, lookup: KeyLookup, only: PartId?): List<Pair<KeyElement, PartId>> {
    move.feature?.let { feature ->
        return (only?.let(::listOf) ?: lookup.heads).flatMap { head -> lookup.feature(head, feature).map { it.copy(area = move.area) to head } }
    }
    val named = requireNotNull(move.part)
    val part = if (mirror) lookup.twinOf(named) ?: named else named
    val head = lookup.headOf(part) ?: return emptyList()
    return listOf(KeyElement(part, move.run, move.area) to head)
}

