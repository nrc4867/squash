package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.surface.isRun
import dev.chieppa.squash.document.KeyFeature
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3

/** What a muzzle, an ear and a nose are named on a head: its run or piece, or the socket a part sits on. */
private const val MUZZLE = "muzzle"
private const val EAR = "ear"
private const val NOSE = "nose"

/** What separates a piece's kind from its name, as in pad:nose. */
private const val PIECE_SEPARATOR = ':'

/** The heads, features, lines of sockets and twins of an assembled [character]. */
class CharacterKeyLookup(private val character: AssembledCharacter) : KeyLookup {
    private val byId: Map<PartId, PlacedPart> = character.parts.filter { it.id != null }.associateBy { requireNotNull(it.id) }

    override val heads: List<PartId> = byId.values.filter { (it.role ?: it.template.role) == Role.HEAD }.map { requireNotNull(it.id) }

    override fun feature(head: PartId, feature: KeyFeature): List<KeyElement> {
        val part = byId[head] ?: return emptyList()
        if (feature == KeyFeature.HEAD) return listOf(KeyElement(head))
        val (piece, socket) = when (feature) {
            KeyFeature.MUZZLE -> MUZZLE to MUZZLE
            KeyFeature.NOSE -> NOSE to NOSE
            KeyFeature.EARS -> null to EAR
            KeyFeature.HEAD -> return emptyList()
        }
        piece?.let { name -> part.surface.patches.firstOrNull { it.named(name) }?.let { return listOf(KeyElement(head, it.name)) } }
        return byId.values.filter { it.socket?.parent == head && it.socket.socket == socket }.map { KeyElement(requireNotNull(it.id)) }
    }

    override fun headOf(part: PartId): PartId? {
        var at = byId[part]
        val seen = HashSet<PartId>()
        while (at != null) {
            val id = requireNotNull(at.id)
            if (!seen.add(id)) return null
            if (id in heads) return id
            at = at.socket?.parent?.let(byId::get)
        }
        return null
    }

    override fun twinOf(part: PartId): PartId? {
        val placed = byId[part] ?: return null
        val side = placed.side ?: return null
        val socket = placed.socket ?: return null
        val parentTwin = twinOf(socket.parent)
        return byId.values.firstOrNull { other ->
            other.side != null && other.side != side && other.template.id == placed.template.id &&
                other.socket?.socket == socket.socket && (other.socket?.parent == socket.parent || other.socket?.parent == parentTwin)
        }?.id
    }

    /** The placed part [id]. */
    fun part(id: PartId): PlacedPart? = byId[id]
}

/** Whether this patch is the run or piece called [name]: by its whole name, or by the name after its kind, as pad:nose is nose. */
internal fun Patch.named(name: String): Boolean = this.name == name || this.name.substringAfter(PIECE_SEPARATOR, "") == name

/** The middle of ring [ring] of this patch, in its own space. */
internal fun Patch.ringMiddle(ring: Int): Vec3 {
    var sum = Vec3.ZERO
    for (j in 0 until nv) {
        val at = (ring * nv + j) * Patch.COMPONENTS
        sum += Vec3(points[at], points[at + 1], points[at + 2])
    }
    return sum * (1f / nv)
}

/** The middle of all of this patch's points, in its own space. */
internal fun Patch.middle(): Vec3 {
    var sum = Vec3.ZERO
    val count = nu * nv
    for (i in 0 until count) sum += Vec3(points[i * Patch.COMPONENTS], points[i * Patch.COMPONENTS + 1], points[i * Patch.COMPONENTS + 2])
    return sum * (1f / count)
}

/** The run of [part] whose middle lies nearest [point], in body space, or null for a part with no run. */
internal fun hostRun(part: PlacedPart, point: Vec3): Patch? =
    part.surface.patches.filter(::isRun).minByOrNull { run ->
        (0 until run.nu).minOf { ring -> ((part.origin + part.surface.shown(run.ringMiddle(ring))) - point).length }
    }
