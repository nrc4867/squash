package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS

/**
 * What cell strokes did to drawn cloth on one part: the cells they took from what the shapes lay, [cut], and the cells they added,
 * [added], each as cloth of its own. Drawn cloth less the cut and with the added is the part's cloth: its edge is the shapes' outline
 * wherever no cell stroke changed it, and the cells' edge where one did, an opening's or a brush's.
 */
class CellEdits private constructor(private val cut: ClothField?, private val added: ClothField?) {
    /** The field [drawn] reads at (u, v), less the cut and with the added cells. */
    fun at(drawn: Float, u: Float, v: Float): Float {
        var value = drawn
        cut?.let { value = minOf(value, 1f - it.at(u, v)) }
        added?.let { value = maxOf(value, it.at(u, v)) }
        return value
    }

    /** How far inside the cloth (u, v) lies in body units, where the drawn cloth reads [drawn], less the cut and with the added cells. */
    fun inside(drawn: Float, u: Float, v: Float, along: Float, around: Float): Float {
        var d = drawn
        cut?.let { d = minOf(d, -it.inside(u, v, along, around)) }
        added?.let { d = maxOf(d, it.inside(u, v, along, around)) }
        return d
    }

    companion object {
        /** The edits that make the cells [laid], as shapes lay them, the cells [held]; null where they are the same. */
        fun of(laid: BooleanArray, held: BooleanArray): CellEdits? {
            val cut = BooleanArray(CHART_CELLS) { laid[it] && !held[it] }
            val added = BooleanArray(CHART_CELLS) { held[it] && !laid[it] }
            if (cut.none { it } && added.none { it }) return null
            return CellEdits(ClothField.of(listOf(cut)), ClothField.of(listOf(added)))
        }
    }
}
