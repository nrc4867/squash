package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Adds, replaces or removes the line weight [id], as [SetMark] does a mark. */
@Serializable
@SerialName("setLineWeight")
data class SetLineWeight(
    val part: PartId,
    val id: LineWeightId,
    val weight: LineWeight?,
    val thisSide: Boolean = false,
    val index: Int? = null,
    val stored: Boolean = false,
) : DocumentCommand {
    init {
        require(weight == null || weight.id == id) { "Line weight ${weight?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val data = document.writtenData(instance, thisSide)
        val before = data.surface
        val written = weight?.mapAnchors(document.character.writeTransform(instance, thisSide, stored))
        val old = before.lineWeights.locate { it.id == id }
        val weights = before.lineWeights.withItem(written, index) { it.id == id }
        return Applied(
            document.withWritten(instance, thisSide, data.copy(surface = before.copy(lineWeights = weights))),
            SetLineWeight(part, id, old.value, thisSide, old.restoreIndex, stored = true),
        )
    }
}

/** Sets how much the line weight [id] that [part] shows adds; with [thisSide] the side takes its own copy of it. */
@Serializable
@SerialName("setLineWeightAmount")
data class SetLineWeightAmount(
    val part: PartId,
    val id: LineWeightId,
    val amount: Float,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val shown = document.character.editBase(instance, thisSide).surface.lineWeights.firstOrNull { it.id == id }
        val weight = requireNotNull(shown) { "No line weight ${id.value} on ${part.value}" }
        return SetLineWeight(part, id, weight.copy(amount = amount), thisSide).apply(document)
    }
}

/** Sets what weighs the character's lines. */
@Serializable
@SerialName("setLineRules")
data class SetLineRules(val rules: LineRules) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = Applied(
        document.copy(character = document.character.copy(lineRules = rules)),
        SetLineRules(document.character.lineRules),
    )
}
