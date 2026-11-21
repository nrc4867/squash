package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.hair.GroupLocks
import dev.chieppa.squash.character.hair.Scalp
import dev.chieppa.squash.character.hair.ScalpField
import dev.chieppa.squash.character.hair.fittedMassShape
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.HairOutline
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.MOST_LOCKS
import dev.chieppa.squash.document.NARROWEST_LOCK
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.WIDEST_LOCK
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.document.scalpFlowOn
import dev.chieppa.squash.geom.Vec2

/** The order a fit takes its views in: a tip reached in an earlier view is not fitted again in a later one. */
private val VIEW_ORDER = listOf(OutlineView.FRONT, OutlineView.BACK, OutlineView.THREE_QUARTER, OutlineView.SIDE)

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

/** How many times a fit lays out more locks for tips no lock reached. */
private const val MORE_LOCK_PASSES = 1

/**
 * What fitting a head's hair to its outline gives: the [commands] that set it, the inner mass's parameters and the lock groups that change;
 * how many locks it [fitted] to tips and [kept] inside the outline, and how many tips no lock reached ([missed]).
 */
class HairFit(val commands: List<DocumentCommand>, val fitted: Int, val kept: Int, val missed: Int) {
    /** One step that sets it all, or null when nothing changes. */
    val command: DocumentCommand? get() = if (commands.isEmpty()) null else Together(commands)
}

/**
 * The lock hair of [head] fitted to its outline, as [assembled] (the body with the head and its mass, without the locks) builds it: the
 * inner mass's crown and width sized to the outline's envelope seen from the front, then in each view the outline's tips paired with the
 * locks whose roots face that view by how near their tips land, each paired lock's root, length, rise, sweep, fall and width searched so
 * its tip lands on its tip and its edges follow the outline between the valleys either side, and every other lock shortened, or laid down,
 * where it would stray outside the outline. Tied groups and locks with guides of their own are left as they are. A fit owns the length,
 * rise, sweep, fall, root and width overrides of the locks it reaches; it is deterministic, so the same outline fits the same values.
 * Null where the head has no outline, no locks or builds nothing. See HLD: parts, Hair locks.
 */
fun hairFit(character: Character, assembled: AssembledCharacter, head: PartId, outline: HairOutline? = character.hairOutlineOn(head)): HairFit? {
    outline ?: return null
    val headPart = assembled.part(head) ?: return null
    val originals = character.lockGroups.filter { it.on == head && it.tie == 0f }
    if (originals.isEmpty()) return null
    val size = HeadView.sizeOf(character, headPart)
    val hairOn = character.lockGroups.filter { it.on == head }.mapNotNull { it.mass }.toSet()
    val occluders = assembled.parts.filter { it.id != null && it.locks == null && it.copies == null && it.role != Role.HAIR && it.id !in hairOn }
    val views = VIEW_ORDER.mapNotNull { which -> outline.view(which)?.let { FitView(HeadView.of(headPart, size, which), FlatOutline.of(it), occluders) } }
    val front = views.first()
    val commands = ArrayList<DocumentCommand>()
    val massId = originals.firstNotNullOfOrNull { it.mass }
    val massPart = massId?.let(assembled::part)
    var massPoints = massPart?.shownPoints()
    if (massId != null && massPart != null) {
        val instance = character.part(massId)
        if (instance != null) {
            val data = character.resolvedShape(instance)
            val thickness = originals.map { it.thickness }.sorted()[originals.size / 2]
            val builds = MassBuilds(massPart, data)
            massPoints = builds.points(emptyMap())
            val least = headPart.box?.let { fittedMassShape(it.size.x, it.size.y) }.orEmpty()
            fitMass(front, Scalp.of(headPart, null).middle, builds, massPart.template.defaults + data.params, thickness, least)?.let { refit ->
                massPoints = refit.points
                if (refit.params.isNotEmpty()) commands += SetParameters(massId, refit.params)
            }
        }
    }
    val scalps = HashMap<PartId?, Scalp>()
    fun scalpOf(group: LockGroup): Scalp = scalps.getOrPut(group.mass) {
        if (group.mass == massId) Scalp.over(headPart, massPoints) else Scalp.of(headPart, group.mass?.let(assembled::part))
    }
    val field = ScalpField(character.scalpFlowOn(head))
    var groups = originals.map { it.copy(overrides = it.overrides.mapNotNull(::withoutFit)) }
    var result = fitLocks(groups, views, ::scalpOf, field)
    repeat(MORE_LOCK_PASSES) {
        if (result.short.isEmpty()) return@repeat
        groups = groups.map { g -> result.short[g.id]?.let { more -> g.copy(count = (g.count + more).coerceAtMost(MOST_LOCKS)) } ?: g }
        result = fitLocks(groups, views, ::scalpOf, field)
    }
    for ((original, group) in originals.zip(groups)) {
        val fitted = result.locks.filterKeys { it.first == group.id }
        val overrides = (0 until group.count).mapNotNull { index ->
            val base = group.overrideOf(index)
            fitted[group.id to index]?.over(base, index) ?: base
        }
        val now = group.copy(overrides = overrides)
        if (now != original) commands += SetLockGroup(group.id, now)
    }
    return HairFit(commands, result.fitted, result.kept, result.missed)
}

/** [override] with what a fit sets taken out, or null where nothing else is left. */
internal fun withoutFit(override: LockOverride): LockOverride? {
    val left = override.copy(
        length = null, rise = null, sweep = null, fall = null, around = null, up = null, width = null, lie = null,
        tip = override.tip.takeIf { it != LockTip.FLICKED },
    )
    return if (override.guide != null) override else left.takeIf { it != LockOverride(override.index) }
}

/** The locks a fit sets, by group and index; how many it fitted to tips and kept inside; the tips it missed; and how many more locks each group wants for them. */
private class LockFits(val locks: Map<Pair<LockGroupId, Int>, FittedLock>, val fitted: Int, val kept: Int, val missed: Int, val short: Map<LockGroupId, Int>)

/** One lock a fit may move: its group as laid out, its index and its override with nothing fitted. */
private class Candidate(val group: LockGroup, val laid: GroupLocks, val index: Int, val base: LockOverride?) {
    val key: Pair<LockGroupId, Int> get() = group.id to index
}

private fun fitLocks(groups: List<LockGroup>, views: List<FitView>, scalpOf: (LockGroup) -> Scalp, field: ScalpField): LockFits {
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
    var missed = 0
    for (view in views) {
        val tipIndices = view.outline.tips.filter { prominence(view, it) >= view.height * LEAST_PROMINENCE }
        val tips = tipIndices.map { view.outline.points[it] }
        if (tips.isEmpty()) continue
        val open = all.filter { it.key !in out && (fitters.getValue(it.key).start.out dot view.head.toViewer) > FACING_AWAY }
        if (open.isEmpty()) {
            missed += tips.size
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
                missed++
                val nearest = open.minBy { length(view.head.flat(fitters.getValue(it.key).start.root), tips[t]) }
                short[nearest.group.id] = (short[nearest.group.id] ?: 0) + 1
                return@forEachIndexed
            }
            val candidate = open[c]
            val middle = view.head.flat(candidate.laid.scalp.middle)
            out[candidate.key] = fitters.getValue(candidate.key).fitTo(targetAt(view, tipIndices[t], candidate.group, middle))
        }
    }
    val fitted = out.size
    var kept = 0
    for (candidate in all) {
        if (candidate.key in out) continue
        fitters.getValue(candidate.key).keptIn()?.let {
            out[candidate.key] = it
            kept++
        }
    }
    return LockFits(out, fitted, kept, missed, short)
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
