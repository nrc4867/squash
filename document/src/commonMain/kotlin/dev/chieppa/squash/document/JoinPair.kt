package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Makes two unpaired parts the sides of a new [pair], as one step: [left] shares its outlines, parameters and surface work with
 * [right], which becomes the right side on [socket] with the left's role. What the right side held of its own that differs from
 * the left's stays as its overrides, so nothing it held is lost. See HLD: data-model, Pairs by hand.
 */
@Serializable
@SerialName("joinPair")
data class JoinPair(val pair: PairId, val left: PartId, val right: PartId, val socket: SocketRef?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        require(pair !in character.pairs) { "Pair ${pair.value} already exists" }
        require(left != right) { "A part cannot pair with itself" }
        val leftPart = document.requirePart(left)
        val rightPart = document.requirePart(right)
        require(leftPart.pair == null && rightPart.pair == null) { "Only unpaired parts join a pair" }
        val shared = leftPart.own
        val parts = character.parts.map {
            when (it.id) {
                left -> it.copy(own = PartData(), pair = pair, side = Side.LEFT)
                right -> it.copy(
                    own = PartData(), pair = pair, side = Side.RIGHT, overrides = rightPart.own.over(shared), socket = socket,
                    role = leftPart.role, roleGuessed = leftPart.roleGuessed,
                )
                else -> it
            }
        }
        return Applied(
            document.copy(character = character.copy(parts = parts, pairs = character.pairs + (pair to shared))),
            SplitPair(pair, leftPart, rightPart),
        )
    }
}

/** Puts [left] and [right] back as they were before they joined [pair], and drops the pair: the undo of [JoinPair]. */
@Serializable
@SerialName("splitPair")
data class SplitPair(val pair: PairId, val left: PartInstance, val right: PartInstance) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        require(pair in character.pairs) { "No pair ${pair.value}" }
        val parts = character.parts.map {
            when (it.id) {
                left.id -> left
                right.id -> right
                else -> it
            }
        }
        return Applied(
            document.copy(character = character.copy(parts = parts, pairs = character.pairs - pair)),
            JoinPair(pair, left.id, right.id, character.part(right.id)?.socket),
        )
    }
}

/** What of these values differs from [shared]'s: each outline, parameter and surface item that is not the same there. */
private fun PartData.over(shared: PartData): PartData = PartData(
    outlines.filter { (view, curve) -> shared.outlines[view] != curve },
    params.filter { (name, value) -> shared.params[name] != value },
    if (surface == shared.surface) SurfaceWork() else surface,
)

/**
 * Gives [part], and the other side of its pair, [template] in place of the template it pins, as one step. Its outlines, parameters,
 * surface work and role stay as they are, so the drawing carries over. See HLD: parts, Re-templating keeps the drawing.
 */
@Serializable
@SerialName("setTemplate")
data class SetTemplate(val part: PartId, val template: TemplateRef) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        return Applied(document.withTemplate(part, template), SetTemplate(part, instance.template))
    }
}
