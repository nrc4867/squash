package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.EaseProfile
import kotlin.math.exp

/** How far either side of a dropped seam, in its part's u, the cloth eases from the stand-off above it to its own. */
private const val DROP_BLEND = 0.03f

/** How deep a dropped seam pinches the cloth in, as a share of the lesser stand-off either side of it. */
private const val SEAM_PINCH = 0.35f

/** How wide a dropped seam's pinch is along its part, in u. */
private const val SEAM_WIDTH = 0.012f

/** How far along its part, in chart rows, a gathering band's cells are read either side, so the cloth eases into the band. */
private const val GATHER_ROWS = 2f

/** How far a fold's cells are read either side of a place, in chart rows, so its edge rises over about a row and stays a step. */
private const val FOLD_ROWS = 0.5f

/**
 * How far a worn garment stands off one covered part at each place on it, in body units: its [profile] along the part, front and back,
 * eased from [drop]'s stand-off above a dropped seam to its own below it with a pinch at the seam; pulled in to each of [gathers]' ease
 * over that band's cells; out by each of [folds]' thickness over its cells, as cloth turned back over itself; and standing off the
 * garments [under] it by theirs, so eases add. See HLD: surface, Garments on the surface.
 */
data class ClothEase(
    val profile: EaseProfile,
    val gathers: List<Gather> = emptyList(),
    val drop: Drop? = null,
    val under: List<ClothEase> = emptyList(),
    val folds: List<Fold> = emptyList(),
) {
    /** The ease at [u] along the part and [v] round it, [back] of the way from its front (0) to its back (1). */
    fun at(u: Float, v: Float, back: Float): Float {
        var ease = profile.at(u, back)
        drop?.let { seam ->
            val below = smoothstep(seam.u - DROP_BLEND, seam.u + DROP_BLEND, u)
            ease = seam.above + (ease - seam.above) * below
            val off = (u - seam.u) / SEAM_WIDTH
            ease -= SEAM_PINCH * minOf(seam.above, ease) * exp(-off * off)
        }
        for (gather in gathers) ease += (gather.ease - ease) * gather.weight(u, v)
        for (fold in folds) ease += fold.thickness * fold.weight(u, v)
        for (inner in under) ease += inner.at(u, v, back)
        return ease
    }

    /** No less than the least the ease is anywhere on the part. */
    val least: Float
        get() {
            var low = profile.least
            drop?.let { low = minOf(low, it.above) * (1f - SEAM_PINCH) }
            gathers.forEach { low = minOf(low, it.ease) }
            return low + under.sumOf { it.least.toDouble() }.toFloat()
        }

    /** No more than the most the ease is anywhere on the part. */
    val most: Float
        get() {
            var high = profile.most
            drop?.let { high = maxOf(high, it.above) }
            high += folds.sumOf { it.thickness.toDouble() }.toFloat()
            return high + under.sumOf { it.most.toDouble() }.toFloat()
        }

    /** Whether it stands off the same everywhere, as a single fit does. */
    val uniform: Boolean get() = profile.uniform && gathers.isEmpty() && folds.isEmpty() && drop == null && under.all { it.uniform }

    /** This ease standing off [inner] too. */
    fun over(inner: List<ClothEase>): ClothEase = if (inner.isEmpty()) this else copy(under = under + inner)

    companion object {
        /** The same [ease] everywhere. */
        fun flat(ease: Float): ClothEase = ClothEase(EaseProfile.flat(ease))
    }
}

/** A band of [cloth]'s cells, those of area [area] or all of them where it is -1, that gathers the cloth in to [ease]. */
data class Gather(val cloth: ClothField, val area: Int, val ease: Float) {
    /** How much of the band is at (u, v), from 0 off it to 1 within it, eased over [GATHER_ROWS] rows either side. */
    fun weight(u: Float, v: Float): Float {
        val step = GATHER_ROWS / CHART_ROWS
        val read = { at: Float -> if (area < 0) cloth.at(at, v) else cloth.areaAt(area, at, v) }
        val mean = (read(u - step) + 2f * read(u) + read(u + step)) / 4f
        return smoothstep(0.15f, 0.6f, mean)
    }
}

/** A band of [cloth]'s cells, those of area [area] or all of them where it is -1, folded back over the cloth beside it, standing out by [thickness]. */
data class Fold(val cloth: ClothField, val area: Int, val thickness: Float) {
    /** How much of the fold is at (u, v), from 0 off it to 1 within it, rising over about a row at its edge. */
    fun weight(u: Float, v: Float): Float {
        val step = FOLD_ROWS / CHART_ROWS
        val read = { at: Float -> if (area < 0) cloth.at(at, v) else cloth.areaAt(area, at, v) }
        val mean = (read(u - step) + 2f * read(u) + read(u + step)) / 4f
        return smoothstep(0.3f, 0.55f, mean)
    }
}

/** A seam dropped to [u] along its part, the cloth above it standing off by [above], the ease of the cloth it grows from there. */
data class Drop(val u: Float, val above: Float)

/** 0 at or below [from], 1 at or past [to], eased between. */
internal fun smoothstep(from: Float, to: Float, at: Float): Float {
    val t = ((at - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
