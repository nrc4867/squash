package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.model.TeethPreset
import dev.chieppa.squash.document.Jaw
import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MouthTeeth
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.ToothSlot
import dev.chieppa.squash.document.ToothType

/** A mouth's teeth as it draws them: each jaw, its preset with the mouth's changes on it, and the tooth types its slots name. */
data class MouthDentition(val types: Map<String, ToothType>, val upper: Jaw, val lower: Jaw) {
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

        /** [teeth] on [preset]: each jaw with what the mouth changes of it. */
        fun of(preset: TeethPreset, teeth: MouthTeeth): MouthDentition =
            MouthDentition(preset.types, teeth.upper.on(preset.upper), teeth.lower.on(preset.lower))
    }
}

/** The preset [group], talking in [style], starts its teeth from: the one it names, else its style's. */
fun TemplateLibrary.teethPresetOf(group: MarkGroup, style: MouthStyle): TeethPreset = teethPreset(group.teeth.preset ?: style.teeth)

/** [group]'s teeth as it draws them, talking in [style]. */
fun TemplateLibrary.dentitionOf(group: MarkGroup, style: MouthStyle): MouthDentition = MouthDentition.of(teethPresetOf(group, style), group.teeth)
