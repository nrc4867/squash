package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.SlotName

/** A flat layer's colour taken [share] of the way from its slot's toward [toward]. */
data class LayerTint(val toward: TintTarget, val share: Float)

/** A colour a layer's tint goes toward. */
sealed interface TintTarget {
    /** Slot [name]'s colour, or [orElse] where it names none the palette holds. */
    data class Slot(val name: SlotName?, val orElse: TintTarget) : TintTarget

    /** The ink's colour. */
    data object Ink : TintTarget

    /** Pure white. */
    data object White : TintTarget

    /** The shadow tone of the skin the layer lies on. */
    data object SkinShadow : TintTarget
}
