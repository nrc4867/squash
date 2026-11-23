package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.field.JawCut
import dev.chieppa.squash.geom.field.JawHalfSolid
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.MouthLiningSolid
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.ringMiddle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** What a run of a head built with its jaw open is: the snout half, the jaw half, or the mouth's lining. */
enum class JawSide { UPPER, JAW, LINING }

/** The joint a head's jaw turns at. */
const val JAW_JOINT = "jaw"

/** The run a jaw is cut from. */
private const val MUZZLE = "muzzle"

/** Where the mouth line starts at the muzzle's front, in its u. */
private const val MUZZLE_TIP = 0.985f

/** How far back past the jaw joint, in u, the mouth line runs. */
private const val BACK_PAST_JAW = 0.08f

/** The mouth line's places from the muzzle's tip back, each its distance in v from the underside's middle. */
private val FROM_UNDER = floatArrayOf(0.1f, 0.13f, 0.16f, 0.175f, 0.19f, 0.205f, 0.22f)

/** The rounding along the cut, as a share of the muzzle's least extent. */
private const val ROUNDING_SHARE = 0.05f

/** The blend the jaw's halves join the snout's by at the mouth's corners, as a share of the muzzle's least extent. */
private const val CHEEK_SHARE = 0.25f

/** How far the lining stands back inside the head, as a share of the muzzle's least extent. */
private const val LINING_INSET_SHARE = 0.18f

/** The u the lining places at, past every run's own. */
private const val LINING_U = 50f

/**
 * Whether [part] builds its jaw open, and at what angle: a head, not mirrored, with a muzzle run and a jaw joint off its axis is built
 * with its jaw turned open to the joint's upper limit, so a pose shuts it. Null for any other part.
 */
fun openJawJoint(part: PlacedPart): JointSpec? {
    if (part.role != Role.HEAD || part.mirrored) return null
    if (part.surface.patches.none { it.name == MUZZLE && isFieldRun(part, it) }) return null
    val joint = part.template.blocks.flatMap { it.joints() }.firstOrNull { it.name == JAW_JOINT && it.v != null } ?: return null
    return joint.takeIf { (it.limits?.max ?: 0f) > 0f }
}

/** The angle, in degrees, [part]'s jaw is built open at; 0 for a part built shut. */
fun jawBuiltDegrees(part: PlacedPart): Float = openJawJoint(part)?.limits?.max ?: 0f

/**
 * A head built with its jaw open: [part], placed at [origin], its [cut], how much the cut is rounded, [rounding], the snout's and the
 * jaw's halves among the field's members, [upperMembers] and [jawMembers], the head's runs before the cut, [hosts], and the mouth's
 * [lining], in its own space.
 */
class OpenJaw(
    val part: PartId,
    val cut: JawCut,
    val origin: Vec3,
    val rounding: Float,
    private val upperMembers: List<JawHalfSolid>,
    private val jawMembers: List<JawHalfSolid>,
    private val hosts: List<Solid>,
    private val lining: Solid,
) {
    /** Whether [local], a point of the head's own space as it lies shut, lies on the jaw's half. */
    fun onJaw(local: Vec3): Boolean = cut.region(local) < -rounding

    /**
     * Which of its terms shapes the head at body point [p] on the surface: positive where the cut does, on the cut's faces and the lining,
     * negative where the head's runs before the cut do, on the skin, zero along the middle of the lips' rounding, where the two give way
     * to each other; and never positive outside those runs, where the blend at the mouth's corners fills out the cheek. Read on the half
     * whose surface [p] lies on, taken back shut for the jaw's, from the shape alone; the lining is inside throughout.
     */
    fun inside(p: Vec3): Float {
        val q = p - origin
        val upper = upperMembers.minOf { abs(it.distance(q)) }
        val jaw = jawMembers.minOf { abs(it.distance(q)) }
        if (abs(lining.distance(q)) < minOf(upper, jaw)) return rounding
        val onUpper = upper <= jaw
        val at = if (onUpper) q else cut.shut(q)
        val cutTerm = if (onUpper) -cut.region(at) else cut.region(at)
        val skin = hosts.minOf { it.distance(at) }
        return minOf(cutTerm - skin, -skin)
    }
}

/**
 * [runs] and [members] with each head [openJawJoint] builds open split along its mouth line into a snout half and a jaw half turned open,
 * with a lining in the gap between them; [plain] are the runs' solids before any reach or cut. The heads split are in [OpenJaws.jaws].
 */
internal class OpenJaws(val runs: List<FieldRun>, val members: List<JoinedMember>, val jaws: List<OpenJaw>)

internal fun withOpenJaws(runs: List<FieldRun>, members: List<JoinedMember>, solids: RunSolids, plain: List<Solid>): OpenJaws {
    var outRuns = runs
    var outMembers = members
    val jaws = ArrayList<OpenJaw>()
    for (head in runs.map { it.part }.distinct()) {
        val joint = openJawJoint(head) ?: continue
        val opened = openOne(outRuns, outMembers, solids, plain, head, joint) ?: continue
        outRuns = opened.runs
        outMembers = opened.members
        jaws += opened.jaws
    }
    return OpenJaws(outRuns, outMembers, jaws)
}

private fun openOne(runs: List<FieldRun>, members: List<JoinedMember>, solids: RunSolids, plain: List<Solid>, head: PlacedPart, joint: JointSpec): OpenJaws? {
    val skullIndex = runs.indexOfFirst { it.part === head && it.first && it.jawSide == null }
    val muzzleIndex = runs.indexOfFirst { it.part === head && it.patch.name == MUZZLE && it.jawSide == null }
    if (skullIndex < 0 || muzzleIndex < 0 || skullIndex == muzzleIndex) return null
    val skullRun = runs[skullIndex]
    val muzzleRun = runs[muzzleIndex]
    val hingeRun = head.surface.patches.filter { it.slices != null }.let { swept -> swept.firstOrNull { it.covers(joint.u) } ?: swept.first() }
    val hinge = hingeRun.ringMiddle(joint.u)
    val axis = PartBuilder.axes(head.template).side.normalized()
    val tip = muzzleRun.patch.positionAt(MUZZLE_TIP, 0.5f) - hinge
    val forward = (tip - axis * (tip dot axis)).normalized()
    val up = forward cross axis
    val back = (joint.u + BACK_PAST_JAW).coerceAtMost(MUZZLE_TIP)
    val line = FROM_UNDER.indices.map { i ->
        val u = MUZZLE_TIP + (back - MUZZLE_TIP) * i / (FROM_UNDER.size - 1)
        val d = muzzleRun.patch.positionAt(u, 0.5f - FROM_UNDER[i]) - hinge
        val x = d dot forward
        val y = d dot up
        sqrt(x * x + y * y) to atan2(y, x)
    }.sortedBy { it.first }
    val built = requireNotNull(joint.limits).max * (PI.toFloat() / 180f)
    val cut = JawCut(hinge, axis, forward, line.map { it.first }.toFloatArray(), line.map { it.second }.toFloatArray(), line.first().first, (-PI / 2).toFloat(), built)
    val skull = members[skullIndex]
    val muzzle = members[muzzleIndex]
    val size = (muzzle.solid.high - muzzle.solid.low).let { minOf(it.x, it.y, it.z) }
    val rounding = size * ROUNDING_SHARE
    val cheek = size * CHEEK_SHARE
    fun halfOf(member: JoinedMember, upper: Boolean) = JawHalfSolid(member.solid, cut, upper, rounding)
    val out = members.toMutableList()
    val upperSkull = halfOf(skull, true)
    val upperMuzzle = halfOf(muzzle, true)
    out[skullIndex] = JoinedMember(upperSkull, skull.origin, skull.mirrored, skull.blend, skull.parent, skull.part, skull.ease, skull.angles)
    out[muzzleIndex] = JoinedMember(upperMuzzle, muzzle.origin, muzzle.mirrored, muzzle.blend, muzzle.parent, muzzle.part, muzzle.ease, muzzle.angles)
    val jawSkull = halfOf(skull, false)
    val jawMuzzle = halfOf(muzzle, false)
    val jawSkullIndex = out.size
    val jawSkullBare = JoinedMember(jawSkull, skull.origin, false, cheek, skullIndex, skull.part)
    out += JoinedMember(jawSkull, skull.origin, false, cheek, skullIndex, skull.part, 0f, solids.angles(jawSkullBare, out[skullIndex]))
    out += JoinedMember(jawMuzzle, muzzle.origin, false, muzzle.blend, jawSkullIndex, muzzle.part)
    val cornerBare = JoinedMember(jawMuzzle, muzzle.origin, false, cheek, muzzleIndex, muzzle.part)
    out += JoinedMember(jawMuzzle, muzzle.origin, false, cheek, muzzleIndex, muzzle.part, 0f, solids.angles(cornerBare, out[muzzleIndex]))
    val hosts = listOf(plain[skullIndex], plain[muzzleIndex])
    val lining = MouthLiningSolid(hosts, cut, size * LINING_INSET_SHARE, LINING_U)
    out += JoinedMember(lining, muzzle.origin, false, 0f, -1, muzzle.part)
    val outRuns = runs.toMutableList()
    outRuns[skullIndex] = skullRun.on(JawSide.UPPER)
    outRuns[muzzleIndex] = muzzleRun.on(JawSide.UPPER)
    outRuns += skullRun.on(JawSide.JAW)
    outRuns += muzzleRun.on(JawSide.JAW)
    outRuns += muzzleRun.on(JawSide.JAW)
    outRuns += muzzleRun.on(JawSide.LINING)
    val jaw = OpenJaw(requireNotNull(head.id), cut, head.origin, rounding, listOf(upperSkull, upperMuzzle), listOf(jawSkull, jawMuzzle), hosts, lining)
    return OpenJaws(outRuns, out, listOf(jaw))
}

private fun FieldRun.on(side: JawSide) = FieldRun(part, patchIndex, patch, first && side == JawSide.UPPER, side)
