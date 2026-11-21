package dev.chieppa.squash.schemagen.reference

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.hair.HairPreset
import dev.chieppa.squash.character.hair.MASS_TEMPLATE
import dev.chieppa.squash.character.hair.PresetGroup
import dev.chieppa.squash.character.hair.fittedMassShape
import dev.chieppa.squash.character.hair.presetFlow
import dev.chieppa.squash.character.hair.presetLayout
import dev.chieppa.squash.character.hair.presetMassed
import dev.chieppa.squash.character.hair.presetShell
import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.Hairline
import dev.chieppa.squash.document.LockTrim
import dev.chieppa.squash.document.ShellFade
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Cowlick
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.LockSection
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Parting
import dev.chieppa.squash.document.PartingKind
import dev.chieppa.squash.document.ScalpFlow
import dev.chieppa.squash.document.Whorl
import dev.chieppa.squash.recipe.model.RecipeHairOutline
import dev.chieppa.squash.recipe.model.RecipeOutlineView
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

/** Where each kind of lock group roots, and which way it grows by its kind alone. */
private val KINDS: Map<LockGroupKind, String> = mapOf(
    LockGroupKind.FRINGE to "across the front of the top, high enough that its locks lie down over the mass's hairline; the locks over the eyes are shorter, so the fringe parts to show them; grows down over the brow",
    LockGroupKind.LEFT to "along the character's left side in two staggered rows; grows down the head",
    LockGroupKind.RIGHT to "along the character's right side in two staggered rows; grows down the head",
    LockGroupKind.BACK to "over the back in up to three staggered rows, the lower rows shorter so the rows end together; grows down the head",
    LockGroupKind.CROWN to "over a disc round the crown, laid out by the golden angle; grows out of the crown's middle, swirling round it",
    LockGroupKind.STRAYS to "round the top, wandering up and down; grows down the head",
    LockGroupKind.TOP to "in two runs from behind the hairline back to the crown, one either side of the parting (or of the head's middle with none), each in two rows once it holds six locks or more; grows down the head, and away from a parting beside it",
)

private val SECTIONS: Map<LockSection, String> = mapOf(
    LockSection.RIBBON to "a flattened lens cupped round the head: smooth, straight or wavy hair, a bob, long hair",
    LockSection.CLUMP to "a rounded egg: spiky, short or curly hair, tufts",
    LockSection.BRAID to "three round strands plaited along the lock, each crossing over the middle in turn, tapering to a tie with a short loose tail past it, the tie in the group's `band` slot where it has one: a braid, twin braids",
)

private val TIPS: Map<LockTip, String> = mapOf(
    LockTip.POINTED to "closes to a point over its last 0.3: spikes and ragged ends",
    LockTip.ROUND to "a dome whose last rings step evenly round it: a bob's blunt ends, curls",
    LockTip.FORKED to "split into `tipPoints` (2 or 3) points cut back between them: a choppy, layered fringe",
    LockTip.FLICKED to "turns out 50 degrees over its last 0.3: a flicked fringe or a flipped hem",
)

/** The lock override fields a fit does not set. */
private val FIT_SKIPS = setOf("index", "thickness", "curl", "wave", "twist", "section", "guide", "bones", "physics", "hidden")

/** Lock hair: every group field with its default, range and units, each preset's groups, the scalp flow and the inner mass. */
internal object Hair {
    private val json = Json { encodeDefaults = true }

    fun render(templates: List<PartTemplate>): String = buildString {
        appendLine("# Lock hair")
        appendLine()
        appendLine("A recipe's `hair` is a list, one entry a head: a `preset` gives a whole hairstyle, `groups` change its groups by id or add new ones, `flow` sets which way the hair grows over the scalp, and `mass` or `fitMass` gives the inner mass the roots sit on. Sizes are in body units; angles in degrees; shares run 0 to 1 unless given otherwise.")
        appendLine()
        appendLine("## Groups")
        appendLine()
        appendLine("A group the recipe names by a preset group's id changes only the fields it sets; `groups: []` or no `groups` keeps the preset's groups as they are. A group of any other id is added and needs its `kind`; `removed: true` leaves out a preset group of that id.")
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(LockGroup.serializer(), LockGroup(LockGroupId("g"), LockGroupKind.FRINGE, PartId("head")), skip = setOf("id", "kind", "on", "mass", "physics", "overrides"))))
        appendLine()
        appendLine("A `trim` cuts each lock where its grown path first crosses a plane from the side its root lies on: a bob's hem, bangs cut straight, the level top of a flat top. A lock that never reaches it keeps its length. A blunt cut ends square, the lock as wide as it was where it was cut.")
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(LockTrim.serializer(), LockTrim(), owner = "trim")))
        appendLine()
        append(table(listOf("kind", "roots and grows"), KINDS.map { (kind, text) -> listOf(code(serialName(kind.name)), text) }))
        appendLine()
        append(table(listOf("section", "when"), SECTIONS.map { (section, text) -> listOf(code(serialName(section.name)), text) }))
        appendLine()
        append(table(listOf("tip", "when"), TIPS.map { (tip, text) -> listOf(code(serialName(tip.name)), text) }))
        appendLine()
        appendLine("## Presets")
        appendLine()
        appendLine("Each preset's groups, their sizes (`length`, `width`, `thickness`) as shares of the head's reach from its middle, which the preset multiplies by the head as built or by `reach`. Every group not listed here is not in the preset: add it with its `kind`.")
        for (preset in HairPreset.entries) {
            appendLine()
            appendLine("### ${serialName(preset.name)}")
            appendLine()
            appendLine("Flow: ${flowText(presetFlow(preset, PartId("head")))}.")
            presetShell(preset, PartId("head"))?.let { appendLine(); appendLine("Shell: ${shellText(it)}.") }
            if (!presetMassed(preset)) {
                appendLine()
                appendLine("No inner mass: its locks root on the head.")
            }
            val layout = presetLayout(preset)
            if (layout.isNotEmpty()) {
                appendLine()
                append(table(PRESET_HEADER, layout.map(::presetRow)))
            }
        }
        appendLine()
        appendLine("## Flow")
        appendLine()
        appendLine("Which way each lock root grows: a background fall down the head, a whorl spiralling out of the crown, a parting the hair lies away from on each side, an overall comb over the top, and cowlicks, each reaching only so far. A group follows it as far as its `flow`, its kind's own way the rest; a group's `sweep` adds to it, and a lock's own values win over both. Roots keep off a parting's line, so the scalp shows along it as a thin line and nowhere else.")
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(ScalpFlow.serializer(), ScalpFlow(PartId("head")), owner = "flow", skip = setOf("on", "whorl", "parting", "cowlicks"))))
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(Whorl.serializer(), Whorl(), owner = "flow.whorl")))
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(Parting.serializer(), Parting(), owner = "flow.part")))
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(Cowlick.serializer(), Cowlick(), owner = "flow.cowlicks[]")))
        appendLine()
        appendLine("## Shell")
        appendLine()
        appendLine("`shell` cuts the hair to the skin over the head: a thin shell lying on the scalp from just outside its hairline to the crown, in a stubble tone, its edge softening into the skin and, with a fade, its tone fading into the skin up the sides and back from the hairline. It is a part of its own riding the head's bone, coloured its tone with the head's own colour laid over it where it fades; locks may lie over it. Each field given replaces the preset's shell's, the defaults below where the preset has none; `removed: true` leaves out the preset's shell.")
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(HairShell.serializer(), HairShell(PartId("head")), owner = "shell", skip = setOf("on", "hairline"))))
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(Hairline.serializer(), Hairline(), owner = "shell.hairline")))
        appendLine()
        append(table(listOf("fade", "reaches"), FADES.map { (fade, text) -> listOf(code(serialName(fade.name)), text) }))
        appendLine()
        appendLine("## Outline")
        appendLine()
        appendLine("`outline` draws the hair's silhouette as the sheet does, one closed curve a view, and fits the hair to it: the inner mass's `crown` and `width` sized to the region the front outline covers, each tip of an outline taken by the lock whose tip lands nearest it from a root facing that view, that lock's root moved, its length, rise, sweep, fall, lie and width searched so its tip lands on the outline's tip and its sides follow the outline to the valleys either side, a tip pointing out from the head below the scalp's middle ending in a flick, and every other lock shortened, or laid down, where it would stray outside the outline. Then any lock standing well out of the head and its mass to the side or below in some view is laid down, or shortened, until it no longer does; the locks either side of a narrow gap of skin under the hairline are widened to overlap over it; and a lock standing up out of the top is made wide enough near its tip to read as a spike. The fitted values are the locks' overrides below and the mass's parameters; a fit owns them, so values given for them by hand are replaced. Tied groups and locks with guides of their own are left as they are. A view given in the sheet head's lengths (`heads`) is sized to the head as built by its eye line (`eyes`), else by its height. The build prints, and writes under its `hair-fit.txt`, how far each outline lies from the hair as built, mean and worst in the head's drawn units, and warns of each tip no lock reached, with why. `kit sheet hair` traces an outline from a sheet's view (kit/README.md).")
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(RecipeHairOutline.serializer(), RecipeHairOutline(emptyList()), owner = "outline", skip = setOf("views"))))
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(RecipeOutlineView.serializer(), RecipeOutlineView(OutlineView.FRONT, emptyList()), owner = "outline.views[]")))
        appendLine()
        appendLine("The lock overrides a fit sets, which a group's `locks` can also give by hand:")
        appendLine()
        append(table(listOf("field", "default", "range", "meaning"), fieldRows(LockOverride.serializer(), LockOverride(0), owner = "locks[]", skip = FIT_SKIPS)))
        appendLine()
        appendLine("## Inner mass")
        appendLine()
        appendLine("`fitMass` fits a `$MASS_TEMPLATE` cap to the head as built, as the app's preset menu does: its size and crown from the head's box, its top following the skull's dome. Its `params` replace the fitted values; most heads need none. The fitted values, with each parameter's range from the template:")
        appendLine()
        append(table(listOf("parameter", "fitted", "range", "meaning"), massRows(templates)))
    }

    private val PRESET_HEADER = listOf("group", "kind", "count", "length", "width", "thickness", "rise", "fall", "sweep", "curl", "taper", "section", "tip", "layer", "tie", "flow", "lie", "stand", "trim", "coil")

    private fun presetRow(g: PresetGroup): List<String> = listOf(
        code(g.name), serialName(g.kind.name), g.count.toString(), num(g.length), num(g.width), num(g.thickness), num(g.rise), num(g.fall),
        num(g.sweep), num(g.curl), num(g.taper), serialName(g.section.name), serialName(g.tip.name), g.layer.toString(), num(g.tie), num(g.flow),
        num(g.lie), num(g.stand), g.trim?.let { "level ${num(it.level)}, tilt ${num(it.tilt)}" } ?: "-", num(g.coil),
    )

    private val FADES: Map<ShellFade, String> = mapOf(
        ShellFade.NONE to "no fade: the tone is whole down to the hairline",
        ShellFade.LOW to "fades into the skin over about 14 degrees above the hairline round the sides and back: just above the ears and nape",
        ShellFade.MID to "over about 26 degrees: to about the temples",
        ShellFade.HIGH to "over about 40 degrees: to the corners of the top",
    )

    private fun shellText(shell: HairShell): String = listOfNotNull(
        "thickness ${num(shell.thickness)}",
        "fade ${serialName(shell.fade.name)}",
        shell.fadeTo.takeIf { it > 0f }?.let { "fadeTo ${num(it)}" },
        if (shell.steps == 0) "smooth" else "${shell.steps} steps",
        "shade ${num(shell.shade)}",
    ).joinToString(", ")

    private fun flowText(flow: ScalpFlow): String {
        val parting = when (flow.parting.kind) {
            PartingKind.NONE -> "no parting"
            PartingKind.CENTRE -> "a centre parting"
            PartingKind.SIDE -> "a side parting at ${num(flow.parting.at)}"
        }
        val sweeps = listOfNotNull(
            flow.sweep.takeIf { it != 0f }?.let { "sweep ${num(it)}" },
            flow.sweepBack.takeIf { it != 0f }?.let { "sweepBack ${num(it)}" },
        )
        return (listOf("a crown whorl", parting) + sweeps).joinToString(", ")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> fieldRows(serializer: KSerializer<T>, sample: T, owner: String? = null, skip: Set<String> = emptySet()): List<List<String>> {
        val descriptor = serializer.descriptor
        val defaults = json.encodeToJsonElement(serializer, sample).jsonObject
        return (0 until descriptor.elementsCount).mapNotNull { i ->
            val name = descriptor.getElementName(i)
            if (name in skip) return@mapNotNull null
            val annotations = descriptor.getElementAnnotations(i)
            val range = annotations.filterIsInstance<Range>().firstOrNull()?.let { "${num(it.min.toFloat())} to ${num(it.max.toFloat())}" } ?: "-"
            val default = defaults[name]?.let { if (it is JsonNull) "none" else it.toString().trim('"') } ?: "-"
            val doc = annotations.filterIsInstance<Doc>().firstOrNull()?.text ?: "-"
            listOf(code(if (owner == null) name else "$owner.$name"), default, range, doc)
        }
    }

    private fun massRows(templates: List<PartTemplate>): List<List<String>> {
        val template = templates.filter { it.id.value == MASS_TEMPLATE }.maxByOrNull { it.version } ?: return emptyList()
        return fittedMassShape(1f, 1f).keys.sorted().map { name ->
            val spec = template.params[name]
            val fitted = if (name == "size" || name == "crown") "by the head" else num(fittedMassShape(1f, 1f).getValue(name))
            val range = spec?.let { listOfNotNull(it.min?.let(::num), it.max?.let(::num)).joinToString(" to ") }?.ifEmpty { "-" } ?: "-"
            listOf(code(name), fitted, range, spec?.doc ?: "-")
        }
    }

    private fun serialName(name: String): String = name.lowercase().split('_').mapIndexed { i, w -> if (i == 0) w else w.replaceFirstChar { it.uppercase() } }.joinToString("")
}
