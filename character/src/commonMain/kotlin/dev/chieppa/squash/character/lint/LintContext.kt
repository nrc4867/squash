package dev.chieppa.squash.character.lint

import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.model.Mirror
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SectionSpec
import dev.chieppa.squash.character.model.Span

/** Whether this block's run of slices reaches [u]. */
fun Block.reaches(u: Float): Boolean = run()?.let { u >= it.start && u <= it.end } ?: false

private const val V_TOP = 0f
private const val V_UNDER = 0.5f

/** The most a socket may multiply a child's depth by. */
const val MAX_SINK = 4f

/** The checks a block's lint is written from, reporting under [path]. */
class LintContext(val template: PartTemplate, private val path: String) {
    fun finding(field: String, message: String) = listOf(Finding("$path.$field", message))

    fun block(name: String): Block? = template.blocks.firstOrNull { it.name == name }

    /** Whether the block named [name] comes before [block] in the file, as the blocks that read it need. */
    fun listedBefore(name: String, block: Block): Boolean =
        template.blocks.indexOfFirst { it.name == name } < template.blocks.indexOf(block)

    fun unit(field: String, value: Float?): List<Finding> =
        if (value == null || value in 0f..1f) emptyList() else finding(field, "$value is outside 0 to 1")

    fun unit(field: String, span: Span): List<Finding> = when {
        span.start !in 0f..1f || span.end !in 0f..1f -> finding(field, "$span is outside 0 to 1")
        span.start >= span.end -> finding(field, "$span runs backward")
        else -> emptyList()
    }

    /** A reference names a numeric parameter of the template. */
    fun param(field: String, param: Param?): List<Finding> {
        val name = param?.refName ?: return emptyList()
        val spec = template.params[name] ?: return finding(field, "no parameter '$name'")
        return if (spec.default.isFlag) finding(field, "'$name' is a flag, not a number") else emptyList()
    }

    /** A reference names a flag parameter of the template. */
    fun flag(field: String, ref: ParamRef?): List<Finding> {
        val name = ref?.name ?: return emptyList()
        val spec = template.params[name] ?: return finding(field, "no parameter '$name'")
        return if (spec.default.isFlag) emptyList() else finding(field, "'$name' is a number, not a flag")
    }

    /** A socket's sink multiplier is 0 or more and no more than [MAX_SINK]. */
    fun sink(field: String, value: Float): List<Finding> =
        if (value in 0f..MAX_SINK) emptyList() else finding(field, "$value is outside 0 to $MAX_SINK")

    /** A count is at least [min]. */
    fun atLeast(field: String, value: Int, min: Int): List<Finding> =
        if (value >= min) emptyList() else finding(field, "$value is below $min")

    /** A run that is on whenever [condition] is reaches [u], so a point there has a surface to sit on. */
    fun onTheSurface(field: String, u: Float, condition: ParamRef?): List<Finding> {
        val runs = template.blocks.filter { it.condition == null || it.condition == condition }
        return if (runs.any { it.reaches(u) }) emptyList() else finding(field, "no run reaches u = $u")
    }

    /** A paired point's twin mirrors v to 1 - v, so it must lie off the plane the twin mirrors across. */
    fun paired(field: String, v: Float, pair: Mirror?): List<Finding> =
        if (pair == null || (v != V_TOP && v != V_UNDER && v != 1f)) emptyList()
        else finding(field, "a paired point at v = $v coincides with its twin")

    /** Control points match the template's segment count. */
    fun section(field: String, section: SectionSpec?): List<Finding> {
        val count = section?.controlPointCount ?: return emptyList()
        val segments = template.topology.segments
        return if (count == segments) emptyList() else finding(field, "$count control points for $segments segments")
    }
}
