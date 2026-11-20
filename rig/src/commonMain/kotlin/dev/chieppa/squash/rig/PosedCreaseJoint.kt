package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.smoothstep
import kotlin.math.cos

/** The bend, in degrees, at which a crease is at its deepest. */
private const val DEEPEST_BEND = 70f

/** The bend, in degrees, over which a joint's own closing side takes over from its rest side. */
private const val SIDE_FROM = 4f
private const val SIDE_TO = 20f

/** How wide a crease's shade runs along the limb, as a share of the joint's radius, at rest and added at its deepest. */
private const val WIDTH_AT_REST = 0.2f
private const val WIDTH_DEEPENED = 0.14f

/** The least width of a crease's shade along the limb, in the body's mesh cells, at rest and added at its deepest: a thin joint's crease still spans enough cells to draw. */
private const val LEAST_CELLS_AT_REST = 1.5f
private const val LEAST_CELLS_DEEPENED = 1.5f

/** How far round the limb a crease reaches either side of its inner side, in degrees, at rest and added at its deepest. */
private const val SPAN_AT_REST = 28f
private const val SPAN_DEEPENED = 32f

/** Where round the limb a crease is at full strength, and where its middle stretch is, as shares of its reach. */
private const val CORE_SHARE = 0.35f
private const val MIDDLE_SHARE = 0.1f

/** How far from a joint's axis, as shares of its radius, a vertex is too near the axis, or too far off, to lie on its crease. */
private const val NEAREST_AXIS = 0.25f
private const val FURTHEST_AXIS = 2.1f

/** How many widths along the limb a crease reaches before it is nothing. */
private const val WIDTHS_REACHED = 3f

/**
 * A crease joint in one pose: its pivot [at], the [plane] that splits its posed angle evenly, the [inner] side it closes on, its
 * [amount], 0 to its strength, and its shade's [width] along the limb and reach round it, as cosines of the angle off [inner].
 */
class PosedCreaseJoint(
    val joint: CreaseJoint,
    val at: Vec3,
    val plane: Vec3,
    val inner: Vec3,
    val amount: Float,
    val width: Float,
    val depth: Float,
    private val reachCosine: Float,
    private val coreCosine: Float,
    private val middleCosine: Float,
) {
    /**
     * How [p] faces the crease's inner side round the limb, as the cosine of the angle off it, or below -1 where it lies too far along
     * the limb, or too near or too far from its axis, to lie on the crease.
     */
    fun facing(p: Vec3): Float {
        val rel = p - at
        val along = rel dot plane
        if (along > width * WIDTHS_REACHED || along < -width * WIDTHS_REACHED) return OFF
        val radial = rel - plane * along
        val distance = radial.length
        if (distance < joint.radius * NEAREST_AXIS || distance > joint.radius * FURTHEST_AXIS) return OFF
        return (radial dot inner) / distance
    }

    /** How strongly the crease lies round the limb where a point [facing] it so, 0 to [amount]: its shade's and ink's strength there. */
    fun around(facing: Float): Float = amount * smoothstep(reachCosine, coreCosine, facing)

    /** The same for its middle stretch alone, where a deep crease's shorter lines either side run. */
    fun middle(facing: Float): Float = amount * smoothstep(coreCosine, middleCosine, facing)

    /** How far [p] lies from the joint's axis, in its radii. */
    fun axisShare(p: Vec3): Float {
        val rel = p - at
        return (rel - plane * (rel dot plane)).length / joint.radius
    }

    /** How far [p] lies along the limb from the crease's middle, in widths: the crease's ink runs where this is 0. */
    fun along(p: Vec3): Float = ((p - at) dot plane) / width

    companion object {
        /**
         * [joint] as [frames] pose it, scaled by [strength], with [rest] of a full crease showing on its rest side while it rests; null
         * where it creases nowhere: no rest side and no bend, or no strength.
         */
        fun of(joint: CreaseJoint, frames: BoneFrames, strength: Float, rest: Float): PosedCreaseJoint? {
            val scaled = strength * joint.strength
            if (scaled <= 0f) return null
            val parentTurn = frames.turns[joint.parent]
            val own = frames.turns[joint.bone]
            val reference = parentTurn * joint.reference
            val continued = parentTurn * joint.direction
            val bent = own * joint.direction
            val plane = (reference + bent).unitOr(bent)
            val bend = degreesBetween(continued.unitOr(bent), bent.unitOr(continued))
            val closing = square(bent - continued, plane)
            val share = if (closing == null) 0f else smoothstep(SIDE_FROM, SIDE_TO, bend)
            val restSide = joint.inner?.let { square(parentTurn * it, plane) }
            val inner = when {
                restSide == null -> closing?.takeIf { share > 0f }
                closing == null || share <= 0f -> restSide
                else -> (restSide * (1f - share) + closing * share).let { mixed -> if (mixed.length > MIXED_LEAST) mixed.normalized() else if (share > 0.5f) closing else restSide }
            } ?: return null
            val depth = smoothstep(0f, DEEPEST_BEND, bend)
            val opened = if (restSide != null && closing != null) share * maxOf(0f, -(closing dot restSide)) else 0f
            val restAmount = if (restSide != null) rest * (1f - opened) else 0f
            val amount = scaled * maxOf(restAmount, depth)
            if (amount <= 0f) return null
            val pivot = frames.transforms[joint.bone](joint.pivot)
            val reach = (SPAN_AT_REST + SPAN_DEEPENED * depth) * DEGREES_TO_RADIANS
            return PosedCreaseJoint(
                joint, pivot, plane, inner, amount, maxOf(joint.radius * (WIDTH_AT_REST + WIDTH_DEEPENED * depth), joint.cell * (LEAST_CELLS_AT_REST + LEAST_CELLS_DEEPENED * depth)), depth,
                cos(reach), cos(reach * CORE_SHARE), cos(reach * MIDDLE_SHARE),
            )
        }

        /** The shortest a mix of two sides is before it is taken as no side: the two nearly opposite. */
        private const val MIXED_LEAST = 0.3f

        /** A facing no point on a crease has. */
        const val OFF = -2f
    }
}
