package dev.chieppa.squash.character.block

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.DigitAxis
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Where along a claw's patch, in its own u, the hidden anchor ring, the collar's closed root, the claw's root and its point lie. */
internal object ClawU {
    const val ANCHOR = 0f
    const val COLLAR = 0.4f
    const val ROOT = 0.5f
    const val POINT = 1f
}

/** How far round the tip's front toward the digit's back a claw leaves it, in degrees. */
private const val EMERGE_DEGREES = 30f

/** How deep inside the tip's surface, as a share of the tip's radius, a claw's root lies. */
private const val ROOT_DEPTH = 0.92f

/** How far a claw's root points up from the digit's line toward its back, in degrees, before it hooks down. */
private const val ROOT_LIFT_DEGREES = 10f

/** How far a whole hook turns the claw's line toward the digit's underside from root to point, in degrees. */
private const val FULL_HOOK_DEGREES = 110f

/** A claw's breadth across the digit as a share of its height. */
private const val FLAT = 0.6f

/** How the claw's height falls from root to point: the power of what is left of its length. */
private const val TAPER_POWER = 0.85f

/** Rings along the claw from its root to its point, the point a pole. */
private const val CLAW_RINGS = 12

/** The collar's rings, from its closed root to the claw's root, by their place back from the claw's root in claw heights and their size in the collar's. */
internal val COLLAR_BACK = floatArrayOf(0.7f, 0.6f, 0.45f, 0.28f, 0.12f)
private val COLLAR_SIZE = floatArrayOf(0f, 0.7f, 0.95f, 1f, 0.92f)

/** How far behind the claw's root its hidden anchor lies, in claw lengths: a whole retract slides it back nine tenths of that. */
internal const val ANCHOR_BACK = 2f

/** How far down from the digit's line, in degrees, a retracting claw slides as it goes back, so its hooked point stays inside the toe. */
private const val SLIDE_DEGREES = 10f

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/**
 * One claw's rings in the part's own axes, from its hidden anchor to its point: [us], [centres], [heights] across the digit's back
 * and underside and [breadths] across the row, and [side], the direction across the row its breadth runs along. The anchor ring is
 * buried, so the patch shows from the collar's closed root on.
 */
internal class ClawRings(val us: FloatArray, val centres: List<Vec3>, val heights: FloatArray, val breadths: FloatArray, val side: Vec3)

/** The shape of a claw: its [length] as a share of its digit's, its root's [thickness] as a share of the tip's, its [hook] 0 to 1 and its [sheath] collar 0 to 1. */
internal class ClawShape(val length: Float, val thickness: Float, val hook: Float, val sheath: Float) {
    /**
     * The rings of a claw on the digit along [axis], tapering by [taper] to its tip, its back toward [back]: rooted inside the front of
     * the tip a little toward the back, pointing on along the digit and hooking toward its underside to a sharp point, with a collar
     * round its root and a hidden anchor behind it inside the toe, toward which it slides when it retracts.
     */
    fun rings(axis: DigitAxis, taper: Float, back: Vec3): ClawRings {
        val dir = axis.direction
        val tipRadius = axis.radius * taper
        val centre = axis.at(1f - tipRadius / axis.length)
        val emerge = EMERGE_DEGREES * DEGREES_TO_RADIANS
        val root = centre + (dir * cos(emerge) + back * sin(emerge)) * (tipRadius * ROOT_DEPTH)
        val clawLength = length * axis.length
        val height = 2f * tipRadius * thickness
        val lift = ROOT_LIFT_DEGREES * DEGREES_TO_RADIANS
        val start = (dir * cos(lift) + back * sin(lift)).normalized()
        val down = (dir * sin(lift) - back * cos(lift)).normalized()
        val turn = max(hook, 0f) * FULL_HOOK_DEGREES * DEGREES_TO_RADIANS
        fun along(s: Float): Vec3 {
            if (turn < SMALL) return root + start * (clawLength * s)
            val radius = clawLength / turn
            val angle = turn * s
            return root + start * (radius * sin(angle)) + down * (radius * (1f - cos(angle)))
        }
        val slide = SLIDE_DEGREES * DEGREES_TO_RADIANS
        val anchor = root - (dir * cos(slide) + back * sin(slide)) * (clawLength * ANCHOR_BACK)
        val collar = height * (1f + sheath * COLLAR_SWELL)
        val us = ArrayList<Float>()
        val centres = ArrayList<Vec3>()
        val heights = ArrayList<Float>()
        fun ring(u: Float, at: Vec3, size: Float) {
            us += u
            centres += at
            heights += size
        }
        ring(ClawU.ANCHOR, anchor, 0f)
        COLLAR_BACK.indices.forEach { i ->
            val u = ClawU.COLLAR + (ClawU.ROOT - ClawU.COLLAR) * i / COLLAR_BACK.size
            ring(u, root - start * (height * COLLAR_BACK[i]), collar * COLLAR_SIZE[i])
        }
        for (i in 0..CLAW_RINGS) {
            val s = i.toFloat() / CLAW_RINGS
            ring(ClawU.ROOT + (ClawU.POINT - ClawU.ROOT) * s, along(s), height * (1f - s).pow(TAPER_POWER))
        }
        val side = (dir cross back).normalized()
        return ClawRings(us.toFloatArray(), centres, heights.toFloatArray(), FloatArray(heights.size) { heights[it] * FLAT }, side)
    }

    private companion object {
        const val SMALL = 1e-4f

        /** How much wider than the claw's root a whole sheath's collar is. */
        const val COLLAR_SWELL = 0.45f
    }
}
