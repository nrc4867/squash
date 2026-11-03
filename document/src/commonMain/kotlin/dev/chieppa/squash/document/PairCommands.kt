package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ends a mirrored pair: each side keeps what it resolves to as its own values, so nothing it shows changes, and from
 * then on is a part alone.
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
        return Applied(
            document.copy(character = character.copy(parts = parts, pairs = character.pairs - pair)),
            RestorePair(pair, shared, sides.associate { it.id to it.overrides }),
        )
    }
}

/** Makes the parts in [overrides] sides of [pair] again, sharing [shared] under their overrides: the undo of [BreakPair]. */
@Serializable
@SerialName("restorePair")
data class RestorePair(val pair: PairId, val shared: PartData, val overrides: Map<PartId, PartData>) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        require(pair !in character.pairs) { "Pair ${pair.value} already exists" }
        overrides.keys.forEach { document.requirePart(it) }
        val parts = character.parts.map { part ->
            val side = overrides[part.id] ?: return@map part
            part.copy(own = PartData(), overrides = side, pair = pair)
        }
        return Applied(
            document.copy(character = character.copy(parts = parts, pairs = character.pairs + (pair to shared))),
            BreakPair(pair),
        )
    }
}
