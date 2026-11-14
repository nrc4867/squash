package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh
import kotlin.math.abs
import kotlin.math.round

/** How far inside a range, in u, a point past the end of a part the range reaches counts as lying. */
private const val WHOLE_END = 1f

/** How far out along a run past an open end, in the garment's ease, a part coming out through the end is looked for over a point. */
private const val OVER_EASES = 8f

/** How much nearer a part coming out through an end, in the garment's ease, the way out from a point must come for the part to overhang it. */
private const val APPROACH_EASES = 0.5f

/** How far inside a hem reaching past its part's end the hem is cut, in the reach's cells, so it lies on a hanging shell's wall above its end. */
private const val HEM_INSET_CELLS = 1.5f

/**
 * How far inside [worn]'s coverage a vertex of a garment's or a body's mesh meshed from [field] lies, in body units, held within
 * [reach]: for each run it holds a share of, the distance into its branch's u range, and its v range for a branch part of the way round,
 * measured along the run, a range ending at an end of its part covering that end whole, unless a part the garment does not cover comes
 * out through that end, where the range ends a little short of the end's ring and of the plane across the run there within the
 * garment's ease of a part coming out through an end, [ends], their members as placed, so the garment grown over the run's closed end
 * stays open round the part coming out and closes over the rest of the end, and a range
 * reaching past it, onto a shell hanging free, ending a little short of where it says, on the shell's wall; a run of a part the garment does not cover,
 * or a part's later run, which its branch does not read, counts as [reach] outside. The share-weighted sum of those is one smooth function, zero along the hem, the neck opening and the cuffs. Where a part comes out through a hole of the garment, [holes],
 * its members as placed, the coverage is at most how far the vertex lies outside them, less [clear]. Safe to read from many threads at once.
 */
class Coverage(
    private val field: CharacterField,
    private val worn: WornCover,
    private val reach: Float,
    private val holes: List<JoinedMember> = emptyList(),
    private val clear: Float = 0f,
    private val ends: List<JoinedMember> = emptyList(),
) {
    private val branches = Array(field.runs.size) { field.runs[it].takeIf { run -> run.first }?.let { run -> worn.branchOf(run.partId) } }
    private val scales = Array(field.runs.size) { if (branches[it] != null) RunScales(field.runs[it].patch) else null }
    private val open = Array(field.runs.size) { if (branches[it] != null) worn.openEnds[field.runs[it].partId] else null }
    private val hemInset = reach / COVERAGE_CELLS * HEM_INSET_CELLS
    private val lowEnds = Array(field.runs.size) { if (open[it]?.low == true) RunEnd.of(field.runs[it].patch, high = false) else null }
    private val highEnds = Array(field.runs.size) { if (open[it]?.high == true) RunEnd.of(field.runs[it].patch, high = true) else null }

    /** The coverage at vertex [v] of [mesh]. */
    fun at(mesh: SurfaceMesh, v: Int): Float {
        var sum = 0f
        var total = 0f
        for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member < 0) continue
            val share = mesh.heldShares[v * HELD + k]
            val branch = branches[member]
            val inside = if (branch == null) {
                -reach
            } else {
                val u = mesh.heldU[v * HELD + k]
                val scale = checkNotNull(scales[member])
                val stretch = scale.along(u)
                val ends = open[member]
                val below = if (branch.from == 0f && ends?.low != true) WHOLE_END * stretch else (u - branch.from) * stretch - if (branch.from < 0f || ends?.low == true) hemInset else 0f
                val above = if (branch.to == 1f && ends?.high != true) WHOLE_END * stretch else (branch.to - u) * stretch - if (branch.to > 1f || ends?.high == true) hemInset else 0f
                val at = Vec3(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2])
                val local = field.field.members[member].local(at)
                val run = field.field.members[member]
                val beforeLow = lowEnds[member]?.let { endInside(it, local, run, branch.ease, at) } ?: below
                val beforeHigh = highEnds[member]?.let { endInside(it, local, run, branch.ease, at) } ?: above
                val along = minOf(below, above, beforeLow, beforeHigh)
                if (branch.allRound) along else minOf(along, aroundInside(mesh.heldV[v * HELD + k], branch) * scale.around(u))
            }
            sum += share * inside.coerceIn(-reach, reach)
            total += share
        }
        val covered = if (total > 0f) sum / total else -reach
        if (holes.isEmpty() || covered <= -reach) return covered
        val at = Vec3(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2])
        var outside = reach
        for (hole in holes) if (hole.boxDistance(at) < reach + clear) outside = minOf(outside, hole.own(at) - clear)
        return minOf(covered, outside.coerceIn(-reach, reach))
    }

    /**
     * How far inside the coverage body point [at], [local] in [run]'s part's space, lies by [end] of the run: short of the plane across the
     * end, or under a part coming out through an end that overhangs it, as a head sitting on the run's end does, and clear of that part.
     * So the garment ends at the plane round a part that rises from the end, as a neck does, and tucks under one that sits over it. A
     * part overhangs a point by how much nearer it the way out from the point comes than the point lies, less [APPROACH_EASES] of the
     * ease, read every half ease out to [OVER_EASES]; a part rising straight up beside the point comes no nearer.
     */
    private fun endInside(end: RunEnd, local: Vec3, run: JoinedMember, ease: Float, at: Vec3): Float {
        val plane = -end.past(local) - hemInset
        if (ends.isEmpty() || plane >= reach || ease <= 0f) return plane
        val out = if (run.mirrored) end.out.mirrored else end.out
        var under = -reach
        for (part in ends) {
            val own = part.own(at)
            var nearest = own
            var step = ease / 2f
            while (step <= ease * OVER_EASES) {
                nearest = minOf(nearest, part.own(at + out * step))
                step += ease / 2f
            }
            under = maxOf(under, minOf(own - hemInset, own - nearest - ease * APPROACH_EASES))
        }
        return maxOf(plane, under)
    }

    /** Fills [out] at vertices [from] until [to] of [mesh]. */
    fun fill(mesh: SurfaceMesh, out: FloatArray, from: Int, to: Int) {
        for (v in from until to) out[v] = at(mesh, v)
    }
}

/** Whether [anchor], on [surface], its part's, lies in this cover's range on its part's first run, whose (u, v) a branch reads. */
fun GarmentCover.coversAnchor(anchor: Anchor, surface: PartSurface): Boolean {
    val branch = branches[anchor.part] ?: return false
    if (surface.patches.getOrNull(firstRunIndex(surface.patches))?.name != anchor.patch) return false
    if (anchor.u < branch.from || anchor.u > branch.to) return false
    return branch.allRound || aroundInside(anchor.v, branch) >= 0f
}

/** How far [v] lies inside [branch]'s range round, wrapping once. */
private fun aroundInside(v: Float, branch: GarmentBranch): Float {
    val middle = (branch.fromV + branch.toV) / 2f
    val half = (branch.toV - branch.fromV) / 2f
    var off = v - middle
    off -= round(off)
    return half - abs(off)
}

/** Where an end ring of a run lies, in its part's space, and the way out of the run through it. */
private class RunEnd(private val center: Vec3, val out: Vec3) {
    /** How far [local], a point in the part's space, lies past the end's ring, out along the run. */
    fun past(local: Vec3): Float = (local - center) dot out

    companion object {
        /** How many rings in from an end the run's way out of it is read from. */
        private const val AXIS_RINGS = 3

        fun of(patch: Patch, high: Boolean): RunEnd? {
            if (patch.nu < 2) return null
            val end = if (high) patch.nu - 1 else 0
            val inner = if (high) maxOf(0, end - AXIS_RINGS) else minOf(patch.nu - 1, AXIS_RINGS)
            val center = ringMiddle(patch, end)
            val out = center - ringMiddle(patch, inner)
            if (out.length <= 0f) return null
            return RunEnd(center, out.normalized())
        }

        private fun ringMiddle(patch: Patch, ring: Int): Vec3 {
            var sum = Vec3.ZERO
            for (c in 0 until patch.nv) sum += patch.point(patch.index(ring, c))
            return sum * (1f / patch.nv)
        }
    }
}
