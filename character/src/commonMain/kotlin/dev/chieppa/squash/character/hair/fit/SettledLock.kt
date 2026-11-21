package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.hair.GroupLocks
import dev.chieppa.squash.character.hair.LockSpec
import dev.chieppa.squash.character.hair.evenly
import dev.chieppa.squash.character.hair.guideOf
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.geom.Vec3

/** How many places along a lock a touch-up reads. */
private const val SETTLED_SAMPLES = 14

/**
 * One lock as a fit leaves it: its [group] as [laid] out, its [index], its override with nothing fitted ([base]) and what the fit gives it
 * ([fitted]), null where the fit leaves it as its group grows it.
 */
internal class SettledLock(val group: LockGroup, val laid: GroupLocks, val index: Int, val base: LockOverride?, val fitted: FittedLock?) {
    val key: Pair<LockGroupId, Int> get() = group.id to index

    /** What the fit gives it, or nothing. */
    val own: FittedLock get() = fitted ?: FittedLock()

    /** The lock as it grows with [lock] over its base. */
    fun spec(lock: FittedLock = own): LockSpec = laid.spec(index, lock.over(base, index))

    /** Evenly spaced places along the lock grown as [spec], root first. */
    fun along(spec: LockSpec): List<Vec3> = evenly(guideOf(spec, laid.scalp), SETTLED_SAMPLES)

    companion object {
        /** Every lock of [groups] a fit may move, laid out by [lay], with what [fitted] gives each. */
        fun of(groups: List<LockGroup>, lay: (LockGroup) -> GroupLocks, fitted: Map<Pair<LockGroupId, Int>, FittedLock>): List<SettledLock> =
            groups.flatMap { group ->
                val laid = lay(group)
                laid.roots.indices.mapNotNull { index ->
                    val base = group.overrideOf(index)
                    if (base?.hidden == true || base?.guide != null) null else SettledLock(group, laid, index, base, fitted[group.id to index])
                }
            }
    }
}
