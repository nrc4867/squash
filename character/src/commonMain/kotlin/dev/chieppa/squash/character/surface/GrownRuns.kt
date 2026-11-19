package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.ClothHang
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.RunRings
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.field.SweptSolid
import dev.chieppa.squash.geom.field.refined
import dev.chieppa.squash.geom.field.withRadii
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How far apart in u a grown run's rings lie at most, so a band of ease as narrow as a cuff's is held by rings of its own. */
private const val FINEST_RING_U = 1f / CHART_ROWS

/** How near upright, as the mean upward part of its rings' axes, a run is for cloth over it to hang. */
private const val UPRIGHT = 0.8f

/** How much of its limit cloth hangs by at the sides, where an arm hangs beside it, against its front and back. */
private const val SIDE_HANG = 0.25f

/** How little of a direction round a ring lies level for a hang to be read along it. */
private const val LEAST_LEVEL = 0.2f

/** Runs a garment grows by an ease that varies or hangs, each kept while its solid and how it grows are the same, so its member reads the same. */
class GrownRuns {
    private var kept: Map<List<Any?>, Solid> = emptyMap()
    private var used = HashMap<List<Any?>, Solid>()

    /**
     * [solid], a run's, grown by how far [ease] stands off it past [base], which its member adds all round, and hanging by [hang] below
     * the highest ring the cloth covers from [from] to [to] along it where the run stands upright; null where it grows the same all round
     * and does not hang, or is not a swept run.
     */
    fun of(solid: Solid, ease: ClothEase?, hang: ClothHang?, base: Float, from: Float, to: Float, clearance: Clearance? = null): Solid? {
        val swept = solid as? SweptSolid ?: return null
        if (ease == null && hang == null) return null
        val key = listOf(Same(solid), ease, hang, base, from, to, clearance)
        return used.getOrPut(key) { kept[key] ?: grown(swept, ease, hang, base, from, to, clearance) ?: solid }.takeIf { it !== solid }
    }

    /** Keeps only what the last build used. */
    fun settle() {
        kept = used
        used = HashMap()
    }
}

/** [swept] grown ring by ring by [ease] past [base] and hung by [hang]; null where neither changes it. */
private fun grown(swept: SweptSolid, ease: ClothEase?, hang: ClothHang?, base: Float, from: Float, to: Float, clearance: Clearance?): SweptSolid? {
    val fine = swept.refined(FINEST_RING_U)
    val rings = fine.rings()
    val upright = hang != null && uprightOf(rings)
    if ((ease == null || ease.uniform) && !upright) return null
    val radii = rings.radii.copyOf()
    if (ease != null && !ease.uniform) grow(rings, radii, ease, base)
    if (upright) hung(rings, radii, checkNotNull(hang), from, to)
    clearance?.let { cleared(rings, radii, it) }
    return fine.withRadii(radii)
}

/** Grows each ring of [rings] in [radii] by how far [ease] stands off there past [base], read at the ring's u and each direction's v and side. */
private fun grow(rings: RunRings, radii: FloatArray, ease: ClothEase, base: Float) {
    val angles = RunRings.ANGLES
    for (i in 0 until rings.count) {
        if (!rings.hasV[i]) continue
        for (k in 0 until angles) {
            val forward = direction(rings, i, k)[0]
            val back = (1f - forward) / 2f
            radii[i * angles + k] += maxOf(ease.at(rings.us[i], rings.vs[i * angles + k], back) - base, 0f)
        }
    }
}

/**
 * Hangs [radii] below the highest ring of [rings] the cloth covers, from [from] to [to] along it: ring by ring down,
 * each direction's radius at least as far out, level, as the ring above it reaches that way, plus [ClothHang.flare] for each unit fallen,
 * and no more than [ClothHang.limit] past its own at the front and back, [SIDE_HANG] of it at the sides.
 */
private fun hung(rings: RunRings, radii: FloatArray, hang: ClothHang, from: Float, to: Float) {
    val angles = RunRings.ANGLES
    val covered = (0 until rings.count).filter { rings.us[it] in from..to && rings.hasV[it] }
    if (covered.isEmpty()) return
    val start = covered.maxBy { rings.middles[it * 3 + 1] }
    val down = if (rings.middles[(rings.count - 1) * 3 + 1] < rings.middles[1]) 1 else -1
    var above = start
    var i = start + down
    while (i in 0 until rings.count) {
        if (!rings.hasV[i]) break
        val fall = rings.middles[above * 3 + 1] - rings.middles[i * 3 + 1]
        for (k in 0 until angles) {
            val up = direction(rings, above, k)
            val held = radii[above * angles + k]
            val px = rings.middles[above * 3] + up[0] * held
            val pz = rings.middles[above * 3 + 2] + up[2] * held
            val d = direction(rings, i, k)
            val level = sqrt(d[0] * d[0] + d[2] * d[2])
            if (level < LEAST_LEVEL) continue
            val hx = d[0] / level
            val hz = d[2] / level
            val out = (px - rings.middles[i * 3]) * hx + (pz - rings.middles[i * 3 + 2]) * hz + hang.flare * maxOf(fall, 0f)
            val wanted = out / level
            val own = radii[i * angles + k]
            val limit = hang.limit * (SIDE_HANG + (1f - SIDE_HANG) * abs(hx))
            radii[i * angles + k] = maxOf(own, minOf(wanted, own + limit))
        }
        above = i
        i += down
    }
}

/** Keeps each ring's growth in [radii] past [rings]' own radius within [clearance] of the other parts near it. */
private fun cleared(rings: RunRings, radii: FloatArray, clearance: Clearance) {
    val angles = RunRings.ANGLES
    for (i in 0 until rings.count) {
        if (!rings.hasV[i]) continue
        for (k in 0 until angles) {
            val at = i * angles + k
            val own = rings.radii[at]
            val extra = radii[at] - own
            if (extra <= 0f) continue
            val d = direction(rings, i, k)
            val local = Vec3(rings.middles[i * 3] + d[0] * own, rings.middles[i * 3 + 1] + d[1] * own, rings.middles[i * 3 + 2] + d[2] * own)
            radii[at] = own + minOf(extra, clearance.room(local, extra))
        }
    }
}

/** Whether [rings]' axes point mostly up or down. */
private fun uprightOf(rings: RunRings): Boolean {
    var sum = 0f
    for (i in 0 until rings.count) sum += abs(rings.tangents[i * 3 + 1])
    return sum / rings.count >= UPRIGHT
}

/** The direction round ring [i] of [rings] its radius [k] is held in, in the run's own space: from its up toward its side. */
private fun direction(rings: RunRings, i: Int, k: Int): FloatArray {
    val angle = 2.0 * PI * k / RunRings.ANGLES
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    val tx = rings.tangents[i * 3]
    val ty = rings.tangents[i * 3 + 1]
    val tz = rings.tangents[i * 3 + 2]
    val ux = rings.ups[i * 3]
    val uy = rings.ups[i * 3 + 1]
    val uz = rings.ups[i * 3 + 2]
    val sx = uy * tz - uz * ty
    val sy = uz * tx - ux * tz
    val sz = ux * ty - uy * tx
    return floatArrayOf(ux * c + sx * s, uy * c + sy * s, uz * c + sz * s)
}

/** How far apart, in body units, grown cloth keeps from another part beside it at least. */
private const val CLEAR_MARGIN = 0.002f

/**
 * The other parts a run grown at [origin], [mirrored] when it is a pair's other side, keeps its cloth clear of: each of [others], a body
 * member, with the share of the gap to it the run's cloth may take, a half where that part's own cloth grows toward it too, else all of it.
 * A part the run's surface lies inside, as it does where the two join, is no obstacle there.
 */
class Clearance(private val origin: Vec3, private val mirrored: Boolean, private val others: List<Pair<JoinedMember, Float>>) {
    private val key: List<Any> = listOf(origin, mirrored, others.map { (member, share) -> member.key to share })

    /** How far the cloth may grow out from the run's surface point [local], in the run's own space, before it nears another part; [most] at most. */
    fun room(local: Vec3, most: Float): Float {
        val shifted = if (mirrored) Vec3(local.x, local.y, -local.z) else local
        val p = shifted + origin
        var room = most
        for ((member, share) in others) {
            if (member.boxDistance(p) * share - CLEAR_MARGIN >= room) continue
            val gap = member.own(p)
            if (gap <= 0f) continue
            room = minOf(room, maxOf(gap * share - CLEAR_MARGIN, 0f))
        }
        return room
    }

    override fun equals(other: Any?): Boolean = other is Clearance && other.key == key

    override fun hashCode(): Int = key.hashCode()
}
