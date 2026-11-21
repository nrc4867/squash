package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.hair.LockSpec
import dev.chieppa.squash.document.LockGroupId

/** How far a lock may stand out of the head and its mass to the side or below in any view, as a share of their height there. */
private const val REACH = 0.18f

/** The shortest a lock standing out is shortened to, as a share of its length. */
private const val SHORTEST_REINED = 0.35f

/** The halvings that find how far a lock standing out is shortened. */
private const val REIN_STEPS = 8

/**
 * The locks of [locks] that stand out of the head and its mass, to the side or below, by more than [REACH] in any of [envelopes], each
 * laid down where that brings it in at its length, else shortened until it no longer stands out, and laid down too where shortening to
 * [SHORTEST_REINED] is not enough. Locks that stand out nowhere are not in the map.
 */
internal fun locksReined(locks: List<SettledLock>, envelopes: List<HeadEnvelope>): Map<Pair<LockGroupId, Int>, FittedLock> {
    val out = HashMap<Pair<LockGroupId, Int>, FittedLock>()
    for (lock in locks) {
        val spec = lock.spec()
        if (standsOut(lock, spec, envelopes) <= REACH) continue
        out[lock.key] = reined(lock, spec, envelopes)
    }
    return out
}

private fun reined(lock: SettledLock, spec: LockSpec, envelopes: List<HeadEnvelope>): FittedLock {
    val own = lock.own
    fun trial(share: Float, rise: Float?) = own.copy(length = spec.length * share, rise = rise ?: own.rise)
    fun reach(share: Float, rise: Float?) = standsOut(lock, lock.spec(trial(share, rise)), envelopes)
    if (spec.rise > 0f && reach(1f, 0f) <= REACH) return trial(1f, 0f).rounded()
    val rises = if (spec.rise > 0f) listOf(null, 0f) else listOf(null)
    for (rise in rises) {
        if (reach(SHORTEST_REINED, rise) > REACH) continue
        var inside = SHORTEST_REINED
        var outside = 1f
        repeat(REIN_STEPS) {
            val middle = (inside + outside) / 2f
            if (reach(middle, rise) <= REACH) inside = middle else outside = middle
        }
        return trial(inside, rise).rounded()
    }
    return trial(SHORTEST_REINED, rises.last()).rounded()
}

/** How far [lock], grown as [spec], stands out of the head and its mass to the side or below at most over [envelopes], as a share of their height. */
private fun standsOut(lock: SettledLock, spec: LockSpec, envelopes: List<HeadEnvelope>): Float {
    val along = lock.along(spec)
    return envelopes.maxOf { e -> along.maxOf { e.aside(it) } / e.height }
}
