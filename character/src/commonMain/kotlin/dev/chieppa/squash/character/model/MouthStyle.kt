package dev.chieppa.squash.character.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MouthShapeName
import dev.chieppa.squash.document.MouthShapeValues
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a mouth's line runs on its head. */
@Serializable
enum class MouthLine {
    /** Across the front of the face, as lips. */
    @SerialName("front") FRONT,

    /** From under the nose back along each side of a muzzle. */
    @SerialName("muzzle") MUZZLE,

    /** The seam between a beak's halves: nothing parts on the surface, the lower half drops on its jaw and shows the tongue on it. */
    @SerialName("seam") SEAM,
}

/** How much each control does on a mouth of a style: 1 as the controls say, 0 nothing. */
@Serializable
@Doc("How much each control does on this mouth: 1 in full, 0 nothing.")
data class MouthScales(
    @Range(min = 0.0, max = 2.0) val open: Float = 1f,
    @Range(min = 0.0, max = 2.0) val wide: Float = 1f,
    @Range(min = 0.0, max = 2.0) val round: Float = 1f,
    @Range(min = 0.0, max = 2.0) val smile: Float = 1f,
    @Range(min = 0.0, max = 2.0) val upperLip: Float = 1f,
    @Range(min = 0.0, max = 2.0) val lowerLip: Float = 1f,
    @Range(min = 0.0, max = 2.0) val tongue: Float = 1f,
) {
    /** [controls] as a mouth of this scale does them. */
    fun of(controls: MarkControls): MarkControls = controls.copy(
        open = (controls.open * open).coerceIn(0f, 1f),
        wide = (controls.wide * wide).coerceIn(-1f, 1f),
        round = (controls.round * round).coerceIn(0f, 1f),
        smile = (controls.smile * smile).coerceIn(-1f, 1f),
        upperLip = (controls.upperLip * upperLip).coerceIn(0f, 1f),
        lowerLip = (controls.lowerLip * lowerLip).coerceIn(-1f, 1f),
        tongue = (controls.tongue * tongue).coerceIn(0f, 1f),
    )
}

/**
 * A mouth style, a preset a head template names (`templates/mouths/<id>.mouth.json`): where its line runs, how deep it opens and is carved, how much each control does
 * on it, and the nine spoken shapes it says. A mouth group may name another; its own shapes override the style's. See HLD: look, Mouth.
 */
@Serializable
@Doc("A mouth style: where the line runs, how deep it opens, how much each control does, and the nine spoken shapes.")
data class MouthStyle(
    @SerialName("\$schema")
    val schemaPath: String? = null,
    @Doc("The grammar version of the file.") @Range(min = 1.0, max = 1.0)
    val format: Int = 1,
    @Doc("The name a template's `mouth` and a group's `style` use.")
    val id: String,
    val name: String = id,
    @Doc("Where the mouth line runs: front (lips), muzzle (from under the nose back along its sides) or seam (between a beak's halves, which only the jaw opens).")
    val line: MouthLine = MouthLine.FRONT,
    @Doc("How deep a fully open mouth drops its lower lip at its middle, a share of the lip line's width.") @Range(min = 0.05, max = 1.0)
    val depth: Float = DEFAULT_DEPTH,
    @Doc("How deep the head is cut in behind the opening where the lips part, at most, a share of the lip line's width; 0 paints the opening on the surface only.") @Range(min = 0.0, max = 1.0)
    val carve: Float = DEFAULT_CARVE,
    @Doc("How much of the lip line parts, a share of it either side of its middle: 1 for lips, less for a muzzle, whose line runs shut back along its sides.") @Range(min = 0.1, max = 1.0)
    val span: Float = 1f,
    @Doc("How much each control does on this mouth.")
    val scale: MouthScales = MouthScales(),
    @Doc("How far each control may go on this mouth after its scale, so it stays whole from every view; a character's own style may loosen them.")
    val limits: MouthLimits = MouthLimits(),
    @Doc("How thick the upper lip's band is at its middle, a share of the lip line's width, where the lip line's role has a fill.") @Range(min = 0.0, max = 0.5)
    val upperLip: Float = DEFAULT_UPPER_LIP,
    @Doc("How thick the lower lip's band is at its middle, a share of the lip line's width, where the lip line's role has a fill.") @Range(min = 0.0, max = 0.5)
    val lowerLip: Float = DEFAULT_LOWER_LIP,
    @Doc("Whether the spoken shapes move this mouth: false keeps its mouth as its own controls draw it whatever shape is picked, its shapes held as data only.")
    val speaks: Boolean = true,
    @Doc("The nine spoken shapes; a shape left out says nothing but rest.")
    val shapes: Map<MouthShapeName, MouthShapeValues> = emptyMap(),
    @Doc("The teeth preset a mouth of this style starts from (templates/teeth/<name>.teeth.json); none for no teeth.")
    val teeth: String? = null,
    @Doc("The widest a head of this style opens its jaw, in degrees, in place of its template's jaw joint limit; none keeps the template's.") @Range(min = 0.0, max = 60.0)
    val jaw: Float? = null,
) {
    /** [controls] as a mouth of this style does them: scaled, then held to its limits. */
    fun controlsOf(controls: MarkControls): MarkControls = limits.of(scale.of(controls))

    /** What [shape] sets on this style, under [override]. */
    fun shape(shape: MouthShapeName, override: MouthShapeValues? = null): MouthShapeValues = (shapes[shape] ?: MouthShapeValues()).under(override)

    companion object {
        /** How deep a fully open mouth drops at its middle by default, a share of its width. */
        const val DEFAULT_DEPTH = 0.45f

        /** How deep a mouth is cut in behind its opening by default, a share of its width. */
        const val DEFAULT_CARVE = 0.2f

        /** How thick the lips' bands are at their middles by default, shares of the mouth's width. */
        const val DEFAULT_UPPER_LIP = 0.13f
        const val DEFAULT_LOWER_LIP = 0.18f

        /** The style a mouth takes when neither its group nor its head names one that is loaded. */
        const val LIPS = "lips"

        /** What a mouth with no loaded style talks in: lips, opening as deep as any, saying only rest. */
        val PLAIN = MouthStyle(id = LIPS)
    }
}
