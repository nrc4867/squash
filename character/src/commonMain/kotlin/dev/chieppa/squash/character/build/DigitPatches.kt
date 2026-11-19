package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Digits
import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.model.PartTemplate

/**
 * A digit of a template's row of toes or fingers named by a number a part's parameters can hold: 0 on counts the row's numbered digits
 * from the first, so 2 is the patch `fingers:2`; -1 down counts its named digits in the template's order, so -1 is `fingers:thumb` on a
 * hand. See HLD: data-model, Hand-made sockets.
 */
object DigitPatches {
    /** The patch digit [digit] of [template]'s first row of digits names, or null when it has no row or no such named digit. */
    fun patchOf(template: PartTemplate, digit: Int): String? {
        val row = rowOf(template) ?: return null
        if (digit >= 0) return "${row.name}:$digit"
        val named = template.blocks.filterIsInstance<Digits>().firstOrNull()?.digits?.mapNotNull { it.name }.orEmpty()
        return named.getOrNull(-digit - 1)?.let { "${row.name}:$it" }
    }

    /** The number [patch] goes by among [template]'s digits, or null when it is no digit patch of its first row. */
    fun digitOf(template: PartTemplate, patch: String): Int? {
        val row = rowOf(template) ?: return null
        val tail = patch.takeIf { it.startsWith("${row.name}:") }?.removePrefix("${row.name}:") ?: return null
        tail.toIntOrNull()?.let { return it.takeIf { n -> n >= 0 } }
        val named = template.blocks.filterIsInstance<Digits>().firstOrNull()?.digits?.mapNotNull { it.name }.orEmpty()
        return named.indexOf(tail).takeIf { it >= 0 }?.let { -it - 1 }
    }

    /** The patches [template] builds with [params] over its defaults, its digits among them as its count and flags pick them. */
    fun builtPatches(template: PartTemplate, params: Map<String, Float>): Set<String> {
        val values = template.defaults + params
        return template.blocks.filter { block -> block.condition?.let { values[it.name] != 0f } ?: true }.flatMap { block ->
            block.patchNames().flatMap { name -> if (name.repeats) block.countedPatches(values) else setOf(name.name) }
        }.toSet()
    }

    /** The name of [template]'s first row of digits, the part of a digit patch's name before its colon. */
    private fun rowOf(template: PartTemplate) =
        template.blocks.firstOrNull { it is Digits || it is ToeRow }?.patchNames()?.firstOrNull { it.repeats }
}
