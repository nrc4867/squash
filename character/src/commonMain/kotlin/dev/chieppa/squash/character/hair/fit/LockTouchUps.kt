package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.hair.GroupLocks
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.geom.Vec3

/**
 * [fitted], what a fit gives [groups]' locks, touched up where the hair would read badly round [head] and its mass ([massPoints] and
 * [massGrids], in body space): locks standing out of the head and mass to the side or below reined in, the locks beside a gap at the
 * hairline widened over it, and spikes standing up out of the top widened to read as spikes. Each touch-up changes only the locks it
 * finds; every other lock keeps what [fitted] gives it.
 */
internal fun touchedUp(
    groups: List<LockGroup>,
    lay: (LockGroup) -> GroupLocks,
    fitted: Map<Pair<LockGroupId, Int>, FittedLock>,
    head: PlacedPart,
    front: HeadView,
    massPoints: List<Vec3>?,
    massGrids: List<PointGrid>,
): Map<Pair<LockGroupId, Int>, FittedLock> {
    val mesh = head.surface.mesh
    val headPoints = List(mesh.vertexCount) { head.origin + mesh.position(it) }
    val envelopes = HeadEnvelope.around(front, headPoints + massPoints.orEmpty())
    var locks = fitted + locksReined(SettledLock.of(groups, lay, fitted), envelopes)
    locks = locks + hairlineGapsClosed(SettledLock.of(groups, lay, locks), front, head, massGrids)
    return locks + spikesWidened(SettledLock.of(groups, lay, locks), envelopes.first())
}
