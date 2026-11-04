package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.GrainSpec
import dev.chieppa.squash.character.model.RuffSpec
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Grain
import dev.chieppa.squash.document.GrainComb
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surfaceAt
import kotlin.math.abs
import kotlin.math.min

/** How far past its edges a ruff fades out, in u and in v. */
private const val RUFF_FEATHER = 0.04f

private const val HALF_WAY_ROUND = 0.5f
private const val MIN_SPAN = 1e-5f

/**
 * A part's grain: which way fur runs at each place. Along the axis toward the end [spec] names, rising outward on its ruffs, and
 * near a comb in [grain] the way that comb lies. See HLD: look, Surface work rules.
 */
class GrainField(private val spec: GrainSpec, grain: Grain?) {
    private val combs = grain?.combs.orEmpty()

    /** The way fur runs at [u] and [v] on [patch] in the patch's own space, with length 1: along the surface and rising off it. */
    fun directionAt(patch: Patch, u: Float, v: Float): Vec3 {
        val surface = patch.surfaceAt(u, v)
        val axis = surface.alongU.normalized() * if (spec.toward == End.END) 1f else -1f
        var held = 0f
        var combed = Vec3.ZERO
        for (comb in combs) {
            if (comb.patch != patch.name) continue
            val weight = comb.weightAt(u, v, patch.closedV)
            if (weight <= 0f) continue
            val lies = surface.alongU * comb.du + surface.aroundV * comb.dv
            if (lies.length < MIN_SPAN) continue
            combed += lies.normalized() * weight
            held += weight
        }
        val tangent = if (held >= 1f) combed else combed + axis * (1f - held)
        val along = if (tangent.length < MIN_SPAN) axis else tangent.normalized()
        val outward = if (patch.name == BODY_PATCH) ruffShare(u, v, patch.closedV) else null
        val lift = outward?.let { (ruff, share) -> spec.lift + (ruff.lift - spec.lift) * share * (1f - min(held, 1f)) } ?: spec.lift
        return (along + surface.normal * lift).normalized()
    }

    /** The ruff [u] and [v] lie most inside, and how far inside, from 0 past its feather to 1 within it. */
    private fun ruffShare(u: Float, v: Float, closedV: Boolean): Pair<RuffSpec, Float>? =
        spec.ruffs.map { it to it.shareAt(u, v, closedV) }.filter { it.second > 0f }.maxByOrNull { it.second }
}

private fun RuffSpec.shareAt(u: Float, v: Float, closedV: Boolean): Float {
    val alongShare = insideShare(u, this.u.start, this.u.end)
    val centre = (this.v.start + this.v.end) / 2f
    val aroundShare = insideShare(centre + aroundFrom(centre, v, closedV), this.v.start, this.v.end)
    return min(alongShare, aroundShare)
}

private fun insideShare(at: Float, start: Float, end: Float): Float =
    (min(at - start, end - at) / RUFF_FEATHER + 1f).coerceIn(0f, 1f)

/** From [from] to [to] around v, the short way on a closed patch. */
private fun aroundFrom(from: Float, to: Float, closedV: Boolean): Float {
    val straight = to - from
    if (!closedV) return straight
    val wrapped = straight - kotlin.math.round(straight)
    return if (abs(wrapped) > HALF_WAY_ROUND) straight else wrapped
}

private fun GrainComb.weightAt(atU: Float, atV: Float, closedV: Boolean): Float {
    val du = atU - u
    val dv = aroundFrom(v, atV, closedV)
    val share = 1f - kotlin.math.sqrt(du * du + dv * dv) / reach
    return if (share <= 0f) 0f else share * share
}
