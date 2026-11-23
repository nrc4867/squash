package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.model.TeethPreset
import dev.chieppa.squash.document.Jaw
import dev.chieppa.squash.document.JawTeeth
import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MouthTeeth
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.ToothSlot
import dev.chieppa.squash.document.ToothType

/**
 * A mouth's teeth as it draws them: each jaw, its preset with the mouth's changes on it, and the tooth types its slots name; [builtOpen]
 * when the mouth's jaw is built open, where shaped teeth and a shaped tongue are built as parts rather than painted.
 */
data class MouthDentition(val types: Map<String, ToothType>, val upper: Jaw, val lower: Jaw, val builtOpen: Boolean = false) {
    fun jaw(name: JawName): Jaw = when (name) {
        JawName.UPPER -> upper
        JawName.LOWER -> lower
    }

    /** [slot]'s type with the slot's own settings over it; a type no preset names takes the default tooth. */
    fun toothOf(slot: ToothSlot): ToothType {
        val type = types[slot.type] ?: ToothType()
        return type.copy(width = slot.width ?: type.width, length = slot.length ?: type.length, curve = slot.curve ?: type.curve, tip = slot.tip ?: type.tip)
    }

    /**
     * Where each of [slots]' middles sits along its row, its distance from the mouth's middle as a share of the mouth's width: a slot's own
     * `at`, else its gap and half its width past the outer edge of the slot before it.
     */
    fun middles(slots: List<ToothSlot>): List<Float> {
        var edge = 0f
        return slots.map { slot ->
            val half = toothOf(slot).width / 2f
            val middle = slot.at ?: (edge + slot.gap + half)
            edge = middle + half
            middle
        }
    }

    /** Whether any jaw draws a tooth. */
    val draws: Boolean get() = JawName.entries.any { name -> jaw(name).let { it.level != TeethLevel.NONE && it.rows.any { row -> row.slots.isNotEmpty() } } }

    companion object {
        val NONE = MouthDentition(emptyMap(), Jaw(), Jaw())

        /**
         * [teeth] on [preset]: each jaw with what the mouth changes of it. On a mouth whose jaw is [builtOpen], a jaw the preset puts at
         * blocks and the mouth leaves alone starts at shaped; on any other mouth a jaw at shaped is painted as blocks.
         */
        fun of(preset: TeethPreset, teeth: MouthTeeth, builtOpen: Boolean = false): MouthDentition {
            fun jaw(changes: JawTeeth, base: Jaw): Jaw {
                val shown = changes.on(base)
                val level = when {
                    builtOpen && changes.level == null && base.level == TeethLevel.BLOCKS -> TeethLevel.SHAPED
                    !builtOpen && shown.level == TeethLevel.SHAPED -> TeethLevel.BLOCKS
                    else -> shown.level
                }
                return shown.copy(level = level)
            }
            return MouthDentition(preset.types, jaw(teeth.upper, preset.upper), jaw(teeth.lower, preset.lower), builtOpen)
        }
    }
}

/** The preset [group], talking in [style], starts its teeth from: the one it names, else its style's. */
fun TemplateLibrary.teethPresetOf(group: MarkGroup, style: MouthStyle): TeethPreset = teethPreset(group.teeth.preset ?: style.teeth)

/** [group]'s teeth as it draws them, talking in [style], on a jaw [builtOpen] or not. */
fun TemplateLibrary.dentitionOf(group: MarkGroup, style: MouthStyle, builtOpen: Boolean = false): MouthDentition =
    MouthDentition.of(teethPresetOf(group, style), group.teeth, builtOpen)
