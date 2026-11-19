package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.recipe.model.RecipeTarget
import kotlin.math.abs

/** How far off a size may be, as a share of its target, and a place, as a share of the head, before it is flagged. */
const val SIZE_TOLERANCE = 0.15f
const val PLACE_TOLERANCE = 0.05f

/** One target set against what was built: a printed [line], and whether it is [flagged]. */
data class ProportionTargetCheck(val name: String, val flagged: Boolean, val line: String)

/** Each of [targets] against [report]: an `ok` line, or a `FLAG` line for what is off by more than its tolerance or cannot be checked. */
fun checkTargets(targets: List<RecipeTarget>, report: ProportionReport): List<ProportionTargetCheck> = targets.map { target ->
    val measured = report.measured.firstOrNull { it.name == target.name }
    when {
        target.name !in PROPORTION_NAMES -> flagged(target, "FLAG target ${target.name}: not a measured proportion; names: ${PROPORTION_NAMES.joinToString()}")
        target.of != "head" && target.of != "height" -> flagged(target, "FLAG target ${target.name}: of is ${target.of}, not head or height")
        measured == null -> flagged(target, "FLAG target ${target.name}: not measured, ${report.unmeasured[target.name] ?: "no reading"}")
        else -> {
            val built = if (target.of == "head") measured.ofHead else measured.ofHeight
            val tolerance = target.tolerance ?: if (measured.isPlace) PLACE_TOLERANCE else SIZE_TOLERANCE
            val off = if (measured.isPlace) built - target.value else (built - target.value) / target.value
            val words = if (measured.isPlace) "%+.3f of the head".format(off) else "%+.0f%%".format(off * 100)
            val allowed = if (measured.isPlace) "%.3f".format(tolerance) else "%.0f%%".format(tolerance * 100)
            val text = "target ${target.name} %.3f of the ${target.of}, built %.3f (%s, tolerance %s)".format(target.value, built, words, allowed)
            if (abs(off) > tolerance) flagged(target, "FLAG $text") else ProportionTargetCheck(target.name, false, "ok $text")
        }
    }
}

private fun flagged(target: RecipeTarget, line: String) = ProportionTargetCheck(target.name, true, line)
