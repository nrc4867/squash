package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.smoothstep
import dev.chieppa.squash.geom.valueNoise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.roundToInt

/** How many of a garment's cells one wrinkle, one hanging fold and one gather above a hem take, peak to peak. */
private const val WRINKLE_CELLS = 5f
private const val DRAPE_CELLS = 10f
private const val GATHER_CELLS = 5f

/** How far a wrinkle, a fold and a gather wander off their even spacing, in radians of their wave at the noise's most. */
private const val WRINKLE_WANDER = 2.4f
private const val DRAPE_WANDER = 1.6f
private const val GATHER_WANDER = 1.2f

/** How much fit, in the body's cells, makes a garment fully loose, and the least and most looseness. */
private const val LOOSE_CELLS = 2.5f
private const val LEAST_LOOSE = 0.25f
private const val MOST_LOOSE = 1.5f

/** Where up a torso a loose top's hanging folds fade out, as shares of its length: they hang from the chest down. */
private const val DRAPE_TOP_FROM = 0.55f
private const val DRAPE_TOP_TO = 0.85f

/** How far above a hem gathers reach, in gathers, and where they start above it, past the hem's own band. */
private const val GATHER_REACH = 1.4f
private const val GATHER_FROM = 0.25f
private const val GATHER_FULL = 0.7f

/** The least number of hanging folds or gathers round a part. */
private const val LEAST_FOLDS = 3

/** Roles whose cloth hangs in folds from the chest down. */
private val DRAPED = setOf(Role.BODY, Role.SPINE)

/** Seeds that keep each pattern's noise its own. */
private const val SEED_WRINKLE = 11
private const val SEED_DRAPE = 23
private const val SEED_DRAPE_GAPS = 37
private const val SEED_GATHER = 53

/** The noise's own spacing for each pattern, in that pattern's waves. */
private const val WANDER_WAVES = 1.5f
private const val GAP_WAVES = 4f

/** Where the noise leaves a gap between hanging folds, eased in over this stretch of it, so most folds run long. */
private const val GAP_FROM = -0.55f
private const val GAP_TO = -0.1f

/**
 * What a garment's cloth does where it bunches and hangs, read once at rest, a value each vertex of its drawn mesh: the [wrinkle] wave's
 * phase along the limb, so its crests run across it, and the hanging folds down a loose top and the gathers above a hem as an envelope
 * and a phase round the part each ([drape], [drapePhase], [gather], [gatherPhase]); with its [looseness], from its fit, and its mesh's
 * [cell]. Each phase wanders by smooth noise in space, so no two waves repeat. See HLD: look, Creases and wrinkles.
 */
class ClothPattern(
    val wrinkle: FloatArray,
    val drape: FloatArray,
    val drapePhase: FloatArray,
    val gather: FloatArray,
    val gatherPhase: FloatArray,
    val looseness: Float,
    val cell: Float,
) {
    companion object {
        /** [garment]'s pattern at rest, weighed by [skin] on [rig], its looseness its fit's against the body's [bodyCell]. */
        fun of(rig: Rig, garment: SurfaceGarment, skin: SurfaceSkin, bodyCell: Float): ClothPattern {
            val surface = garment.surface
            val rest = surface.render
            val count = minOf(rest.vertexCount, skin.vertexCount)
            val cell = surface.mesh.cell
            val stride = RenderMesh.FLOATS_PER_VERTEX
            val frames = PartFrames(rig)
            val chain = chainOffsets(rig)
            val roles = rig.parts.associate { it.id to it.role }
            val positions = Array(count) { rest.vertices.vec(it * stride) }
            val owners = Array(count) { v -> strongestBone(skin, v)?.let { rig.bones[it].name.part } }
            val distances = garment.drawnCoverage
            val wrinkleWave = WRINKLE_CELLS * cell
            val drapeWave = DRAPE_CELLS * cell
            val gatherWave = GATHER_CELLS * cell
            val wrinkle = FloatArray(count) { v ->
                val along = alongChain(rig, skin, v, positions[v], chain)
                2f * PI.toFloat() * along / wrinkleWave + WRINKLE_WANDER * valueNoise(positions[v], wrinkleWave * WANDER_WAVES, SEED_WRINKLE)
            }
            val drapeFolds = foldsRound(frames, owners, positions, drapeWave)
            val gatherFolds = foldsRound(frames, owners, positions, gatherWave)
            val below = hemBelow(garment, frames, owners, positions, count)
            val drape = FloatArray(count)
            val drapePhase = FloatArray(count)
            val gather = FloatArray(count)
            val gatherPhase = FloatArray(count)
            for (v in 0 until count) {
                val part = owners[v] ?: continue
                val frame = frames.of(part) ?: continue
                val p = positions[v]
                val distance = distances.getOrElse(v) { 0f }
                val round = frame.angleRound(p)
                if (roles[part] in DRAPED) {
                    val height = frame.heightShare(p)
                    drape[v] = (1f - smoothstep(DRAPE_TOP_FROM, DRAPE_TOP_TO, height)) * smoothstep(0.2f * drapeWave, drapeWave, distance) *
                        smoothstep(GAP_FROM, GAP_TO, valueNoise(p, drapeWave * GAP_WAVES, SEED_DRAPE_GAPS))
                    drapePhase[v] = (drapeFolds[part] ?: LEAST_FOLDS) * round + DRAPE_WANDER * valueNoise(p, drapeWave * WANDER_WAVES, SEED_DRAPE)
                }
                if (below[v] && distance > 0f) {
                    gather[v] = exp(-distance / (GATHER_REACH * gatherWave)) * smoothstep(GATHER_FROM * gatherWave, GATHER_FULL * gatherWave, distance)
                    gatherPhase[v] = (gatherFolds[part] ?: LEAST_FOLDS) * round + GATHER_WANDER * valueNoise(p, gatherWave * WANDER_WAVES, SEED_GATHER)
                }
            }
            val garmentFit = garment.cover.garment.fit ?: garment.cover.branches.values.maxOfOrNull { it.ease } ?: 0f
            val looseness = (garmentFit / (LOOSE_CELLS * bodyCell)).coerceIn(LEAST_LOOSE, MOST_LOOSE)
            return ClothPattern(wrinkle, drape, drapePhase, gather, gatherPhase, looseness, cell)
        }
    }
}

/** The bone weighing vertex [v] most, or null where none does. */
internal fun strongestBone(skin: SurfaceSkin, v: Int): Int? {
    var best = -1
    var most = 0f
    for (slot in 0 until MAX_INFLUENCES) {
        val b = skin.bones[v * MAX_INFLUENCES + slot]
        val w = skin.weights[v * MAX_INFLUENCES + slot]
        if (b >= 0 && w > most) {
            best = b
            most = w
        }
    }
    return best.takeIf { it >= 0 }
}

/** How far down its chain of bones from the rig's root each bone's pivot lies, along the bones' spans. */
private fun chainOffsets(rig: Rig): FloatArray {
    val offsets = FloatArray(rig.bones.size)
    for (i in rig.bones.indices) {
        val parent = rig.parents[i]
        offsets[i] = if (parent < 0) 0f else offsets[parent] + (rig.bones[i].pivot - rig.bones[parent].pivot).length
    }
    return offsets
}

/** How far along the rig's chains of bones [p], vertex [v], lies, blended by its weights, so the distance runs on across each joint. */
private fun alongChain(rig: Rig, skin: SurfaceSkin, v: Int, p: Vec3, chain: FloatArray): Float {
    var total = 0f
    var weight = 0f
    for (slot in 0 until MAX_INFLUENCES) {
        val b = skin.bones[v * MAX_INFLUENCES + slot]
        if (b < 0) continue
        val w = skin.weights[v * MAX_INFLUENCES + slot]
        val bone = rig.bones[b]
        val direction = if (bone.direction.length > 0f) bone.direction.normalized() else Vec3.ZERO
        total += w * (((p - bone.pivot) dot direction) + chain[b])
        weight += w
    }
    return if (weight > 0f) total / weight else 0f
}

/** How many folds of [wave] fit round each part, by the middle distance of [positions] from its axis. */
private fun foldsRound(frames: PartFrames, owners: Array<PartId?>, positions: Array<Vec3>, wave: Float): Map<PartId, Int> =
    positions.indices.groupBy { owners[it] }.mapNotNull { (part, vertices) ->
        val frame = part?.let(frames::of) ?: return@mapNotNull null
        val radii = vertices.map { frame.distanceFromAxis(positions[it]) }.sorted()
        part to maxOf(LEAST_FOLDS, (2f * PI.toFloat() * radii[radii.size / 2] / wave).roundToInt())
    }.toMap()

/**
 * Whether the cloth's nearest edge lies below each vertex at rest, so cloth gathers above it: the hem of a top, a cuff, a trouser hem,
 * not a neck opening or a waist. Below is down where its part rests more upright than flat, and toward its far end otherwise. Read from
 * how the garment's distance inside its cloth grows along each edge of its mesh.
 */
private fun hemBelow(garment: SurfaceGarment, frames: PartFrames, owners: Array<PartId?>, positions: Array<Vec3>, count: Int): BooleanArray {
    val mesh = garment.surface.mesh
    val corners = mesh.vertexCount
    val distances = garment.drawnCoverage
    val rising = FloatArray(count)
    val triangles = mesh.triangles
    val ups = Array(count) { v -> owners[v]?.let(frames::of)?.up ?: Vec3.Y }
    for (t in 0 until triangles.size / 3) {
        for (k in 0 until 3) {
            val a = triangles[t * 3 + k]
            val b = triangles[t * 3 + (k + 1) % 3]
            if (a >= count || b >= count || a >= distances.size || b >= distances.size) continue
            val step = positions[b] - positions[a]
            val grows = distances[b] - distances[a]
            rising[a] += grows * (step dot ups[a])
            rising[b] += grows * (step dot ups[b])
        }
    }
    for (v in corners until count) rising[v] = quadMean(rising, triangles, v - corners)
    return BooleanArray(count) { rising[it] > 0f }
}

/**
 * Each part's own frame at rest: the pivot and direction of its first bone, its length along its bones, and which way its cloth hangs
 * down from, [PartFrame.up].
 */
private class PartFrames(rig: Rig) {
    private val frames: Map<PartId, PartFrame> = buildMap {
        val lengths = rig.bones.groupBy { it.name.part }.mapValues { (_, bones) -> bones.sumOf { it.length.toDouble() }.toFloat() }
        for (bone in rig.bones) {
            if (bone.name.part in this || bone.direction.length <= 0f) continue
            put(bone.name.part, PartFrame(bone.pivot, bone.direction.normalized(), lengths.getValue(bone.name.part)))
        }
    }

    fun of(part: PartId): PartFrame? = frames[part]
}

/** A part's frame at rest: from [origin] along [direction] for [length]. */
private class PartFrame(val origin: Vec3, val direction: Vec3, val length: Float) {
    private val front = (Vec3.X - direction * (Vec3.X dot direction)).let { if (it.length > MIN_ACROSS) it.normalized() else (Vec3.Y - direction * (Vec3.Y dot direction)).normalized() }
    private val side = direction cross front

    /** Which way is up for cloth hanging on this part at rest: the body's up for a part resting more upright than flat, else back along it. */
    val up: Vec3 = if (abs(direction dot Vec3.Y) >= UPRIGHT) Vec3.Y else direction * -1f

    /** The angle round the part's axis of [p], in radians, from its front. */
    fun angleRound(p: Vec3): Float {
        val rel = p - origin
        return atan2(rel dot side, rel dot front)
    }

    fun distanceFromAxis(p: Vec3): Float {
        val rel = p - origin
        return (rel - direction * (rel dot direction)).length
    }

    /** How far up the part [p] lies, as a share of its length from its first pivot. */
    fun heightShare(p: Vec3): Float = if (length > 0f) ((p - origin) dot direction) / length else 0f

    private companion object {
        const val MIN_ACROSS = 0.1f
        const val UPRIGHT = 0.5f
    }
}
