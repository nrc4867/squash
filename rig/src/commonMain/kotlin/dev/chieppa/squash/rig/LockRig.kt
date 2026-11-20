package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.hair.LockChain
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3

/** The joint name of bone [k] of lock [index]'s chain, from 1 at its root. */
fun lockJoint(index: Int, k: Int): String = "lock-$index-$k"

/**
 * A lock's chain as the rig holds it, ready for a solver: its [group] and [index], its [bones] as places in [Rig.bones] from root to tip,
 * the bone it hangs from ([carrier], the head's), its rest pose as those bones stand, how it [physics] moves, and the chains of its group
 * either side of it it is tied to, by place in [Rig.chains] ([neighbours]).
 */
class RigChain(
    val part: PartId,
    val group: LockGroupId,
    val index: Int,
    val carrier: Int,
    val bones: List<Int>,
    val physics: LockPhysics,
    val neighbours: List<Int>,
)

/** The lock groups' parts of the rig, their bones and their chains. */
internal class LockRigs(val bones: List<Bone>, val parts: List<RigPart>, val chains: List<RigChain>)

private val UP = Vec3(0f, 1f, 0f)

/**
 * The rig of every lock group [character] holds, its bones numbered on after [body]'s: each lock a chain of its own bones along its guide,
 * the first hanging from the head's bone that moves the head most where the lock roots, the authored shape its rest; each vertex follows the
 * bone whose span holds it, shared with the one before it over the first half of that span, so the root follows the head. A group's band
 * rides the head's bone whole. See HLD: data-model, Hair locks.
 */
internal fun lockRigs(character: AssembledCharacter, rigged: List<RigPart>, body: List<Bone>): LockRigs {
    val bones = ArrayList<Bone>()
    val parts = ArrayList<RigPart>()
    val chains = ArrayList<RigChain>()
    for (placed in character.parts) {
        val placing = placed.locks ?: continue
        val id = placed.id ?: continue
        val head = rigged.firstOrNull { it.id == placing.head } ?: continue
        fun carrierAt(point: Vec3): Int = head.weights.of(nearestVertex(head, point)).maxByOrNull { it.second }?.first ?: 0
        if (placing.chains.isEmpty()) {
            val center = placed.box?.center ?: placed.origin
            parts += riding(placed, id, carrierAt(center))
            continue
        }
        val firstChain = chains.size
        val owned = HashMap<String, Pair<Int, List<Int>>>()
        for ((k, chain) in placing.chains.withIndex()) {
            val carrier = carrierAt(chain.joints.first())
            val first = body.size + bones.size
            bones += chainBones(id, chain, body[carrier].name)
            val indices = List(chain.bones) { first + it }
            owned[chain.patch] = carrier to indices
            val neighbours = placing.neighbours.mapNotNull { (a, b) -> if (a == k) firstChain + b else if (b == k) firstChain + a else null }
            chains += RigChain(id, placing.group, chain.index, carrier, indices, chain.physics, neighbours)
        }
        parts += weighted(placed, id, owned)
    }
    return LockRigs(bones, parts, chains)
}

/** The bones of [chain] on part [id], the first hanging from [carrier]. */
private fun chainBones(id: PartId, chain: LockChain, carrier: BoneName): List<Bone> = List(chain.bones) { k ->
    val from = chain.joints[k]
    val to = chain.joints[k + 1]
    val along = (to - from).let { if (it.length > 0f) it.normalized() else Vec3(0f, -1f, 0f) }
    val hinge = (along cross UP).let { if (it.length > 1e-4f) it.normalized() else Vec3(0f, 0f, 1f) }
    Bone(
        name = BoneName(id, lockJoint(chain.index, k + 1)),
        parent = if (k == 0) carrier else BoneName(id, lockJoint(chain.index, k)),
        pivot = from,
        direction = along,
        length = (to - from).length,
        hinge = hinge,
        limits = null,
        stretch = false,
    )
}

/** [placed] weighted to its chains: each patch's vertices along the chain [owned] names for it, by their u. */
private fun weighted(placed: PlacedPart, id: PartId, owned: Map<String, Pair<Int, List<Int>>>): RigPart {
    val mesh = placed.surface.mesh
    val bones = IntArray(mesh.vertexCount * MAX_INFLUENCES) { -1 }
    val weights = FloatArray(bones.size)
    val vertices = ArrayList<SeamVertex>(mesh.vertexCount)
    val anchors = ArrayList<Anchor>(mesh.vertexCount)
    var start = 0
    for (patch in placed.surface.patches) {
        val (carrier, chain) = owned[patch.name] ?: (0 to emptyList())
        for (local in 0 until patch.nu * patch.nv) {
            val vertex = start + local
            val u = patch.ringUs[local / patch.nv]
            if (chain.isEmpty()) {
                bones[vertex * MAX_INFLUENCES] = carrier
                weights[vertex * MAX_INFLUENCES] = 1f
            } else {
                val along = u.coerceIn(0f, 1f) * chain.size
                val k = along.toInt().coerceAtMost(chain.size - 1)
                val share = (0.5f + (along - k)).coerceAtMost(1f)
                bones[vertex * MAX_INFLUENCES] = chain[k]
                weights[vertex * MAX_INFLUENCES] = share
                if (share < 1f) {
                    bones[vertex * MAX_INFLUENCES + 1] = if (k == 0) carrier else chain[k - 1]
                    weights[vertex * MAX_INFLUENCES + 1] = 1f - share
                }
            }
            val v = (local % patch.nv).toFloat() / patch.segments
            vertices += SeamVertex(id, patch.name, u, v)
            anchors += Anchor(id, patch.name, u, v)
        }
        start += patch.nu * patch.nv
    }
    return RigPart(id, restOf(placed), mesh.indices, PartWeights(id, bones, weights), mesh, placed.origin, vertices, anchors)
}

/** [placed] riding [bone] whole. */
private fun riding(placed: PlacedPart, id: PartId, bone: Int): RigPart {
    val mesh = placed.surface.mesh
    val bones = IntArray(mesh.vertexCount * MAX_INFLUENCES) { if (it % MAX_INFLUENCES == 0) bone else -1 }
    val weights = FloatArray(bones.size) { if (it % MAX_INFLUENCES == 0) 1f else 0f }
    return RigPart(id, restOf(placed), mesh.indices, PartWeights(id, bones, weights), mesh, placed.origin)
}

private fun restOf(placed: PlacedPart): FloatArray {
    val mesh = placed.surface.mesh
    return FloatArray(mesh.vertexCount * 3) { i -> (placed.origin + mesh.position(i / 3)).let { p -> if (i % 3 == 0) p.x else if (i % 3 == 1) p.y else p.z } }
}

/** The vertex of [part] lying nearest [point] at rest. */
private fun nearestVertex(part: RigPart, point: Vec3): Int {
    var best = 0
    var least = Float.MAX_VALUE
    for (v in 0 until part.rest.size / 3) {
        val dx = part.rest[v * 3] - point.x
        val dy = part.rest[v * 3 + 1] - point.y
        val dz = part.rest[v * 3 + 2] - point.z
        val d = dx * dx + dy * dy + dz * dz
        if (d < least) {
            least = d
            best = v
        }
    }
    return best
}
