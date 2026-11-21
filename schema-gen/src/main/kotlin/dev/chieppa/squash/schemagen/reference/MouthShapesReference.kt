package dev.chieppa.squash.schemagen.reference

import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.document.MouthShapeName
import dev.chieppa.squash.document.MouthShapeValues

/** What each spoken shape reads as, and the sounds it stands for. */
private val SHAPE_READS: Map<MouthShapeName, Pair<String, String>> = mapOf(
    MouthShapeName.REST to ("silence" to "the closed lip line"),
    MouthShapeName.MBP to ("M B P" to "lips pressed, a little wider"),
    MouthShapeName.AI to ("A I" to "the jaw open, wide"),
    MouthShapeName.E to ("E" to "half open and wide, the upper teeth showing"),
    MouthShapeName.O to ("O" to "round, the jaw half open"),
    MouthShapeName.U to ("U W Q" to "a small pucker, nearly closed"),
    MouthShapeName.FV to ("F V" to "the lower lip tucked under the upper teeth"),
    MouthShapeName.L to ("L Th D N" to "open, the tongue's tip up to the teeth"),
    MouthShapeName.ETC to ("C D G K S T" to "the teeth together, the lips apart"),
)

/** The spoken shapes, how a style shapes them, and how to preview them. */
internal object MouthShapesReference {
    fun render(mouths: List<MouthStyle>): String = buildString {
        appendLine("### Mouth shapes")
        appendLine()
        appendLine("Nine spoken shapes, each a preset over the seven controls above. A mouth says one in place of its own controls, its own `smile` added on top as a mood, so a mouth can talk while it smiles. A group's `shapes` (`{\"o\": {\"round\": 0.6}}`) change any of its style's; a value left out keeps the style's.")
        appendLine()
        append(table(listOf("shape", "sounds", "reads"), MouthShapeName.entries.map { listOf(code(it.word), SHAPE_READS.getValue(it).first, SHAPE_READS.getValue(it).second) }))
        appendLine()
        appendLine("`set=head:mouth.shape:o` (or `set=head:shape:o` for a part's one mouth) shows every mouth saying a shape without editing the recipe, and `faces=shapes` renders the face close-up saying each of the nine as `<outfit>-face-shape-<shape>.png`, with a contact sheet of them all as `<outfit>-face-shapes.png`.")
        appendLine()
        appendLine("### Mouth styles")
        appendLine()
        appendLine("A mouth talks in the style its group's `style` names, else the one its head template's `mouth` names, else `lips`. A style is a preset file, `templates/mouths/<name>.mouth.json` (schema `schema/mouth-style.schema.json`): where its line runs, how deep it opens, how much each control does on it (`scale`), and its nine shapes. A style whose `speaks` is false holds its shapes as data only: a picked shape leaves its mouth as its own controls draw it, and the Face stage shows it no Shapes strip. A `seam` style, a beak's, parts nothing on the head: its lower half drops on the jaw and shows the inner and tongue roles' fills on its top, the tongue reaching further out with `tongue`. Where the lips part, a mouth is cut into the head as a shallow recess, `carve` of the lip line's width deep at most and never much deeper than the lips part there, its walls in the inner role's fill, darker toward the back, so the side outline shows a notch; `carve` 0, as `painted` has, paints the opening on the surface only.")
        appendLine()
        append(
            table(
                listOf("style", "line", "speaks", "depth", "carve", "scale"),
                mouths.map { style ->
                    val scale = style.scale
                    val scaled = listOf("open" to scale.open, "wide" to scale.wide, "round" to scale.round, "smile" to scale.smile, "upperLip" to scale.upperLip, "lowerLip" to scale.lowerLip, "tongue" to scale.tongue)
                        .filter { it.second != 1f }.joinToString { "${it.first} ${it.second}" }.ifEmpty { "all 1" }
                    listOf(code(style.id), style.line.name.lowercase(), if (style.speaks) "yes" else "no", style.depth.toString(), style.carve.toString(), scaled)
                },
            ),
        )
        appendLine()
        mouths.firstOrNull { it.id == MouthStyle.LIPS }?.let { lips ->
            appendLine("The `lips` style's shapes, which the other presets share before their scales:")
            appendLine()
            append(table(listOf("shape", "open", "wide", "round", "smile", "upperLip", "lowerLip", "tongue"), MouthShapeName.entries.map { listOf(code(it.word)) + values(lips.shape(it)) }))
            appendLine()
        }
    }

    private fun values(shape: MouthShapeValues): List<String> =
        listOf(shape.open, shape.wide, shape.round, shape.smile, shape.upperLip, shape.lowerLip, shape.tongue).map { (it ?: 0f).toString() }
}
