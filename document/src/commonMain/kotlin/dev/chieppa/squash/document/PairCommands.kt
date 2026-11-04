package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ends a mirrored pair: each side keeps what it resolves to as its own values, so nothing it shows changes, and from
 * then on is a part alone. An expression's marks for the pair's groups go to each side the same way.
 */
@Serializable
@SerialName("breakPair")
data class BreakPair(val pair: PairId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val shared = requireNotNull(character.pairs[pair]) { "No pair ${pair.value}" }
        val sides = character.parts.filter { it.pair == pair }
        val parts = character.parts.map {
            if (it.pair == pair) it.copy(own = character.resolved(it), overrides = PartData(), pair = null) else it
        }
        val groups = sides.flatMapTo(mutableSetOf()) { side -> character.resolved(side).surface.groups.map { it.id } }
        val held = character.expressions.associate { set -> set.id to set.marks.filterKeys { it in groups } }.filterValues { it.isNotEmpty() }
        val expressions = character.expressions.map { set -> set.copy(marks = set.marks + character.splitOntoSides(held[set.id].orEmpty(), sides)) }
        return Applied(
            document.copy(character = character.copy(parts = parts, pairs = character.pairs - pair, expressions = expressions)),
            RestorePair(pair, shared, sides.associate { it.id to it.overrides }, held),
        )
    }
}

/**
 * Makes the parts in [overrides] sides of [pair] again, sharing [shared] under their overrides and holding [expressions] as
 * their groups' marks: the undo of [BreakPair].
 */
@Serializable
@SerialName("restorePair")
data class RestorePair(
    val pair: PairId,
    val shared: PartData,
    val overrides: Map<PartId, PartData>,
    val expressions: Map<ExpressionId, Map<MarkGroupId, RoleMarks>> = emptyMap(),
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        require(pair !in character.pairs) { "Pair ${pair.value} already exists" }
        overrides.keys.forEach { document.requirePart(it) }
        val parts = character.parts.map { part ->
            val side = overrides[part.id] ?: return@map part
            part.copy(own = PartData(), overrides = side, pair = pair)
        }
        val sets = character.expressions.map { it.copy(marks = it.marks + expressions[it.id].orEmpty()) }
        return Applied(
            document.copy(character = character.copy(parts = parts, pairs = character.pairs + (pair to shared), expressions = sets)),
            BreakPair(pair),
        )
    }
}

/** These groups' marks with nothing shared left: each of [sides] holds what it shows. */
private fun Character.splitOntoSides(marks: Map<MarkGroupId, RoleMarks>, sides: List<PartInstance>): Map<MarkGroupId, RoleMarks> =
    marks.mapValues { (_, entry) ->
        sides.fold(entry.copy(shared = emptyMap())) { split, side ->
            split.withSide(checkNotNull(side.side) { "A paired part has a side" }, expressionMarks(entry, side))
        }
    }
