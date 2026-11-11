package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.SurfaceUv

private const val PREFIX = "join."

/** What a part's parameters say of the join where it sits on its parent. No part's build reads them. See HLD: look, Where parts meet. */
object JoinParams {
    /** 1 inks the join as a line round the part, 0 or unset leaves it unlined. */
    const val INK = "${PREFIX}ink"

    fun isJoin(name: String) = name.startsWith(PREFIX)
}

/**
 * A strip of quads joining one of a part's swept patches to another part's surface, held on the first part as a patch of its own,
 * [patch], with a u range of its own: from -1 at the other part to 0 at [tube]'s ring [ring] when [atStart], else from 1 at the ring
 * to 2 at the other part. Faces of [tube] past the ring, toward the other part, are left out, as are faces of the [host] inside the
 * strip's foot, which lies on [host]'s patch [hostPatch] at [foot], one place a column of the strip. [joined] is the part sitting on
 * the socket the strip joins, whose [JoinParams] rule the join. The foot stands [lift] off [host], as a garment's strip stands its ease
 * off the body's. See HLD: look, Where parts meet.
 */
class StripJoin(
    val patch: String,
    val tube: String,
    val ring: Int,
    val atStart: Boolean,
    val host: PartId,
    val hostPatch: String,
    val foot: List<SurfaceUv>,
    val joined: PartId,
    val lift: Float = 0f,
) {
    companion object {
        /** The name of a strip's patch at a part's start, or at its end. */
        fun patchName(atStart: Boolean) = if (atStart) "join.start" else "join.end"

        /** How many rings every strip has, both ends among them. */
        const val RINGS = 9
    }
}

/** The part holding the strip that joins [part] to its parent, with that strip, or null when [part] meets its parent by overlap. */
fun AssembledCharacter.stripJoining(part: PartId): Pair<PlacedPart, StripJoin>? {
    for (placed in parts) if (placed.covering == null) for (strip in placed.strips) if (strip.joined == part) return placed to strip
    return null
}

/** Whether [part]'s join with its parent is inked: its [JoinParams.INK] set above a half. */
fun PlacedPart.joinInked(): Boolean = ((template.defaults + placement)[JoinParams.INK] ?: 0f) > 0.5f
