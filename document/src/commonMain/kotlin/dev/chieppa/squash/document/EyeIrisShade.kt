package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/**
 * An iris in cel tones: its top [upper] of its height down in [upperSlot], or with none its colour [darken] of the way to the pupil's,
 * its lower edge hard or [soft]; a crescent along its bottom [lower] of its height up at its middle and thinning to nothing at its horns,
 * below the iris's middle, in [lowerSlot], or with none its colour [lighten] of the way to the white's; and a ring round its edge [rim] of
 * its half width wide in [rimSlot], or with none the upper shade's colour. With both a darker top and a crescent, the top reaches down to
 * the crescent, so the iris shows two tones.
 */
@Serializable
@Doc("The iris shaded in cel tones: a darker top melting into the pupil, a lighter crescent along its bottom and a darker rim; each off at 0.")
data class EyeIrisShade(
    @Doc("How far down the darker top reaches, a share of the iris's height, where there is no crescent; with one it reaches down to the crescent. 0 none.") @Range(min = 0.0, max = 1.0)
    val upper: Float = 0f,
    @Doc("The darker top's palette slot; left out, the iris's colour taken darken of the way to the pupil's.")
    val upperSlot: SlotName? = null,
    @Doc("How far the darker top's colour goes from the iris's toward the pupil's, when it names no slot.") @Range(min = 0.0, max = 1.0)
    val darken: Float = 0.5f,
    @Doc("How soft the darker top's lower edge is, a share of the iris's half height: 0 a hard cel edge.") @Range(min = 0.0, max = 1.0)
    val soft: Float = 0f,
    @Doc("How far up the lighter crescent along the iris's bottom reaches at its middle, a share of the iris's height; it thins to nothing below the middle. 0 none.") @Range(min = 0.0, max = 1.0)
    val lower: Float = 0f,
    @Doc("The crescent's palette slot; left out, the iris's colour taken lighten of the way to the white's.")
    val lowerSlot: SlotName? = null,
    @Doc("How far the crescent's colour goes from the iris's toward the white's, when it names no slot.") @Range(min = 0.0, max = 1.0)
    val lighten: Float = 0.45f,
    @Doc("The width of a darker ring round the iris's edge, a share of its half width. 0 none.") @Range(min = 0.0, max = 0.5)
    val rim: Float = 0f,
    @Doc("The ring's palette slot; the darker top's colour when left out.")
    val rimSlot: SlotName? = null,
)
