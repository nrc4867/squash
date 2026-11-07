package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.ringMiddle

/** How many points around a ring its radius is measured at. */
private const val RADIUS_SAMPLES = 16

/** How far along u either side of a point its axis direction is read. */
private const val TANGENT_REACH = 0.02f

/** A placed part's rings as the rig reads them: their middles and sizes in body space. */
internal class PartRings(private val part: PlacedPart) {
    private val swept: List<Patch> = part.surface.patches.filter { it.slices != null }

    /** How long the part's axis is in body units per unit of u, or 0 when it has no swept patch. */
    val lengthPerU: Float get() = part.surface.axisLength

    val hasRings: Boolean get() = swept.isNotEmpty()

    private fun runAt(u: Float): Patch? = swept.firstOrNull { it.covers(u) } ?: swept.firstOrNull()

    /** The middle of the ring at [u], in body space. */
    fun middle(u: Float): Vec3 {
        val run = runAt(u) ?: return part.origin
        return part.origin + part.surface.shown(run.ringMiddle(u))
    }

    /** The mean distance of the ring at [u] from its middle. */
    fun radius(u: Float): Float {
        val run = runAt(u) ?: return 0f
        val centre = run.ringMiddle(u)
        return (0 until RADIUS_SAMPLES).sumOf { (run.positionAt(u, it.toFloat() / RADIUS_SAMPLES) - centre).length.toDouble() }.toFloat() / RADIUS_SAMPLES
    }

    /** The widest ring from u = [from] to [to]. */
    fun widestOver(from: Float, to: Float, steps: Int = 8): Float = (0..steps).maxOf { radius(from + (to - from) * it / steps) }

    /** The axis's direction at [u] in body space, toward larger u. */
    fun tangent(u: Float): Vec3 {
        val ahead = middle((u + TANGENT_REACH).coerceAtMost(1f))
        val behind = middle((u - TANGENT_REACH).coerceAtLeast(0f))
        val along = ahead - behind
        return if (along.length > 0f) along.normalized() else part.surface.shown(PartBuilder.axes(part.template).along)
    }
}
