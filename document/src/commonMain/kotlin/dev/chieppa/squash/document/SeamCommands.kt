package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Seam strokes live in the surface work of the part whose seam with its parent they paint, and follow its rules: each command
 * writes the pair for a paired part, or with thisSide that side's override alone. See HLD: data-model, The rig.
 */

/** Adds, replaces or removes the seam stroke [id] on [part]'s seam; anchors as [SetBrushStroke] takes them. */
@Serializable
@SerialName("setSeamStroke")
data class SetSeamStroke(
    val part: PartId,
    val id: SeamStrokeId,
    val stroke: SeamStroke?,
    val thisSide: Boolean = false,
    val index: Int? = null,
    val stored: Boolean = false,
) : DocumentCommand {
    init {
        require(stroke == null || stroke.id == id) { "Seam stroke ${stroke?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide).surface
        val written = stroke?.mapAnchors(document.character.writeTransform(instance, thisSide, stored))
        val old = before.seams.locate { it.id == id }
        val seams = before.seams.withItem(written, index) { it.id == id }
        return Applied(
            document.withSeams(instance, thisSide, seams),
            SetSeamStroke(part, id, old.value, thisSide, old.restoreIndex, stored = true),
        )
    }
}

/** One place's seam strokes as it holds them: [part]'s pair, or with [thisSide] its side's overrides, or its own when unpaired. */
@Serializable
data class SeamWrite(val part: PartId, val thisSide: Boolean, val strokes: List<SeamStroke>)

/** Sets the seam strokes of each place in [writes], in order, as they are held: the undo of the seam commands that write several. */
@Serializable
@SerialName("putSeamStrokes")
data class PutSeamStrokes(val writes: List<SeamWrite>) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        var next = document
        val undo = ArrayList<SeamWrite>(writes.size)
        for (write in writes) {
            val instance = next.requirePart(write.part)
            undo += SeamWrite(write.part, write.thisSide, next.writtenData(instance, write.thisSide).surface.seams)
            next = next.withSeams(instance, write.thisSide, write.strokes)
        }
        return Applied(next, PutSeamStrokes(undo.asReversed().toList()))
    }
}

/**
 * Removes the strokes of [part]'s seam: with [thisSide] that side's own, otherwise the pair's and both sides' overrides, so the
 * seam shows its guessed band again.
 */
@Serializable
@SerialName("resetSeam")
data class ResetSeam(val part: PartId, val thisSide: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val writes = when {
            instance.pair == null -> listOf(SeamWrite(part, false, emptyList()))
            thisSide -> listOf(SeamWrite(part, true, emptyList()))
            else -> listOfNotNull(instance, document.character.twinOf(instance)).map { SeamWrite(it.id, true, emptyList()) } +
                SeamWrite(part, false, emptyList())
        }
        return PutSeamStrokes(writes).apply(document)
    }
}

/** Copies the strokes [part]'s seam shows to its twin's seam, mirrored, as the twin's overrides, so both sides show the same. */
@Serializable
@SerialName("seamToTwin")
data class SeamToTwin(val part: PartId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val instance = document.requirePart(part)
        val twin = requireNotNull(character.twinOf(instance)) { "Part ${part.value} has no twin" }
        val mirrored = character.resolved(instance).surface.seams.map { it.mapAnchors(character::mirroredAnchor) }
        return PutSeamStrokes(listOf(SeamWrite(twin.id, true, mirrored))).apply(document)
    }
}

/** Adds [strokes] to [part]'s seam as one step, after those it holds, as Tidy hands stray patches over; anchors as [SetSeamStroke] takes them. */
@Serializable
@SerialName("addSeamStrokes")
data class AddSeamStrokes(val part: PartId, val strokes: List<SeamStroke>, val thisSide: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val transform = document.character.writeTransform(instance, thisSide, stored = false)
        val held = document.writtenData(instance, thisSide).surface.seams
        return PutSeamStrokes(listOf(SeamWrite(part, thisSide, held + strokes.map { it.mapAnchors(transform) }))).apply(document)
    }
}

/** The seam strokes [part] shows, or none when it is not in this character. */
fun Character.seamStrokes(part: PartId): List<SeamStroke> = resolved(part)?.surface?.seams.orEmpty()

private fun SquashDocument.withSeams(part: PartInstance, thisSide: Boolean, seams: List<SeamStroke>): SquashDocument {
    val data = writtenData(part, thisSide)
    return withWritten(part, thisSide, data.copy(surface = data.surface.copy(seams = seams)))
}
