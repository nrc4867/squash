package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The nine spoken mouth shapes, each named for the sounds it reads as. Smile and frown are a mood on top, not shapes. */
@Serializable
enum class MouthShapeName {
    /** Silence: the closed lip line. */
    @SerialName("rest") REST,

    /** M, B and P: lips pressed, a little wider. */
    @SerialName("mbp") MBP,

    /** A and I: the jaw open, wide. */
    @SerialName("ai") AI,

    /** E: half open and wide, the upper teeth showing. */
    @SerialName("e") E,

    /** O: round, the jaw half open. */
    @SerialName("o") O,

    /** U, W and Q: a small pucker, nearly closed. */
    @SerialName("u") U,

    /** F and V: the lower lip tucked under the upper teeth. */
    @SerialName("fv") FV,

    /** L, Th, D and N: open, the tongue's tip up to the teeth. */
    @SerialName("l") L,

    /** C, D, G, K, S and T: the teeth together, the lips apart. */
    @SerialName("etc") ETC,
    ;

    /** The name a recipe and the `set=` option use. */
    val word: String get() = name.lowercase()

    companion object {
        /** The shape [word] names, or null. */
        fun of(word: String): MouthShapeName? = entries.firstOrNull { it.word == word }
    }
}

/**
 * A spoken shape's controls, each left out to take what is under it: the style's value under a group's override, 0 under a style's.
 * See [MarkControls] for what each does.
 */
@Serializable
@Doc("A spoken shape's controls; each left out takes the style's value, or 0 in a style.")
data class MouthShapeValues(
    @Doc("Jaw and lip gap.") @Range(min = 0.0, max = 1.0)
    val open: Float? = null,
    @Doc("Corners out or in.") @Range(min = -1.0, max = 1.0)
    val wide: Float? = null,
    @Doc("Pucker: a narrow, round opening.") @Range(min = 0.0, max = 1.0)
    val round: Float? = null,
    @Doc("Corners up or down, added to the mouth's own smile.") @Range(min = -1.0, max = 1.0)
    val smile: Float? = null,
    @Doc("Raises the upper lip to show the upper teeth.") @Range(min = 0.0, max = 1.0)
    val upperLip: Float? = null,
    @Doc("Tucks under the teeth (1) or presses (-1).") @Range(min = -1.0, max = 1.0)
    val lowerLip: Float? = null,
    @Doc("Lifts the tongue to the teeth, then out past the lip.") @Range(min = 0.0, max = 1.0)
    val tongue: Float? = null,
) {
    /** [over]'s values where it sets them, this one's elsewhere. */
    fun under(over: MouthShapeValues?): MouthShapeValues = if (over == null) this else MouthShapeValues(
        open = over.open ?: open,
        wide = over.wide ?: wide,
        round = over.round ?: round,
        smile = over.smile ?: smile,
        upperLip = over.upperLip ?: upperLip,
        lowerLip = over.lowerLip ?: lowerLip,
        tongue = over.tongue ?: tongue,
    )

    val isEmpty: Boolean get() = this == MouthShapeValues()
}
