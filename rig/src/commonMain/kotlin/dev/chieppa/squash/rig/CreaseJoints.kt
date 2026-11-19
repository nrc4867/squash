package dev.chieppa.squash.rig

import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Vec3
import kotlin.math.acos

/** Each role whose parts' joints crease, with how strongly they do where nothing else sets it: limbs most, a torso least. */
internal val CREASE_ROLE_STRENGTH: Map<Role, Float> = mapOf(
    Role.ARM to 1f,
    Role.LEG to 1f,
    Role.NECK to 0.8f,
    Role.FOOT to 0.8f,
    Role.HAND to 0.7f,
    Role.BODY to 0.7f,
    Role.SPINE to 0.7f,
    Role.HEAD to 0.6f,
)

/** Roles whose parts crease only where they meet the part they sit on: a wrist, an ankle, the neck under a head. */
private val BASE_ONLY = setOf(Role.HAND, Role.FOOT, Role.HEAD)

/** Roles whose joint resting bent, as an ankle under a foot that points forward, creases on the inside of that bend at rest. */
private val BENT_AT_REST = setOf(Role.ARM, Role.LEG, Role.FOOT, Role.HAND)

/** The most a joint's rest bend is, in degrees, for its parent's direction to count as one side of it: past this the two run on as one line. */
private const val MOST_REST_BEND = 150f

/** The least rest bend, in degrees, that creases at rest by itself. */
private const val LEAST_REST_BEND = 20f

/** How far a limit must outreach the other, as a share, for the joint to bend one way at rest: an elbow, a knee. */
private const val ONE_WAY_SHARE = 0.4f

/** The turn a one-way joint is tried at to find which side it closes on, in degrees. */
private const val TRIAL_TURN = 30f

/** The shortest a crease joint's own bone is, as a share of its radius: a bone shorter than this sits inside its neighbour's crease. */
private const val SHORTEST_SPAN = 0.5f

/** How many cells either side of a joint's plane the vertices its radius is read from lie. */
private const val RADIUS_BAND_CELLS = 1.5f

/** The least weight a vertex gives a joint's bones together to count toward its radius. */
private const val RADIUS_INFLUENCE = 0.5f

/** The front of the body, which a neck creases toward at rest. */
private val FRONT = Vec3.X

/**
 * A joint that creases: its [bone] in the rig, the bone whose turn carries its parent side, [parent], and the nearest bone with a span
 * before it, [carrier]; its [pivot], its bone's rest [direction], and the rest direction a bend is measured against, [reference]: its
 * parent's where the joint rests bent, as an ankle does, else its own. [radius] is how thick the body is across it, [inner] the side it
 * closes on at rest, square to its rest plane, null where it rests with no side, and [strength] its role's crease strength.
 */
class CreaseJoint(
    val bone: Int,
    val parent: Int,
    val carrier: Int,
    val pivot: Vec3,
    val direction: Vec3,
    val reference: Vec3,
    val radius: Float,
    val inner: Vec3?,
    val strength: Float,
) {
    /** The normal of the plane through the pivot that splits the joint's rest angle evenly. */
    val restPlane: Vec3 get() = (reference + direction).unitOr(direction)
}

/**
 * The joints of [rig] that crease, read on the body's rest corners at [positions] weighed by [skin], meshed at [cell]: every joint of a
 * part whose role creases ([CREASE_ROLE_STRENGTH]) with a bone of its own past it, a hand's, a foot's and a head's only where they sit
 * on their parent part, and no digit's or jaw's. See HLD: look, Creases and wrinkles.
 */
fun creaseJoints(rig: Rig, positions: FloatArray, skin: SurfaceSkin, cell: Float): List<CreaseJoint> {
    val roles = rig.parts.associate { it.id to it.role }
    return rig.bones.indices.mapNotNull { i ->
        val bone = rig.bones[i]
        val parent = rig.parents[i]
        if (parent < 0 || bone.digit != null || bone.name.joint == JAW) return@mapNotNull null
        val role = roles[bone.name.part] ?: return@mapNotNull null
        val strength = CREASE_ROLE_STRENGTH[role] ?: return@mapNotNull null
        if (role in BASE_ONLY && rig.bones[parent].name.part == bone.name.part) return@mapNotNull null
        if (bone.direction.length <= 0f) return@mapNotNull null
        val carrier = carrierOf(rig, parent)
        val direction = bone.direction.normalized()
        val parentDirection = rig.bones[carrier].direction.takeIf { it.length > 0f }?.normalized()
        val restBend = parentDirection?.let { degreesBetween(it, direction) } ?: 0f
        val reference = if (parentDirection != null && restBend < MOST_REST_BEND) parentDirection else direction
        val plane = (reference + direction).unitOr(direction)
        val radius = radiusAt(positions, skin, intArrayOf(i, parent, carrier), bone.pivot, plane, cell * RADIUS_BAND_CELLS, bone.length + rig.bones[carrier].length)
            ?: return@mapNotNull null
        if (bone.length < radius * SHORTEST_SPAN) return@mapNotNull null
        val inner = when {
            role in BENT_AT_REST && restBend in LEAST_REST_BEND..MOST_REST_BEND -> square(direction - reference, plane)
            else -> oneWayInner(bone, direction, plane) ?: if (role == Role.NECK) square(FRONT, plane) else null
        }
        CreaseJoint(i, parent, carrier, bone.pivot, direction, reference, radius, inner, strength)
    }
}

/** The jaw's joint name, which hinges a mouth and never creases. */
private const val JAW = "jaw"

/** [parent], or the nearest bone up its chain with a span of its own, as a part's end joint has none. */
private fun carrierOf(rig: Rig, parent: Int): Int {
    var at = parent
    while (at >= 0 && rig.bones[at].length <= 0f && rig.parents[at] >= 0) at = rig.parents[at]
    return at
}

/** The side a joint whose limits reach much further one way than the other closes toward, square to [plane], or null. */
private fun oneWayInner(bone: Bone, direction: Vec3, plane: Vec3): Vec3? {
    val limits = bone.limits ?: return null
    val closing = when {
        limits.max > 0f && -limits.min < ONE_WAY_SHARE * limits.max -> TRIAL_TURN
        limits.min < 0f && limits.max < ONE_WAY_SHARE * -limits.min -> -TRIAL_TURN
        else -> return null
    }
    val turned = rotation(bone.hinge, closing * DEGREES_TO_RADIANS) * direction
    return square(turned - direction, plane)
}

/** The part of [v] square to the unit [plane] normal, made unit, or null where it lies along it. */
internal fun square(v: Vec3, plane: Vec3): Vec3? {
    val across = v - plane * (v dot plane)
    return if (across.length > SAME_SIDE) across.normalized() else null
}

private const val SAME_SIDE = 1e-4f

/** The angle between two unit directions, in degrees. */
internal fun degreesBetween(a: Vec3, b: Vec3): Float = acos((a dot b).coerceIn(-1f, 1f)) * RADIANS_TO_DEGREES

/**
 * How thick the body is across a joint: the middle distance from the line through [pivot] along [plane] of the corners within [band]
 * of the plane that [bones] together weigh at least [RADIUS_INFLUENCE], short of [reach]; null with none.
 */
private fun radiusAt(positions: FloatArray, skin: SurfaceSkin, bones: IntArray, pivot: Vec3, plane: Vec3, band: Float, reach: Float): Float? {
    val distances = ArrayList<Float>()
    val count = minOf(positions.size / 3, skin.vertexCount)
    for (v in 0 until count) {
        if (influence(skin, v, bones) < RADIUS_INFLUENCE) continue
        val rel = positions.vec(v * 3) - pivot
        val along = rel dot plane
        if (along > band || along < -band) continue
        val across = (rel - plane * along).length
        if (across < reach) distances += across
    }
    if (distances.isEmpty()) return null
    distances.sort()
    return distances[distances.size / 2]
}

/** How much of vertex [v] the [bones] weigh together, each counted once. */
internal fun influence(skin: SurfaceSkin, v: Int, bones: IntArray): Float {
    var total = 0f
    for (slot in 0 until MAX_INFLUENCES) {
        val b = skin.bones[v * MAX_INFLUENCES + slot]
        if (b >= 0 && bones.contains(b)) total += skin.weights[v * MAX_INFLUENCES + slot]
    }
    return total
}
