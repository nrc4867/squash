package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.WIDEST_LOCK

/** How far along a lock, as a share of its length, its tip is read for how wide it is: where it starts closing to a point. */
private const val TIP_AT = 0.7f

/** The narrowest a spike's tip may be, as a share of the head and its mass's height from the front. */
private const val SPIKE_TIP = 0.07f

/**
 * The locks of [locks] that stand up out of the top of the head and its mass as [front] sees them, spikes, widened so each is at least
 * [SPIKE_TIP] wide where its tip starts to close; their length and shape stay as they are. Every other lock is left out.
 */
internal fun spikesWidened(locks: List<SettledLock>, front: HeadEnvelope): Map<Pair<LockGroupId, Int>, FittedLock> {
    val out = HashMap<Pair<LockGroupId, Int>, FittedLock>()
    for (lock in locks) {
        val spec = lock.spec()
        if (front.view.flat(lock.along(spec).last()).y <= front.top) continue
        val tip = front.view.drawn(spec.width) * lockShare(TIP_AT, spec.taper)
        val least = front.height * SPIKE_TIP
        if (tip >= least) continue
        out[lock.key] = lock.own.copy(width = (spec.width * least / tip).coerceAtMost(WIDEST_LOCK)).rounded()
    }
    return out
}
