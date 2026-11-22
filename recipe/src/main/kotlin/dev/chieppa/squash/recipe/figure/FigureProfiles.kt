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
    val order = if (spec.kind == FigureKind.TRUNK) listOf("left", "front", "back") else listOf("outer", "inner", "front", "back")
    val read = HashMap<String, Radii>()
    for (view in FigureView.entries) {
        if (spec.part !in reads.getValue(view)) continue
        for (side in BoneSide.entries) {
            if (view == FigureView.FRONT && spec.kind == FigureKind.TRUNK && side == BoneSide.MINUS) continue
            val name = profileName(spec.kind, view, side)
            val samples = split.getValue(view).taken.filter { it.part == spec.part && it.side == side }
            val ends = endsUnder(spec, view, side, split.getValue(view), specs)
            read[name] = binned(samples.filter { it.x <= ends }, input.spacing)
        }
    }
    for (name in order) {
        val seen = read[name].orEmpty()
        val across = when (name) {
            "inner" -> out["outer"]
            "outer" -> null
            else -> null
        }
        val radii = when {
            seen.isNotEmpty() -> filled(spec, seen, across)
            name == "front" || name == "back" -> roundDepth(spec, out) ?: continue
            else -> continue
        }
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
 * outer side, and the whole evened out.
 */
private fun filled(spec: FigurePartSpec, seen: Radii, across: Radii?): Radii {
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
            val root = fillRun(0.0, first.x) { x -> if (ratio != null && across != null) readAt(across, x) * ratio else first.y }
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

/** A limb's depth where the side view does not show it: as deep as it is wide, half its outer and inner sides together at each place. */
private fun roundDepth(spec: FigurePartSpec, out: Map<String, Radii>): Radii? {
    if (spec.kind == FigureKind.TRUNK) return out["left"]
    val outer = out["outer"] ?: return null
    val inner = out["inner"] ?: return outer
    return outer.map { P2(it.x, (it.y + readAt(inner, it.x)) / 2) }
}

/** [r] with [offset] added at each place, never below the least radius. */
private fun offset(r: Radii, offset: Profile?): Radii =
    if (offset == null) r else r.map { P2(it.x, maxOf(LEAST_RADIUS, it.y + offset[it.x])) }
