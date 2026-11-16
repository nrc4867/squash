package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.block.Digits
import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Patch

/** How many times a digit's cells are halved from the body's: a toe or finger meshes at a quarter of the body's cell. */
const val DIGIT_LEVEL = 2

/**
 * The level each member of [field] meshes at (see `CellLevels`): [DIGIT_LEVEL] for a digit, a run of a part's row of toes or fingers,
 * and the body's own cell for every other run, palms, whole feet and hair among them.
 */
fun digitLevels(field: CharacterField): IntArray = IntArray(field.runs.size) { i ->
    val run = field.runs[i]
    if (isDigitRun(run.part, run.patch)) DIGIT_LEVEL else 0
}

/** Whether [patch] of [part] is one digit of a row of toes or fingers its template builds. */
fun isDigitRun(part: PlacedPart, patch: Patch): Boolean = part.template.blocks.any { block ->
    (block is Digits || block is ToeRow) && block.patchNames().any { it.repeats && patch.name.startsWith(it.name + ":") }
}
