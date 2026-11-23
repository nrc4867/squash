package dev.chieppa.squash.schemagen.reference

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.model.TeethPreset
import dev.chieppa.squash.document.Jaw
import dev.chieppa.squash.document.JawTeeth
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.ToothSlot
import dev.chieppa.squash.document.ToothType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor

/** A mouth's teeth and tongue: the dentition model, its levels, what a group may change, and the shipped presets. */
internal object TeethReference {
    fun render(mouths: List<MouthStyle>, presets: List<TeethPreset>): String = buildString {
        appendLine("### Teeth")
        appendLine()
        appendLine("A mouth's teeth come from a preset, `templates/teeth/<name>.teeth.json` (schema `schema/teeth-preset.schema.json`): tooth types by name, and an upper and a lower jaw, each with a level and rows of slots from the middle outward, mirrored to the other side unless a slot names one. The upper jaw hangs from the lip line and stays as the mouth opens; the lower stands on the jaw and drops with it. Teeth fill in the teeth role's `fill` and are inked where its `ink` is; with no fill none show. A group's `teeth` names the preset (`preset`, else its style's `teeth`) and keeps only what it changes, so a later change to the preset still reaches it:")
        appendLine()
        appendLine("```json")
        appendLine("\"teeth\": {\"preset\": \"cat\", \"upper\": {\"slots\": [{\"row\": 0, \"slot\": 3, \"length\": 0.24, \"over\": true}]}}")
        appendLine("```")
        appendLine()
        append(table(listOf("level", "draws"), TeethLevel.entries.map { listOf(code(it.name.lowercase()), levelMeaning(it)) }))
        appendLine()
        appendLine("Sizes are shares of the mouth's width. A tooth reaches a little past its jaw's line, hidden under the lip until the lip lifts off it. A slot whose `over` is set shows over the lips, open or shut, and breaks their ink: an upper one hangs below the upper lip, a lower one stands above the lower lip. Rows after a jaw's first sit behind it, a little narrower and shorter.")
        appendLine()
        append(table(listOf("field", "meaning"), docs("jaw", JawTeeth.serializer().descriptor) + docs("slot", ToothSlot.serializer().descriptor) + docs("type", ToothType.serializer().descriptor)))
        appendLine()
        appendLine("A change in a jaw's `slots` (`{\"row\", \"slot\", ...}`) takes any slot field, and `removed` takes the slot out; its row and slot count the preset's first, then those added.")
        appendLine()
        appendLine("#### Teeth presets")
        appendLine()
        val defaults = mouths.filter { it.teeth != null }.groupBy({ it.teeth!! }, { it.id })
        append(
            table(
                listOf("preset", "upper", "lower", "over", "styles"),
                presets.sortedBy { it.id }.map { preset ->
                    listOf(code(preset.id), jawSummary(preset.upper), jawSummary(preset.lower), overSummary(preset), defaults[preset.id].orEmpty().joinToString { code(it) })
                },
            ),
        )
        appendLine()
        appendLine("### Tongue")
        appendLine()
        appendLine("A group's `tongueLevel` is `flat`, its tongue marks or, with none and a filled tongue role, a plain tongue as it lifts; `shaped`, on a head whose jaw is built open or a beak's lower half, a lofted tongue on the jaw's floor that the `tongue` control curls up toward the upper teeth by 0.5 and reaches out past the lower lip by 1, its tip `tongueTip`: `round`, `groove` or `fork` (flat on any other mouth); or `none`, no tongue at all, on a beak too. A shaped tongue is built only where the tongue role has a fill.")
        appendLine()
    }

    private fun levelMeaning(level: TeethLevel) = when (level) {
        TeethLevel.NONE -> "no teeth"
        TeethLevel.BAR -> "each row as one band from its first slot to its last, as long at each slot as its tooth; slots flagged `over` still show on their own"
        TeethLevel.BLOCKS -> "each slot its own tooth, cut to its type's `tip`"
        TeethLevel.SHAPED -> "each slot's tooth built as a small hard part on a head whose jaw is built open, in the teeth role's slot, plastic where it sets no material; a preset jaw at `blocks` starts here on such a head; painted as blocks on any other mouth"
    }

    private fun jawSummary(jaw: Jaw): String {
        if (jaw.rows.all { it.slots.isEmpty() }) return "none"
        val rows = jaw.rows.joinToString(" / ") { row -> runs(row.slots.map { it.type }) }
        return "${jaw.level.name.lowercase()}: $rows"
    }

    /** [types] as runs: `3 incisor, canine`. */
    private fun runs(types: List<String>): String = buildList {
        var i = 0
        while (i < types.size) {
            var j = i
            while (j < types.size && types[j] == types[i]) j++
            add(if (j - i > 1) "${j - i} ${types[i]}" else types[i])
            i = j
        }
    }.joinToString()

    private fun overSummary(preset: TeethPreset): String = listOf("upper" to preset.upper, "lower" to preset.lower).mapNotNull { (name, jaw) ->
        val over = jaw.rows.flatMap { row -> row.slots.withIndex().filter { it.value.over }.map { it.index } }
        if (over.isEmpty()) null else "$name ${over.joinToString()}"
    }.joinToString("; ")

    @OptIn(ExperimentalSerializationApi::class)
    private fun docs(owner: String, descriptor: SerialDescriptor): List<List<String>> = (0 until descriptor.elementsCount).mapNotNull { i ->
        val doc = descriptor.getElementAnnotations(i).filterIsInstance<Doc>().firstOrNull()?.text ?: return@mapNotNull null
        listOf("$owner ${code(descriptor.getElementName(i))}", doc)
    }
}
