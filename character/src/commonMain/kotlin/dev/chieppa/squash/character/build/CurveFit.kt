package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Tube
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View

/** The share of their size below which a curve's rings count as clipped by its bend. */
const val CLIPPED_HOLD = 0.75f

/**
 * How a part's curve lands on its template: [ignored] where the template does not sweep, and otherwise [hold], the least share of their
 * size its rings keep where the curve bends tighter than the part is thick (1 where none narrows). See HLD: surface, Curved axes.
 */
data class CurveFit(val ignored: Boolean, val hold: Float) {
    /** Whether the bend narrows the part noticeably, so the curve is drawn tighter than the part can follow at its thickness. */
    val clipped: Boolean get() = !ignored && hold < CLIPPED_HOLD

    companion object {
        /** How [params]' own curve lands on [template] drawn with [outlines], or null where the part has no curve of its own. */
        fun of(template: PartTemplate, params: Map<String, Float>, outlines: Map<View, OutlineCurve> = emptyMap()): CurveFit? {
            if (AxisCurveParams.ownPlaces(params).isEmpty()) return null
            val context = PartBuilder.context(template, params, outlines)
            if (context.curveIgnored) return CurveFit(ignored = true, hold = 1f)
            val hold = context.blocksOn.filterIsInstance<Tube>().minOfOrNull { it.bendHold(context) } ?: 1f
            return CurveFit(ignored = false, hold = hold)
        }
    }
}
