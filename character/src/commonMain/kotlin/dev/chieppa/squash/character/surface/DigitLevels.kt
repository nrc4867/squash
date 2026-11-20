package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.block.Digits
import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Patch

/** How many times a head's, an ear's, a hand's or a foot's cells are halved from the body's: the face, the ears, the palm and the sole mesh at half the body's cell. */
const val DETAIL_LEVEL = 1

/** How many times a digit's cells are halved from the body's: a toe or finger meshes at a quarter of the body's cell. */
const val DIGIT_LEVEL = 2

/**
 * The roles whose runs mesh at [DETAIL_LEVEL]: where a face's, an ear's, a hand's or a foot's shape and paint are finer than a limb's or a
 * body's. A paw is a foot, a forepaw as much as a hind one, its pads painted on its sole; an ear in the field has a rim and a hollow.
 */
private val DETAIL_ROLES = setOf(Role.HEAD, Role.JAW, Role.EAR, Role.HAND, Role.FOOT)

/**
 * The level each member of [field] meshes at (see `CellLevels`): [DIGIT_LEVEL] for a digit, a run of a part's row of toes or fingers, or
 * a pocket's lining, whose rim and hollow are smaller than the part it is cut into,
 * [DETAIL_LEVEL] for every other run of a head, jaw, ear, hand or foot, and the body's own cell for every other run, legs, arms, tails, the
 * torso and hair among them, but a tail or a horn (an antler, a trunk) whose tip is thinner than [THIN_TIP_CELLS] of the body's cells across,
 * at [DETAIL_LEVEL], so its round end, a hook at it and a branch on it are several cells across; and a hand's or a foot's own run whose far
 * end is that thin at [DETAIL_LEVEL]'s cell, at [DIGIT_LEVEL], so a slim drawn toe cap keeps a smooth outline.
 */
fun digitLevels(field: CharacterField): IntArray = IntArray(field.runs.size) { i ->
    val run = field.runs[i]
    when {
        isDigitRun(run.part, run.patch) || (run.first && pocketOf(run.part) != null) -> DIGIT_LEVEL
        run.part.role in END_ROLES && thinEnd(run, field.cell / (1 shl DETAIL_LEVEL)) -> DIGIT_LEVEL
        run.part.role in DETAIL_ROLES || (run.part.role in THIN_TIP_ROLES && thinTip(run, field.cell)) -> DETAIL_LEVEL
        else -> 0
    }
}

/** The roles a thin tip meshes finer for. */
private val THIN_TIP_ROLES = setOf(Role.TAIL, Role.HORN)

/** How many body cells across a tail's ring before its end closes must be, at least, to mesh at the body's cell. */
private const val THIN_TIP_CELLS = 3f

/** Where along a tail its tip is measured, before its end closes. */
private const val TIP_U = 0.8f

/** Whether [run]'s ring at [TIP_U] is thinner than [THIN_TIP_CELLS] cells of [cell] across. */
private fun thinTip(run: FieldRun, cell: Float): Boolean {
    val slices = run.patch.slices ?: return false
    val tip = slices.minByOrNull { kotlin.math.abs(it.u - TIP_U) } ?: return false
    return minOf(tip.height, tip.width) < THIN_TIP_CELLS * cell
}

/** The roles whose own run meshes at [DIGIT_LEVEL] where its far end is thin: a toe cap or a mitten drawn to a slim end. */
private val END_ROLES = setOf(Role.HAND, Role.FOOT)

/** The share of a run's rings, from its far end, whose thickness says whether that end is thin. */
private const val END_RING_SHARE = 0.2f

/**
 * Whether [run]'s far end is thinner than [THIN_TIP_CELLS] of [cell] across at its thickest among its last [END_RING_SHARE] of rings,
 * so a drawn toe end narrower than a few cells of its own level meshes finer and keeps a smooth outline.
 */
private fun thinEnd(run: FieldRun, cell: Float): Boolean {
    val slices = run.patch.slices ?: return false
    val from = (slices.size * (1f - END_RING_SHARE)).toInt()
    val thickest = slices.drop(from).maxOfOrNull { minOf(it.height, it.width) } ?: return false
    return thickest < THIN_TIP_CELLS * cell
}

/** Whether [patch] of [part] is one digit of a row of toes or fingers its template builds. */
fun isDigitRun(part: PlacedPart, patch: Patch): Boolean = part.template.blocks.any { block ->
    (block is Digits || block is ToeRow) && block.patchNames().any { it.repeats && patch.name.startsWith(it.name + ":") }
}
