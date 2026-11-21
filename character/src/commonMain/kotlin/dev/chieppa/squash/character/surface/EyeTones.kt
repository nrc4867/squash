package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.SlotName

/**
 * The slots and tints of a painted eye of [look]'s detail: each names its own slot where the look gives one, else takes a fill's slot
 * tinted toward another colour, the iris's darker top toward the pupil's, its crescent toward the white's, the lid's shadow from the white
 * toward a cool grey lilac, and the lid mass and lid marks the ink's.
 */
class EyeTones(look: EyeLook) {
    /** A layer's [slot] and the [tint] it is taken by, none where its slot is its own. */
    data class Tone(val slot: SlotName?, val tint: LayerTint?)

    private val shade = look.irisShade

    private val darker = LayerTint(TintTarget.Slot(look.pupil, TintTarget.Ink), shade.darken)

    val irisUpper: Tone = if (shade.upperSlot != null) Tone(shade.upperSlot, null) else Tone(look.iris, darker)

    val irisLower: Tone = if (shade.lowerSlot != null) {
        Tone(shade.lowerSlot, null)
    } else {
        Tone(look.iris, LayerTint(TintTarget.Slot(look.white, TintTarget.White), shade.lighten))
    }

    val irisRim: Tone = if (shade.rimSlot != null) Tone(shade.rimSlot, null) else irisUpper

    val lidShadow: Tone = look.lidShadow.let { if (it.slot != null) Tone(it.slot, null) else Tone(look.white, LayerTint(TintTarget.LidShade, it.share)) }

    val lid: Tone = inked(look.lidMass.slot ?: look.ink)

    val lidSkin: Tone = Tone(look.lids.slot, null)

    val lidSkinUnder: Tone = Tone(look.lids.fadeTo, null)

    /** Each lid mark's tone, in order: its own slot, else the look's ink. */
    val lidMarks: List<Tone> = look.lids.marks.map { inked(it.slot ?: look.ink) }

    /** [slot] as it is, or with none the ink's colour. */
    private fun inked(slot: SlotName?): Tone = if (slot != null) Tone(slot, null) else Tone(null, LayerTint(TintTarget.Ink, 1f))
}
