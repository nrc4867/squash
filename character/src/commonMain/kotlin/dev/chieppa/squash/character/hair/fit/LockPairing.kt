package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.hair.GroupLocks
import dev.chieppa.squash.character.hair.Scalp
import dev.chieppa.squash.character.hair.ScalpField
import dev.chieppa.squash.character.hair.guideOf
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.NARROWEST_LOCK
import dev.chieppa.squash.document.WIDEST_LOCK
import dev.chieppa.squash.geom.Vec2
import kotlin.math.abs
import kotlin.math.sqrt

/** How far a lock's root may face away from a view, as the cosine of its way out against the way to the viewer, and still be fitted there. */
private const val FACING_AWAY = -0.3f

/**
 * What pairing a lock with a tip it cannot serve costs, more than any tip is worth, so it is left unpaired: a tip over the face needs a
 * lock whose root faces the viewer at least [OVER_BODY_FACING] (the cosine), and a tip on the outer outline a lock other than a fringe's.
 */
private const val UNFIT_COST = 5.0
private const val OVER_BODY_FACING = 0.2f

/** How far a tip stands out of the line between the valleys either side of it, as a share of the outline's height, to be fitted. */
private const val LEAST_PROMINENCE = 0.02f

/** How much a lock's root counts beside where its tip lands as grown when pairing it with a tip. */
private const val ROOT_COUNT = 0.5

/** How far either side of a tip, as a share of the outline's height, its valleys may lie and still bound the lock's edges and width. */
private const val EDGE_REACH = 0.4f

/** How much of the way from each valley toward the tip its edge is read from: the ends near the valleys are where locks meet. */
private const val EDGE_TRIM = 0.15f

/** A lock's width, as a share of the gap between the valleys either side of its tip, and how far from its group's width it may go. */
private const val WIDTH_OF_GAP = 0.6f
private const val WIDTH_RANGE_LOW = 0.8f
private const val WIDTH_RANGE_HIGH = 1.6f

/**
 * How far out from the head a tip below the scalp's middle on the outer outline must point, away from the middle of the valleys either
 * side of it (the cosine of its angle from level), for its lock to end in a flick.
 */
private const val FLICK_OUT = 0.5f

/** How far from its tip, as a share of the outline's height, a fitted lock's tip may land and still reach it. */
private const val REACHED = 0.06f

/** How far either side of a fringe lock's root, as a share of the outline's height, the outline's edge below it is read. */
private const val FRINGE_BAND = 0.04f

/** How far short of the outline, as a share of its height, a crown's or strays' lock left unpaired must end to be grown out to it. */
private const val SHORT_OF_OUTLINE = 0.05f

/** How much wider than the gap between its root and its neighbours' a fringe lock is, seen from the front, so no skin shows between them. */
private const val FRINGE_OVERLAP = 1.35f

/** The most times its group's width a fringe lock is widened to overlap its neighbours. */
private const val FRINGE_WIDEST = 2f

/** The locks a fit sets, by group and index; how many it fitted to tips and kept inside; the tips no lock reached; and how many more locks each group wants for them. */
internal class LockFits(
    val locks: Map<Pair<LockGroupId, Int>, FittedLock>,
    val fitted: Int,
    val kept: Int,
    val unreached: List<UnreachedTip>,
    val short: Map<LockGroupId, Int>,
)

/** One lock a fit may move: its group as laid out, its index and its override with nothing fitted. */
private class Candidate(val group: LockGroup, val laid: GroupLocks, val index: Int, val base: LockOverride?) {
    val key: Pair<LockGroupId, Int> get() = group.id to index
}

/**
 * [groups]' locks fitted to [views]: in each drawn view its tips paired with the free locks facing it by the cheapest pairing and fitted
 * to them; then each lock left unpaired grown to the outline where its kind fills it (a fringe's down to the outline's edge below it, a
 * crown's or strays' out to the outline the way it points, where it ends well short) or kept inside every view; then each fringe
 * widened to overlap its neighbours. Every lock is held no thinner than a lock reads as and inside every view.
 */
internal fun fitLocks(groups: List<LockGroup>, views: List<FitView>, scalpOf: (LockGroup) -> Scalp, field: ScalpField): LockFits {
    val all = groups.flatMap { group ->
        val laid = GroupLocks(group, scalpOf(group), field)
        laid.roots.indices.mapNotNull { index ->
            val base = group.overrideOf(index)
            if (base?.hidden == true || base?.guide != null) null else Candidate(group, laid, index, base)
        }
    }
    val fitters = all.associate { it.key to LockFitter(it.laid, it.index, it.base, views) }
    val out = HashMap<Pair<LockGroupId, Int>, FittedLock>()
    val short = HashMap<LockGroupId, Int>()
    val unreached = ArrayList<UnreachedTip>()
    for (view in views.filter { it.drawn }) {
        val tipIndices = view.outline.tips.filter { prominence(view, it) >= view.height * LEAST_PROMINENCE }
        val tips = tipIndices.map { view.outline.points[it] }
        if (tips.isEmpty()) continue
        val open = all.filter { it.key !in out && (fitters.getValue(it.key).start.out dot view.head.toViewer) > FACING_AWAY }
        if (open.isEmpty()) {
            tips.forEach { unreached += UnreachedTip(view.head.view, it, UnreachedTip.Why.NO_LOCK_FACES) }
            continue
        }
        val cost = Array(tips.size) { t ->
            val tip = tips[t]
            val over = view.onBody(tip)
            DoubleArray(open.size) { c ->
                val fitter = fitters.getValue(open[c].key)
                val grown = view.head.flat(fitter.guide.last())
                val root = view.head.flat(fitter.start.root)
                val facing = fitter.start.out dot view.head.toViewer
                val unfit = if (over) facing < OVER_BODY_FACING else open[c].group.kind == LockGroupKind.FRINGE
                (length(grown, tip) + ROOT_COUNT * length(root, tip)) / view.height + if (unfit) UNFIT_COST else 0.0
            }
        }
        val pairing = cheapestPairing(cost)
        pairing.forEachIndexed { t, c ->
            if (c < 0 || cost[t][c] >= UNFIT_COST) {
                unreached += UnreachedTip(view.head.view, tips[t], if (c < 0) UnreachedTip.Why.TOO_FEW_LOCKS else UnreachedTip.Why.NO_LOCK_SERVES)
                val nearest = open.minBy { length(view.head.flat(fitters.getValue(it.key).start.root), tips[t]) }
                short[nearest.group.id] = (short[nearest.group.id] ?: 0) + 1
                return@forEachIndexed
            }
            val candidate = open[c]
            val fitter = fitters.getValue(candidate.key)
            val middle = view.head.flat(candidate.laid.scalp.middle)
            val found = fitter.fitTo(targetAt(view, tipIndices[t], candidate.group, middle), contain = false)
            val fitted = fitter.contained(fitter.bounded(found))
            out[candidate.key] = fitted
            fun misses(lock: FittedLock) =
                length(view.head.flat(guideOf(fitter.specOf(lock), candidate.laid.scalp).last()), tips[t]) > view.height * REACHED
            when {
                misses(found) -> unreached += UnreachedTip(view.head.view, tips[t], UnreachedTip.Why.OUT_OF_REACH)
                misses(fitted) -> unreached += UnreachedTip(view.head.view, tips[t], UnreachedTip.Why.KEPT_INSIDE)
            }
        }
    }
    val fitted = out.size
    var kept = 0
    val front = views.first()
    for (candidate in all) {
        if (candidate.key in out) continue
        val fitter = fitters.getValue(candidate.key)
        val target = when (candidate.group.kind) {
            LockGroupKind.FRINGE -> fringeTarget(front, fitter)
            LockGroupKind.CROWN, LockGroupKind.STRAYS -> outwardTarget(views, fitter, candidate.laid.scalp)
            else -> null
        }
        val lock = if (target != null) fitter.fitTo(target) else fitter.keptIn()
        lock?.let {
            out[candidate.key] = it
            kept++
        }
    }
    for (group in groups.filter { it.kind == LockGroupKind.FRINGE }) overlapFringe(group, all.filter { it.group.id == group.id }, fitters, out, front)
    return LockFits(out, fitted, kept, unreached, short)
}

/** Each lock of a fringe [group] seen from [front] widened, where it is narrower, to overlap the gaps to its neighbours' roots. */
private fun overlapFringe(
    group: LockGroup, members: List<Candidate>, fitters: Map<Pair<LockGroupId, Int>, LockFitter>, out: MutableMap<Pair<LockGroupId, Int>, FittedLock>, front: FitView,
) {
    if (members.size < 2) return
    val roots = members.map { c -> c to front.head.flat(fitters.getValue(c.key).specOf(out[c.key] ?: FittedLock()).root) }.sortedBy { it.second.x }
    val widest = minOf(WIDEST_LOCK, group.width * FRINGE_WIDEST)
    roots.forEachIndexed { i, (candidate, at) ->
        val gaps = listOfNotNull(roots.getOrNull(i - 1)?.second, roots.getOrNull(i + 1)?.second).map { length(it, at) }
        val wanted = (gaps.max() * FRINGE_OVERLAP * front.head.scale).coerceIn(NARROWEST_LOCK, widest)
        val fitter = fitters.getValue(candidate.key)
        val now = out[candidate.key] ?: FittedLock()
        if (fitter.specOf(now).width < wanted) out[candidate.key] = now.copy(width = wanted).rounded()
    }
}

/** Where a fringe lock left unpaired reaches: the front outline's edge over the face below its root, where the outline draws one. */
private fun fringeTarget(front: FitView, fitter: LockFitter): LockTarget? {
    if ((fitter.start.out dot front.head.toViewer) <= 0f) return null
    val root = front.head.flat(fitter.start.root)
    val band = front.height * FRINGE_BAND
    val edge = front.outline.resampled(front.height / 120f).filter { abs(it.x - root.x) <= band && it.y < root.y && front.onBody(it) }.maxByOrNull { it.y }
    return edge?.let { LockTarget(front, it, emptyList(), null) }
}

/**
 * Where a crown's or strays' lock left unpaired reaches: in the drawn view that sees it most nearly side on, the outline the way it
 * points from the scalp's middle, where its tip ends more than [SHORT_OF_OUTLINE] short of it.
 */
private fun outwardTarget(views: List<FitView>, fitter: LockFitter, scalp: Scalp): LockTarget? {
    val view = views.filter { it.drawn }.minByOrNull { abs(fitter.start.out dot it.head.toViewer) } ?: return null
    val middle = view.head.flat(scalp.middle)
    val tip = view.head.flat(fitter.guide.last())
    val way = (tip - middle).let { d -> sqrt(d.x * d.x + d.y * d.y).takeIf { it > 1e-6f }?.let { d * (1f / it) } } ?: return null
    fun ahead(p: Vec2) = (p.x - middle.x) * way.x + (p.y - middle.y) * way.y
    val along = view.outline.resampled(view.height / 120f).filter { ahead(it) > 0f }
    val at = along.maxByOrNull { p -> ahead(p) / length(p, middle).coerceAtLeast(1e-6f) } ?: return null
    if (length(at, middle) - length(tip, middle) < view.height * SHORT_OF_OUTLINE) return null
    return LockTarget(view, at, emptyList(), null)
}

/** How far the tip at point [tip] of [view]'s outline stands out of the line between the valleys either side of it; unbounded with none near. */
private fun prominence(view: FitView, tip: Int): Float {
    val (before, after) = valleysAround(view, tip) ?: return Float.MAX_VALUE
    val outline = view.outline
    return toSegment(outline.points[tip], outline.points[before], outline.points[after])
}

/** The valleys either side of the tip at point [tip], each within reach of it along the outline, or null. */
private fun valleysAround(view: FitView, tip: Int): Pair<Int, Int>? {
    val outline = view.outline
    val reach = view.height * EDGE_REACH
    val before = outline.valleys.filter { it != tip }.minByOrNull { outline.arc(it, tip) }?.takeIf { outline.arc(it, tip) <= reach } ?: return null
    val after = outline.valleys.filter { it != tip }.minByOrNull { outline.arc(tip, it) }?.takeIf { outline.arc(tip, it) <= reach } ?: return null
    return before to after
}

/** Where a lock of [group] is fitted for the tip at point [tip] of [view]'s outline: the tip, the outline between the valleys either side, and the width between them. */
private fun targetAt(view: FitView, tip: Int, group: LockGroup, middle: Vec2): LockTarget {
    val outline = view.outline
    val at = outline.points[tip]
    val (before, after) = valleysAround(view, tip) ?: return LockTarget(view, at, emptyList(), null)
    val step = view.height / 60f
    val left = outline.between(before, tip, step)
    val right = outline.between(tip, after, step)
    val edge = left.drop((left.size * EDGE_TRIM).toInt()) + right.dropLast((right.size * EDGE_TRIM).toInt())
    val gap = length(outline.points[before], outline.points[after])
    val width = (gap * WIDTH_OF_GAP * view.head.scale)
        .coerceIn(group.width * WIDTH_RANGE_LOW, group.width * WIDTH_RANGE_HIGH)
        .coerceIn(NARROWEST_LOCK, WIDEST_LOCK)
    val base = Vec2((outline.points[before].x + outline.points[after].x) / 2f, (outline.points[before].y + outline.points[after].y) / 2f)
    val points = (at - base).let { it * (1f / length(at, base).coerceAtLeast(1e-6f)) }
    val outward = if (at.x >= middle.x) points.x else -points.x
    val flick = at.y < middle.y && outward > FLICK_OUT && !view.onBody(at)
    return LockTarget(view, at, edge, width, if (flick) LockTip.FLICKED else null)
}
