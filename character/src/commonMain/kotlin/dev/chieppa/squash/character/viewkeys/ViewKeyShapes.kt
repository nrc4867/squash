package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.isRun
import dev.chieppa.squash.character.copies.DEGREES_TO_RADIANS
import dev.chieppa.squash.character.copies.turnedAbout
import dev.chieppa.squash.character.hair.shellGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ViewKeys
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD

/** How many floats a vertex's change holds: its position's, its normal's and its shading normal's. */
internal const val DELTA_FLOATS = 9

/**
 * A character's head cheats built once at every key direction on the meshes it already has, so a view only blends them: for each head
 * its frame at rest; for the one surface the vertices a key moves, each with its change at every direction and the head it turns with;
 * for each separate part a key moves, every vertex's change at every direction, in the part's own space, a short-hair shell's taken
 * from its head's on the one surface so it rides the skin under it ([shellDeltas]). The one surface keeps its
 * vertices, quads and coordinates, so its paint, ink marks and eyes ride on it as on a pose. Built by [of].
 */
class ViewKeyShapes internal constructor(
    /** The character the shapes were built on, at rest. */
    val rest: AssembledCharacter,
    /** Each keyed head's frame at rest. */
    val heads: Map<PartId, HeadFrame>,
    /** The surface the shapes were built on, or null with none. */
    val surface: CharacterSurface?,
    /** The render vertices of [surface] some key moves. */
    internal val moved: IntArray,
    /** The head each of [moved] turns with, as an index into [headOrder]. */
    internal val movedHead: IntArray,
    /** Per direction, each of [moved]'s change, [DELTA_FLOATS] floats a vertex, and its line normal's, three a vertex. */
    internal val surfaceDeltas: List<FloatArray>,
    internal val lineDeltas: List<FloatArray>,
    /** Per moved separate part, its head and per direction every vertex's change in its own space, or null where the key leaves it. */
    internal val parts: Map<PartId, PartDeltas>,
) {
    /** The heads in the order [movedHead] counts them. */
    internal val headOrder: List<PartId> = heads.keys.toList()

    /** Whether no key moves anything. */
    val isEmpty: Boolean get() = moved.isEmpty() && parts.isEmpty()

    companion object {
        /** No head cheat at all. */
        fun none(character: AssembledCharacter, surface: CharacterSurface?) =
            ViewKeyShapes(character, emptyMap(), surface, IntArray(0), IntArray(0), emptyList(), emptyList(), emptyMap())

        /**
         * The shapes [keys] give [character] at rest with its one [surface], built from the same meshes: each head's moves at each key
         * direction, its default keys in its face style, and its face turned toward the camera as far as it follows. See HLD: look, View keys.
         */
        fun of(character: AssembledCharacter, surface: CharacterSurface?, keys: ViewKeys, checkCancelled: () -> Unit = {}): ViewKeyShapes {
            val lookup = CharacterKeyLookup(character)
            val templates = lookup.heads.associateWith { requireNotNull(lookup.part(it)).template.id }
            val follows = templates.mapValues { (_, template) -> FaceFollow.of(keys, template) }.filterValues { it.follow > 0f }
            val tracks = keyTracks(keys, lookup) { head -> defaultKeys(FaceFollow.styleOf(keys, templates.getValue(head))) }
            if (tracks.all { it.isEmpty() } && follows.isEmpty()) return none(character, surface)
            val keyed = (tracks.flatten().map { it.head } + follows.keys).distinct()
            val frames = keyed.associateWith { head -> headFrame(requireNotNull(lookup.part(head))) }
            val chains = KEY_DIRECTIONS.mapIndexed { d, direction -> KeyChains(lookup, tracks[d], frames, follows, direction) }
            checkCancelled()
            val built = surface?.let { SurfaceShapes.of(it, chains, frames.keys.toList(), lookup) }
            checkCancelled()
            val parts = character.parts.mapNotNull { part ->
                val id = part.id ?: return@mapNotNull null
                if (part.copies != null) return@mapNotNull null
                val shellOn = part.locks?.takeIf { it.group == shellGroupId(it.head) }?.head
                val head = lookup.headOf(id) ?: shellOn ?: return@mapNotNull null
                if (head !in frames) return@mapNotNull null
                val shared = if (shellOn == head && surface != null && built != null) shellDeltas(part, head, surface, built, frames.getValue(head)) else null
                val deltas = shared ?: chains.map { partDelta(part, it, head) }
                if (deltas.all { it == null }) null else id to PartDeltas(head, deltas)
            }.toMap()
            return ViewKeyShapes(
                character, frames, surface,
                built?.moved ?: IntArray(0), built?.heads ?: IntArray(0), built?.deltas.orEmpty(), built?.lines.orEmpty(), parts,
            )
        }
    }
}

/** A separate part's changes: the [head] it turns with, and per direction its vertices' changes, or null where it rests. */
class PartDeltas(val head: PartId, val deltas: List<FloatArray?>)

/** A head's frame at rest: along its facing, the body's up made square to it, its length its box's longest side, about its box's middle. */
private fun headFrame(head: PlacedPart): HeadFrame {
    val box = head.box
    val length = box?.let { maxOf(it.high.x - it.low.x, it.high.y - it.low.y, it.high.z - it.low.z) } ?: 1f
    return HeadFrame.of(PartBuilder.facing(head.template), length, box?.center ?: head.origin)
}
