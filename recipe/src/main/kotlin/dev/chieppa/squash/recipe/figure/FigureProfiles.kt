package dev.chieppa.squash.recipe.figure

import kotlin.math.abs

/** The stretch nearest its root, as a share of its length, a part standing on a trunk's end is read over for its width there. */
private const val ROOT_SHARE = 0.2

/** How far a profile's tip, open to a part on its end, stops short of the part's length, as a share of its last radius. */
private const val OPEN_TIP_SHARE = 0.5

/** How far below a part standing on a trunk's end the trunk's readings stop, as a share of that part's width: its join's fillet. */
private const val FILLET_SHARE = 0.25

/** A socket within this share of a trunk's end carries what sits on it on that end. */
private const val END_SHARE = 0.05

/** Radii never fall below this, in body units. */
private const val LEAST_RADIUS = 0.002

/** Readings either side a reading's median is taken over, and how far off it, as a share of it, a stray lies. */
private const val STRAY_REACH = 4
private const val STRAY_SHARE = 0.35

/** A front view sample this near the body's middle, in body units, is drawn meeting it. */
private const val MIDDLE_REACH = 0.003

/** A sample on the middle meets it when the side takes at least this share of its stroke within [MEETING_RUN] of it, in body units. */
private const val MEETING_SHARE = 0.6
private const val MEETING_RUN = 0.02

/** How far past the middle a limb's inner side reaches above where it is drawn meeting it, as a share of its width there. */
private const val CROSSING_SHARE = 0.15

/** Body units between the radii a fill writes. */
private const val FILL_STEP = 0.004

/** The radii of every profile of [spec], read off the split and filled where the outline does not show them. */
internal fun profiles(
    spec: FigurePartSpec,
    input: FigureInput,
    split: Map<FigureView, FigureSplit>,
    reads: Map<FigureView, Set<String>>,
    specs: Map<String, FigurePartSpec>,
): Map<String, Radii> {
    val out = LinkedHashMap<String, Radii>()
    val order = profileNames(spec)
    val read = HashMap<String, Radii>()
    var crossing: ((Double) -> Double)? = null
    for (view in FigureView.entries) {
        if (spec.part !in reads.getValue(view)) continue
        for (side in BoneSide.entries) {
            val name = profileName(spec, view, side)
            if (view.halved && mirroredSide(spec, name)) continue
            val samples = split.getValue(view).taken.filter { it.part == spec.part && it.side == side }
            val ends = endsUnder(spec, view, side, split.getValue(view), specs)
            val binnedSamples = binned(samples.filter { it.x <= ends }, input.spacing)
            if (binnedSamples.isNotEmpty() || name !in read) read[name] = binnedSamples
            if (name == "inner" && view == FigureView.FRONT) crossing = crossingTheMiddle(samples, input.spacing)
        }
    }
    val seenRadii = LinkedHashMap<String, Radii>()
    for (name in order) {
        val seen = read[name].orEmpty()
        if (seen.isNotEmpty()) seenRadii[name] = filled(spec, seen, if (name == "inner") seenRadii["outer"] else null, if (name == "inner") crossing else null)
    }
    for (name in order) {
        val radii = seenRadii[name] ?: unseen(spec, name, seenRadii) ?: continue
        out[name] = offset(radii, input.offsets[spec.part]?.get(name))
    }
    return out
}

/**
 * How far along a trunk its readings in [view] on [side] run: up to a quarter of the width of a part standing on its closed end below that
 * part's root, since that part's join draws the outline above it, as a neck's fillet does over a torso's top; its whole length elsewhere.
 */
private fun endsUnder(spec: FigurePartSpec, view: FigureView, side: BoneSide, split: FigureSplit, specs: Map<String, FigurePartSpec>): Double {
    if (spec.kind != FigureKind.TRUNK || !spec.closed) return Double.POSITIVE_INFINITY
    val onEnd = specs.values.filter { it.parent == spec.part && it.kind == FigureKind.TRUNK && it.socketU >= 1 - END_SHARE }
    var ends = Double.POSITIVE_INFINITY
    for (child in onEnd) {
        val near = split.taken.filter { it.part == child.part && it.side == side && it.x < child.length * ROOT_SHARE }
        val width = near.map { it.radius }.takeIf { it.isNotEmpty() }?.average() ?: continue
        ends = minOf(ends, child.socketU * spec.length - FILLET_SHARE * width)
    }
    return ends
}

/**
 * Where a limb's inner side, drawn in the front view, meets the body's middle, as between two legs: above that place the limbs are
 * one, so the inner side there reaches past the middle, by a share of its width where it is drawn meeting it, and the drawn arch
 * below opens as steeply as it is drawn rather than closing to a slit. Null when the side is not drawn reaching the middle: a sample
 * on the middle meets it only as the end of a run of this side's outline, not as a few of another part's, as a chest's beside an arm.
 */
private fun crossingTheMiddle(samples: List<PartSample>, spacing: Double): ((Double) -> Double)? {
    val meet = samples.filter { m ->
        abs(m.sample.at.x) <= MIDDLE_REACH &&
            samples.count { it.sample.stroke == m.sample.stroke && abs(it.sample.along - m.sample.along) <= MEETING_RUN } * spacing >= MEETING_SHARE * MEETING_RUN
    }.minByOrNull { it.x } ?: return null
    val bone = meet.bone
    val dir = bone.to - bone.from
    val length = dir.length
    if (length <= 0) return null
    val across = abs(dir.y) / length
    if (across <= 0) return null
    val past = CROSSING_SHARE * meet.radius
    val apex = meet.x
    return { x ->
        if (x >= apex) 0.0 else {
            val on = bone.from + dir * ((x - bone.xFrom) / (bone.xTo - bone.xFrom))
            abs(on.x) / across + past
        }
    }
}

/** [samples]' radii averaged in bins [spacing] long along the part. */
private fun binned(samples: List<PartSample>, spacing: Double): Radii =
    withoutStrays(samples.groupBy { Math.floor(it.x / spacing).toLong() }.toSortedMap().map { (_, bin) -> P2(bin.map { it.x }.average(), bin.map { it.radius }.average()) })

/**
 * [radii] without the strays: a reading further than a share of the median of its neighbours from it is another part's outline that the
 * split gave this one, as a wide hip beside an arm's elbow; the gap it leaves is bridged.
 */
private fun withoutStrays(radii: Radii): Radii = radii.filterIndexed { i, p ->
    val around = radii.subList(maxOf(0, i - STRAY_REACH), minOf(radii.size, i + STRAY_REACH + 1)).map { it.y }.sorted()
    val median = around[around.size / 2]
    abs(p.y - median) <= STRAY_SHARE * median
}

/**
 * [seen] radii over the part's whole run: gaps bridged by a curve that follows the seen slopes without swinging past its ends, an open
 * part's ends the outline leaves held level, or for a limb's inner side kept to the ratio its first seen radius has to [across], its
 * outer side, and never under [least] there, and the whole evened out.
 */
private fun filled(spec: FigurePartSpec, seen: Radii, across: Radii?, least: ((Double) -> Double)? = null): Radii {
    val open = !spec.closed
    var r = seen.filter { it.x >= 0 && it.x <= spec.length }
    if (r.isEmpty()) return r
    if (open) {
        val tipEnd = spec.length - OPEN_TIP_SHARE * r.last().y
        r = r.filter { it.x <= tipEnd }.ifEmpty { listOf(r.first()) }
        if (r.last().x < tipEnd - FILL_STEP) r = r + fillRun(r.last().x, tipEnd) { r.last().y }
        if (r.first().x > FILL_STEP) {
            val first = r.first()
            val ratio = across?.let { first.y / readAt(it, first.x).coerceAtLeast(LEAST_RADIUS) }
            val root = fillRun(0.0, first.x) { x -> maxOf(least?.invoke(x) ?: 0.0, if (ratio != null && across != null) readAt(across, x) * ratio else first.y) }
            r = root + r
        }
    }
    r = bridged(r)
    return evened(r)
}

/** Radii every [FILL_STEP] from [from] up to [to], each [radius] there. */
private fun fillRun(from: Double, to: Double, radius: (Double) -> Double): Radii {
    val count = maxOf(1, Math.ceil((to - from) / FILL_STEP).toInt())
    return (0 until count).map { k -> from + (to - from) * k / count }.filter { it < to }.map { P2(it, radius(it)) }
}

/** [r] with each gap wider than a few samples bridged by a cubic following the slopes either side, held to the gap's secant's sign. */
private fun bridged(r: Radii): Radii {
    if (r.size < 2) return r
    val out = mutableListOf(r.first())
    for (i in 1 until r.size) {
        val a = r[i - 1]
        val b = r[i]
        if (b.x - a.x > 2 * FILL_STEP) {
            val secant = (b.y - a.y) / (b.x - a.x)
            val m0 = monotone(if (i >= 2) (a.y - r[i - 2].y) / (a.x - r[i - 2].x).coerceAtLeast(1e-9) else 0.0, secant)
            val m1 = monotone(if (i + 1 < r.size) (r[i + 1].y - b.y) / (r[i + 1].x - b.x).coerceAtLeast(1e-9) else 0.0, secant)
            out += fillRun(a.x, b.x) { x -> hermite(a, m0, b, m1, x) }.drop(1)
        }
        out += b
    }
    return out
}

private fun monotone(slope: Double, secant: Double): Double =
    if (slope * secant <= 0) 0.0 else Math.signum(slope) * minOf(abs(slope), 3 * abs(secant))

private fun hermite(a: P2, m0: Double, b: P2, m1: Double, x: Double): Double {
    val h = b.x - a.x
    val t = (x - a.x) / h
    val t2 = t * t
    val t3 = t2 * t
    return (2 * t3 - 3 * t2 + 1) * a.y + (t3 - 2 * t2 + t) * h * m0 + (-2 * t3 + 3 * t2) * b.y + (t3 - t2) * h * m1
}

/** [r] evened by a moving average one place each way, its ends kept. */
private fun evened(r: Radii): Radii = r.mapIndexed { i, p ->
    if (i == 0 || i == r.lastIndex) p else P2(p.x, (r[i - 1].y + p.y + r[i + 1].y) / 3)
}

/** A side's opposite across its part, outside the pair a halved view draws. */
private val OPPOSITE_SIDE = mapOf("front" to "back", "back" to "front", "up" to "down", "down" to "up")

/**
 * A profile no view reads, from those that are read: the opposite side's where only one of a pair is read; a limb as deep as it is wide (half its outer and inner sides together at each place)
 * or as wide as it is deep, a trunk as round as its one read pair, half that pair's two sides together.
 */
private fun unseen(spec: FigurePartSpec, name: String, out: Map<String, Radii>): Radii? {
    val acrossNames = if (spec.kind == FigureKind.LIMB) listOf("outer", "inner") else listOf("left")
    if (name in acrossNames && acrossNames.any { it in out }) return null
    OPPOSITE_SIDE[name]?.let { other -> out[other]?.let { return it } }
    val read = if (name in acrossNames) out.filterKeys { it !in acrossNames } else out.filterKeys { it in acrossNames }
    val curves = read.values.toList().ifEmpty { return null }
    val first = curves.first()
    return first.map { p -> P2(p.x, curves.map { readAt(it, p.x) }.average()) }
}

/** [r] with [offset] added at each place, never below the least radius. */
private fun offset(r: Radii, offset: Profile?): Radii =
    if (offset == null) r else r.map { P2(it.x, maxOf(LEAST_RADIUS, it.y + offset[it.x])) }
