package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.block.Digits
import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Patch

/** How many times a head's, a hand's or a foot's cells are halved from the body's: the face, the palm and the sole mesh at half the body's cell. */
const val DETAIL_LEVEL = 1

/** How many times a digit's cells are halved from the body's: a toe or finger meshes at a quarter of the body's cell. */
const val DIGIT_LEVEL = 2

/**
 * The roles whose runs mesh at [DETAIL_LEVEL]: where a face's, a hand's or a foot's shape and paint are finer than a limb's or a body's.
 * A paw is a foot, a forepaw as much as a hind one, its pads painted on its sole.
 */
private val DETAIL_ROLES = setOf(Role.HEAD, Role.JAW, Role.HAND, Role.FOOT)

/**
 * The level each member of [field] meshes at (see `CellLevels`): [DIGIT_LEVEL] for a digit, a run of a part's row of toes or fingers,
 * [DETAIL_LEVEL] for every other run of a head, jaw, hand or foot, and the body's own cell for every other run, legs, arms, tails, the
 * torso and hair among them.
 */
fun digitLevels(field: CharacterField): IntArray = IntArray(field.runs.size) { i ->
    val run = field.runs[i]
    when {
        isDigitRun(run.part, run.patch) -> DIGIT_LEVEL
        run.part.role in DETAIL_ROLES -> DETAIL_LEVEL
        else -> 0
    }
}

/** Whether [patch] of [part] is one digit of a row of toes or fingers its template builds. */
fun isDigitRun(part: PlacedPart, patch: Patch): Boolean = part.template.blocks.any { block ->
    (block is Digits || block is ToeRow) && block.patchNames().any { it.repeats && patch.name.startsWith(it.name + ":") }
}
