package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.field.JawCut
import dev.chieppa.squash.geom.field.JawHalfSolid
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.MouthLiningSolid
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt
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

/** The fewest places of a drawn lip line that make a mouth line. */
private const val LEAST_LIP_PLACES = 2

/** The width the lips' edge is read across, for shading and where a lining's pieces overlap, as a share of the muzzle's least extent. */
private const val EDGE_SHARE = 0.05f

/** The blend the jaw's halves join the snout's by at the mouth's corners, as a share of the muzzle's least extent. */
private const val CHEEK_SHARE = 0.25f

/** How far the lining stands back inside the head, as a share of the muzzle's least extent. */
private const val LINING_INSET_SHARE = 0.18f

/** The u the lining places at, past every run's own. */
private const val LINING_U = 50f

/**
 * Whether [part] builds its jaw open, and at what angle: a head, not mirrored, with a muzzle run, a jaw joint off its axis and a lip line
 * drawn on its runs ([PlacedPart.lipLine]) is built with its jaw turned open to the joint's upper limit along that line, so a pose shuts
 * it. Null for any other part.
 */
fun openJawJoint(part: PlacedPart): JointSpec? {
    if (part.role != Role.HEAD || part.mirrored) return null
    if (part.surface.patches.none { it.name == MUZZLE && isFieldRun(part, it) }) return null
    if (lipPlaces(part).size < LEAST_LIP_PLACES) return null
    val joint = part.template.blocks.flatMap { it.joints() }.firstOrNull { it.name == JAW_JOINT && it.v != null } ?: return null
    return joint.takeIf { (it.limits?.max ?: 0f) > 0f }
}

/** The angle, in degrees, [part]'s jaw is built open at; 0 for a part built shut. */
fun jawBuiltDegrees(part: PlacedPart): Float = openJawJoint(part)?.limits?.max ?: 0f

/**
 * A head built with its jaw open: [part], placed at [origin], its [cut] along its drawn lip line, the width its lips' edge is read across, [edge], the blend its halves join by at the mouth's corners, [corner], the snout's and the
 * jaw's halves among the field's members, [upperMembers] and [jawMembers], the head's runs before the cut, [hosts], and the mouth's
 * [lining], in its own space.
 */
class OpenJaw(
    val part: PartId,
    val cut: JawCut,
    val origin: Vec3,
    val edge: Float,
    val corner: Float,
    private val upperMembers: List<JawHalfSolid>,
    private val jawMembers: List<JawHalfSolid>,
    private val hosts: List<Solid>,
    private val lining: Solid,
) {
    /**
     * How much of the jaw's turn body point [p] on the surface takes: all of it on the jaw's half, none on the snout's, by which half is
     * nearer. Only in the blend that joins them at the mouth's corners does it ease between, [corner] wide, narrowing to none [corner]
     * past them, so the lips either side of the cut each turn whole and meet where they were cut.
     */
    fun jawShare(p: Vec3): Float {
        val q = p - origin
        val upper = upperMembers.minOf { abs(it.distance(q)) }
        val jaw = jawMembers.minOf { abs(it.distance(q)) }
        val width = corner * (1f - cut.pastCorner(q) / corner).coerceIn(0f, 1f)
        if (width <= 0f) return if (jaw < upper) 1f else 0f
        val x = (0.5f + (upper - jaw) / (2f * width)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Whether [local], a point of the head's own space as it lies shut, lies on the jaw's half. */
    fun onJaw(local: Vec3): Boolean = cut.region(local) < -edge

    /**
     * Which of its terms shapes the head at body point [p] on the surface: positive where the cut does, on the cut's faces and the lining,
     * negative where the head's runs before the cut do, on the skin, zero along the lips' edge, where the two meet; and never positive outside those runs, where the blend at the mouth's corners fills out the cheek. Read on the half
     * whose surface [p] lies on, taken back shut for the jaw's, from the shape alone; the lining is inside throughout.
     */
    fun inside(p: Vec3): Float {
        val q = p - origin
        val upper = upperMembers.minOf { abs(it.distance(q)) }
        val jaw = jawMembers.minOf { abs(it.distance(q)) }
        if (abs(lining.distance(q)) < minOf(upper, jaw)) return edge
        val onUpper = upper <= jaw
        val at = if (onUpper) q else cut.shut(q)
        val cutTerm = if (onUpper) -cut.region(at) else cut.region(at)
        val skin = hosts.minOf { it.distance(at) }
        return minOf(cutTerm - skin, -skin, cut.aboveJawsUnderside(q))
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
    val built = jawCutParts(head.template, head.surface.patches, lipPlaces(head), joint) ?: return null
    val cut = solids.kept(built.key) { built.cut() }
    val skull = members[skullIndex]
    val muzzle = members[muzzleIndex]
    val size = (muzzle.solid.high - muzzle.solid.low).let { minOf(it.x, it.y, it.z) }
    val edge = size * EDGE_SHARE
    val cheek = size * CHEEK_SHARE
    fun halfOf(member: JoinedMember, upper: Boolean) = solids.kept(listOf(Same(member.solid), Same(cut), upper)) { JawHalfSolid(member.solid, cut, upper, rounding = 0f) }
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
    val lining = solids.kept(hosts.map { Same(it) } + listOf(Same(cut), size)) { MouthLiningSolid(hosts, cut, size * LINING_INSET_SHARE, LINING_U) }
    out += JoinedMember(lining, muzzle.origin, false, 0f, -1, muzzle.part)
    val outRuns = runs.toMutableList()
    outRuns[skullIndex] = skullRun.on(JawSide.UPPER)
    outRuns[muzzleIndex] = muzzleRun.on(JawSide.UPPER)
    outRuns += skullRun.on(JawSide.JAW)
    outRuns += muzzleRun.on(JawSide.JAW)
    outRuns += muzzleRun.on(JawSide.JAW)
    outRuns += muzzleRun.on(JawSide.LINING)
    val jaw = OpenJaw(requireNotNull(head.id), cut, head.origin, edge, cheek, listOf(upperSkull, upperMuzzle), listOf(jawSkull, jawMuzzle), hosts, lining)
    return OpenJaws(outRuns, out, listOf(jaw))
}

/** Where [part]'s lip line lies in its own space, place by place, leaving out any on a patch it does not have. */
private fun lipPlaces(part: PlacedPart): List<Vec3> = lipPlaces(part.surface.patches, part.lipLine)

/** Where the lip line [lipLine] lies on [patches], in their own space, place by place, leaving out any on a patch they do not have. */
fun lipPlaces(patches: List<Patch>, lipLine: List<Anchor>): List<Vec3> = lipLine.mapNotNull { anchor ->
    patches.firstOrNull { it.name == anchor.patch && it.slices != null }?.positionAt(anchor.u, anchor.v)
}

/** The head's outward normal at each place of the lip line [lipLine] on [patches], as [lipPlaces] lists them. */
fun lipNormals(patches: List<Patch>, lipLine: List<Anchor>): List<Vec3> = lipLine.mapNotNull { anchor ->
    patches.firstOrNull { it.name == anchor.patch && it.slices != null }?.surfaceAt(anchor.u, anchor.v)?.normal
}

/** What a jaw's cut is made from, and [key], what it is kept by while unchanged. */
internal class JawCutParts(val hinge: Vec3, val axis: Vec3, val forward: Vec3, val radii: List<Float>, val phis: List<Float>, val built: Float) {
    val key: List<Any> get() = listOf(hinge, axis, forward, radii, phis, built)

    fun cut() = JawCut(hinge, axis, forward, radii.toFloatArray(), phis.toFloatArray(), radii.first(), (-PI / 2).toFloat(), built)
}

/**
 * The parts of the cut a head of [template] with [patches] is split by along the lip line at [lips], its jaw turned about [joint]: the
 * hinge at the joint's place on the run covering it, the side axis, forward toward the muzzle's tip, and the lip line's places as distances
 * from the hinge and angles about it, sorted by distance. Null with no muzzle run.
 */
internal fun jawCutParts(template: PartTemplate, patches: List<Patch>, lips: List<Vec3>, joint: JointSpec): JawCutParts? {
    val muzzle = patches.firstOrNull { it.name == MUZZLE && it.slices != null } ?: return null
    if (lips.size < LEAST_LIP_PLACES) return null
    val hingeRun = patches.filter { it.slices != null }.let { swept -> swept.firstOrNull { it.covers(joint.u) } ?: swept.first() }
    val hinge = hingeRun.ringMiddle(joint.u)
    val axis = PartBuilder.axes(template).side.normalized()
    val tip = muzzle.ringMiddle(1f) - hinge
    val forward = (tip - axis * (tip dot axis)).normalized()
    val up = forward cross axis
    val line = lips.map { place ->
        val d = place - hinge
        val x = d dot forward
        val y = d dot up
        sqrt(x * x + y * y) to atan2(y, x)
    }.sortedBy { it.first }
    val built = requireNotNull(joint.limits).max * (PI.toFloat() / 180f)
    return JawCutParts(hinge, axis, forward, line.map { it.first }, line.map { it.second }, built)
}

/**
 * The cut a head of [template] with [patches], built as [role], [mirrored] or not, with its lip line at [lipLine], is built open along, as
 * [openJawJoint] decides for a placed part; null for a head built shut.
 */
fun openJawCut(template: PartTemplate, role: Role?, mirrored: Boolean, patches: List<Patch>, lipLine: List<Anchor>): JawCut? {
    if (role != Role.HEAD || mirrored || template.surface != SurfaceMethod.FIELD) return null
    if (patches.none { it.name == MUZZLE && isRun(it) && !isSeparatePatch(template, it) }) return null
    val lips = lipPlaces(patches, lipLine)
    val joint = template.blocks.flatMap { it.joints() }.firstOrNull { it.name == JAW_JOINT && it.v != null }?.takeIf { (it.limits?.max ?: 0f) > 0f } ?: return null
    return jawCutParts(template, patches, lips, joint)?.cut()
}

private fun FieldRun.on(side: JawSide) = FieldRun(part, patchIndex, patch, first && side == JawSide.UPPER, side)
