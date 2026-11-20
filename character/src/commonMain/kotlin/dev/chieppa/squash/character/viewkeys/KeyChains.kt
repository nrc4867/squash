package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.surface.isRun
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3

/** A move that reaches only some of the surface of [part]: its [area]'s, eased out past its edge, or the whole part's, swelling it. */
internal class SoftMove(val part: PartId, val area: AreaMask?, val map: KeyAffine, val push: Float, val nearPush: Float, val frame: HeadFrame, val near: Vec3)

/** How far a point on the camera's side of a head's middle, as a share of its length, lies before its near swelling starts and is whole. */
internal const val NEAR_START = -0.05f
internal const val NEAR_WHOLE = 0.25f

/**
 * The maps of everything a key moves at one [direction]: each run, piece and part composed from its own move up through what it sits on,
 * the moves that reach only some of the surface, and how far each head's face turns toward the camera.
 */
internal class KeyChains(
    private val lookup: CharacterKeyLookup,
    moves: List<ElementMove>,
    val frames: Map<PartId, HeadFrame>,
    private val follows: Map<PartId, FaceFollow>,
    direction: KeyDirection,
) {
    private val whole = moves.filter { it.element.area == null }.associateBy { it.element }
    private val runs = HashMap<Pair<PartId, String?>, KeyAffine>()
    private val sign = if (direction.mirrored) -1f else 1f

    /** The moves reaching only some of the surface. */
    val soft: List<SoftMove> = moves.filter { it.move.isSoft }.mapNotNull { em ->
        val placed = lookup.part(em.element.part) ?: return@mapNotNull null
        val frame = frames[em.head] ?: return@mapNotNull null
        val area = em.element.area?.let { AreaMask.of(placed.template, it) ?: return@mapNotNull null }
        val (pivot, axis) = partRoot(placed)
        val map = if (area != null) KeyAffine.of(em.move, frame, pivot, axis) else KeyAffine.IDENTITY
        SoftMove(em.element.part, area, map, em.move.push, em.move.nearPush, frame, frame.left * sign)
    }

    /** How many degrees each head's face turns toward the camera here, signed by the camera's side. */
    val faceDegrees: Map<PartId, Float> = follows.mapValues { (_, follow) -> follow.degreesAt(direction.view) * sign }

    /** Whether nothing at this direction moves. */
    val still: Boolean get() = whole.isEmpty() && soft.isEmpty() && faceDegrees.values.all { it == 0f }

    /** The face follow of [head], or none. */
    fun follow(head: PartId): FaceFollow = follows[head] ?: FaceFollow.NONE

    /** The map of [part]'s run or piece [patch], or the whole part with none. */
    fun of(part: PartId, patch: String?): KeyAffine = runs.getOrPut(part to patch) {
        val placed = lookup.part(part) ?: return@getOrPut KeyAffine.IDENTITY
        if (patch == null) return@getOrPut own(placed, null).then(parentChain(placed))
        val piece = placed.surface.patches.firstOrNull { it.name == patch } ?: return@getOrPut of(part, null)
        val ownMap = own(placed, piece)
        if (isRun(piece)) return@getOrPut ownMap.then(of(part, null))
        val host = hostRun(placed, placed.origin + placed.surface.shown(piece.middle()))
        ownMap.then(of(part, host?.name))
    }

    private fun parentChain(placed: PlacedPart): KeyAffine {
        val parent = placed.socket?.parent?.let(lookup::part) ?: return KeyAffine.IDENTITY
        val host = hostRun(parent, placed.origin)
        return of(requireNotNull(parent.id), host?.name)
    }

    /** The move named for [placed]'s [piece], or for the whole part with none, about its root. */
    private fun own(placed: PlacedPart, piece: Patch?): KeyAffine {
        val id = requireNotNull(placed.id)
        val element = whole.entries.firstOrNull { (key, _) -> key.part == id && (if (piece == null) key.run == null else key.run != null && piece.named(key.run)) }?.value
            ?: return KeyAffine.IDENTITY
        val frame = frames[element.head] ?: return KeyAffine.IDENTITY
        val (pivot, axis) = if (piece == null) partRoot(placed) else pieceRoot(placed, piece)
        return KeyAffine.of(element.move, frame, pivot, axis)
    }

    /** Where a whole part is rooted, a head's middle or another part's socket point, and which way it runs from there, in body space. */
    fun partRoot(placed: PlacedPart): Pair<Vec3, Vec3> {
        val run = placed.surface.patches.firstOrNull(::isRun)
        val axis = run?.let { placed.surface.shown(it.ringMiddle(it.nu - 1) - it.ringMiddle(0)) }?.takeIf { it.length > 0f }?.normalized()
            ?: PartBuilder.axes(placed.template).along
        return (frames[placed.id]?.center ?: placed.origin) to axis
    }

    /** Where a run or piece is rooted, the middle of its first ring or its middle, and which way it runs, in body space. */
    private fun pieceRoot(placed: PlacedPart, piece: Patch): Pair<Vec3, Vec3> {
        if (!isRun(piece)) return placed.origin + placed.surface.shown(piece.middle()) to partRoot(placed).second
        val first = piece.ringMiddle(0)
        val along = placed.surface.shown(piece.ringMiddle(piece.nu - 1) - first)
        return placed.origin + placed.surface.shown(first) to (if (along.length > 0f) along.normalized() else partRoot(placed).second)
    }
}

