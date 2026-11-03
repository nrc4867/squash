package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.model.ParamSpec
import dev.chieppa.squash.character.model.PartTemplate
import kotlin.math.round

/** One parameter as the Part drawer shows it: its current value and the limits its template sets. */
data class ParameterRow(
    val name: String,
    val value: Float,
    val isFlag: Boolean,
    val min: Float?,
    val max: Float?,
    val step: Float?,
    val unit: String?,
) {
    /** The slider's range; null unless both limits are set. */
    val range: ClosedFloatingPointRange<Float>? get() = if (min != null && max != null) min..max else null

    /** [raw] kept inside the limits and on the template's step. */
    fun constrain(raw: Float): Float {
        val stepped = step?.let { round(((raw - (min ?: 0f)) / it)) * it + (min ?: 0f) } ?: raw
        return stepped.coerceIn(min ?: stepped, max ?: stepped)
    }
}

/** The template's parameters in file order, valued from [params] where a part has set them. */
fun parameterRows(template: PartTemplate, params: Map<String, Float> = emptyMap()): List<ParameterRow> =
    template.params.map { (name, spec) -> spec.row(name, params[name]) }

private fun ParamSpec.row(name: String, set: Float?) =
    ParameterRow(name, set ?: default.value, default.isFlag, min, max, step, unit)
