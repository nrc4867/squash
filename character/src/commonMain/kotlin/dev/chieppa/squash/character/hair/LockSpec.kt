package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.LockSection
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The most degrees a lock's outer part lifts off the head. */
private const val MOST_RISE = 90f

/** The most a lock sweeps either way with the flow's bend added to its group's sweep. */
private const val MOST_SWEEP = 1.5f

/** How deep a lock's root sinks under the scalp, as a share of its thickness. */
private const val ROOT_SINK = 0.6f

/** How far a lock lies off the scalp past its root, as a share of its thickness, before its layer and row lift it further. */
private const val BASE_CLEARANCE = 0.15f

/** How far each layer and each row above another lifts a lock off the scalp, as a share of its thickness. */
private const val LAYER_LIFT = 0.6f
private const val ROW_LIFT = 0.35f

/** How far every other lock of a row stands off its neighbours, as a share of its thickness, so no two lie in one surface. */
private const val STAGGER = 0.2f

/**
 * One lock of a group as it is built: its [index], where it roots ([root], facing [out], growing along [growth]), its sizes and shape with
 * its override over the group's, how far it lies off the scalp past its root ([clearance]) and how deep its root sinks ([sink]), its own
 * [guide] where it has one, its chain's [bones] and its [physics]; a [tied] lock leaves its tie rather than lying along the head. Two locks
 * alike build alike, so a build keeps those it made before.
 */
data class LockSpec(
    val index: Int,
    val root: Vec3,
    val out: Vec3,
    val growth: Vec3,
    val length: Float,
    val width: Float,
    val thickness: Float,
    val taper: Float,
    val section: LockSection,
    val tip: LockTip,
    val tipPoints: Int,
    val rise: Float,
    val sweep: Float,
    val fall: Float,
    val curl: Float,
    val wave: Float,
    val twist: Float,
    val clearance: Float,
    val sink: Float,
    val guide: List<Vec3>?,
    val bones: Int,
    val physics: LockPhysics,
    val tied: Boolean = false,
)

/** The locks [group] shows on [scalp], growing as [growth] says: every lock but those its overrides hide, each with its override over it. */
fun locksOf(group: LockGroup, scalp: Scalp, growth: LockGrowth = KindGrowth): List<LockSpec> {
    val roots = growth.roots(group)
    val rows = (roots.maxOfOrNull { it.row } ?: 0) + 1
    val random = Random(group.seed * 7919 + 17)
    val top = roots.maxOfOrNull { it.elevation } ?: 0f
    val jitters = List(roots.size) { (random.nextFloat() * 2f - 1f) * group.lengthJitter }
    return roots.mapIndexedNotNull { index, root ->
        val override = group.overrideOf(index)
        if (override?.hidden == true) return@mapIndexedNotNull null
        val place = scalp.placeAt(root.azimuth, root.elevation)
        val length = override?.length ?: (group.length * (1f + jitters[index]) * parted(group, root) - rowDrop(group, root, top, scalp)).coerceAtLeast(group.length * 0.2f)
        val thickness = override?.thickness ?: group.thickness
        val lift = BASE_CLEARANCE + group.layer * LAYER_LIFT + (rows - 1 - root.row) * ROW_LIFT + (index % 2) * STAGGER
        LockSpec(
            index = index,
            root = place.point,
            out = place.out,
            growth = growth.at(place, group),
            length = length,
            width = override?.width ?: group.width,
            thickness = thickness,
            taper = group.taper,
            section = override?.section ?: group.section,
            tip = override?.tip ?: group.tip,
            tipPoints = group.tipPoints,
            rise = override?.rise ?: (group.rise + growth.rise(place, group)).coerceAtMost(MOST_RISE),
            sweep = override?.sweep ?: (group.sweep + growth.sweep(place, group)).coerceIn(-MOST_SWEEP, MOST_SWEEP),
            fall = group.fall,
            curl = override?.curl ?: group.curl,
            wave = override?.wave ?: group.wave,
            twist = override?.twist ?: group.twist,
            clearance = thickness * lift,
            sink = thickness * ROOT_SINK,
            guide = override?.guide?.map { Vec3(it.x, it.y, it.z) },
            bones = override?.bones ?: bonesFor(length, scalp),
            physics = override?.physics ?: group.physics,
            tied = group.tie > 0f,
        )
    }
}

/** How many bones a lock [length] long holds: 3 for one shorter than half the head's reach, one more for each half reach past that, at most 6. */
fun bonesFor(length: Float, scalp: Scalp): Int {
    val reach = scalp.reachAt(0f, 45f).coerceAtLeast(1e-3f)
    return (3 + ((length / reach) - 0.5f).coerceAtLeast(0f).times(2f).roundToInt()).coerceIn(3, 6)
}

/**
 * How much shorter a lock of a lower row is than one of the top row, which roots at [top] degrees up the head: the way along the head
 * between them, so a group's rows end together. A crown's and strays' locks keep their length.
 */
private fun rowDrop(group: LockGroup, root: LockRoot, top: Float, scalp: Scalp): Float {
    if (group.kind == LockGroupKind.CROWN || group.kind == LockGroupKind.STRAYS || group.kind == LockGroupKind.TOP || root.row == 0) return 0f
    return (top - root.elevation).coerceAtLeast(0f) * DEGREES * scalp.reachAt(root.azimuth, root.elevation)
}

private const val DEGREES = (PI / 180.0).toFloat()

/** How much shorter a fringe lock over an eye is than one in the middle or at the sides, so the fringe parts to show the eyes. */
private const val FRINGE_PART = 0.25f

/** A fringe lock's share of its group's length by where across the front it roots: whole in the middle and at the ends, least over each eye. */
private fun parted(group: LockGroup, root: LockRoot): Float {
    if (group.kind != LockGroupKind.FRINGE) return 1f
    val (centre, _) = centreOf(group)
    val across = ((root.azimuth - centre) / FRINGE_HALF_REACH).coerceIn(-1f, 1f)
    return 1f - FRINGE_PART * sin(PI.toFloat() * abs(across))
}

/** How far round the head from the front the outer locks of a fringe of spread 1 root, in degrees. */
private const val FRINGE_HALF_REACH = 48f
