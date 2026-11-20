package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.block.Area
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.field.smoothstep
import kotlin.math.abs
import kotlin.math.min

/** How far past an area's edge, in u or v, a key on it eases out to nothing. */
private const val AREA_EASE = 0.08f

/** How much of a key on a template's area reaches each place of the part: whole inside the area, eased out past its edge. */
class AreaMask(private val area: Area) {
    /** The reach at [u] and [v] on the run [patch], nothing on another run. */
    fun at(patch: String, u: Float, v: Float): Float {
        if (patch != area.patch) return 0f
        val du = maxOf(area.u.start - u, u - area.u.end, 0f)
        val dv = aroundOutside(v)
        return 1f - smoothstep(0f, AREA_EASE, maxOf(du, dv))
    }

    /** How far round from the area's span of v a place at [v] lies, across the seam at 0 where the span crosses it. */
    private fun aroundOutside(v: Float): Float {
        val start = area.v.start
        val end = area.v.end
        val inside = if (start <= end) v in start..end else v >= start || v <= end
        if (inside) return 0f
        return min(around(v, start), around(v, end))
    }

    private fun around(a: Float, b: Float): Float {
        val d = abs(a - b) % 1f
        return min(d, 1f - d)
    }

    companion object {
        /** The mask of [template]'s area [name], or null where it has none so named. */
        fun of(template: PartTemplate, name: String): AreaMask? = template.blocks.filterIsInstance<Area>().firstOrNull { it.name == name }?.let(::AreaMask)
    }
}
