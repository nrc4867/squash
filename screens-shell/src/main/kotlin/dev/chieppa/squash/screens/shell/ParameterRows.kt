package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.model.ParamSpec
import dev.chieppa.squash.character.model.PartTemplate
import kotlin.math.abs
import kotlin.math.round

/** One parameter as the Shape inspector shows it: its current value and the limits its template sets. */
data class ParameterRow(
    val name: String,
    val value: Float,
    val isFlag: Boolean,
    val min: Float?,
    val max: Float?,
    val step: Float?,
    val unit: String?,
    val values: List<Float>? = null,
) {
    /** The slider's range; null unless both limits are set. */
    val range: ClosedFloatingPointRange<Float>? get() = if (min != null && max != null) min..max else null

    /** The stretches of consecutive values the template allows, or the whole range where it lists none. */
    val spans: List<ClosedFloatingPointRange<Float>>
        get() = values?.fold(emptyList()) { spans, value ->
            val last = spans.lastOrNull()
            if (last != null && value - last.endInclusive == (step ?: 1f)) spans.dropLast(1) + (last.start..value) else spans + (value..value)
        } ?: listOfNotNull(range)

    /** [raw] as the nearest of the template's values, or kept inside the limits and on its step. */
    fun constrain(raw: Float): Float {
        values?.let { allowed -> return allowed.minBy { abs(it - raw) } }
        val stepped = step?.let { round(((raw - (min ?: 0f)) / it)) * it + (min ?: 0f) } ?: raw
        return stepped.coerceIn(min ?: stepped, max ?: stepped)
    }
}

/** The template's parameters in file order, valued from [params] where a part has set them. */
fun parameterRows(template: PartTemplate, params: Map<String, Float> = emptyMap()): List<ParameterRow> =
    template.params.map { (name, spec) -> spec.row(name, params[name]) }

private fun ParamSpec.row(name: String, set: Float?) =
    ParameterRow(name, set ?: default.value, default.isFlag, values?.first() ?: min, values?.last() ?: max, step, unit, values)
