package dev.chieppa.squash.document

import kotlin.math.sqrt

private const val PER_SEGMENT = 3

/** Below this cosine, the two handles at an anchor point opposite ways and the anchor counts as smooth. */
private const val COLLINEAR_COSINE = -0.999f

/** The plane a curve's open ends sit on: the curve keeps to the side of x = [at] that [above] says. */
data class CurveLimit(val at: Float, val above: Boolean) {
    fun held(point: CurvePoint): CurvePoint {
        val past = if (above) point.x < at else point.x > at
        return if (past) point.copy(x = at) else point
    }
}

/** Whether point [index] of an outline is an anchor on the curve, not a handle. */
fun isAnchor(index: Int): Boolean = index % PER_SEGMENT == 0

/** The section point [index] shapes: a handle's own; an anchor's is the one it starts, or the last for the last anchor. */
fun sectionOf(index: Int, sections: Int): Int = (index / PER_SEGMENT).coerceAtMost(sections - 1)

val OutlineCurve.sections: Int get() = (points.size - 1) / PER_SEGMENT

val OutlineCurve.closed: Boolean get() = points.first() == points.last()

/** Point indices of the anchors, a closed curve's repeated last point left out. */
val OutlineCurve.anchors: List<Int>
    get() = (0..sections * PER_SEGMENT step PER_SEGMENT).filter { !closed || it != points.lastIndex }

/** The anchor handle [index] hangs from. */
fun anchorOf(index: Int): Int = when (index % PER_SEGMENT) {
    1 -> index - 1
    2 -> index + 1
    else -> index
}

/** The handle across [anchor] from [handle], or null at an open end. */
fun OutlineCurve.oppositeHandle(anchor: Int, handle: Int): Int? {
    val across = 2 * anchor - handle
    return when {
        across in points.indices -> across
        !closed -> null
        across < 0 -> points.lastIndex - 1
        else -> 1
    }
}

/** The handles at [anchor]: one, at an open end, or two. */
fun OutlineCurve.handlesAt(anchor: Int): List<Int> = buildList {
    if (anchor > 0) add(anchor - 1) else if (closed) add(points.lastIndex - 1)
    if (anchor < points.lastIndex) add(anchor + 1) else if (closed) add(1)
}

/** Whether both handles at [anchor] lie on one line through it, so it turns without a corner. False at an open end. */
fun OutlineCurve.isSmooth(anchor: Int): Boolean {
    val handles = handlesAt(anchor)
    if (handles.size < 2) return false
    val at = points[anchor]
    val first = points[handles[0]] - at
    val second = points[handles[1]] - at
    val lengths = first.length * second.length
    return lengths > 0f && (first.x * second.x + first.y * second.y) / lengths <= COLLINEAR_COSINE
}

/**
 * This curve with point [index] at [to]. An anchor carries its handles with it; on an open curve with a [limit], an end
 * stays on the limit's plane and nothing goes past it. A handle at a smooth anchor swings the opposite handle to stay in
 * line, keeping that handle's length, unless [corner] says the anchor is a corner.
 */
fun OutlineCurve.moved(index: Int, to: CurvePoint, limit: CurveLimit? = null, corner: Boolean = false): OutlineCurve {
    val next = points.toMutableList()
    val applied = if (limit != null && !closed) limit else null
    fun held(point: CurvePoint) = applied?.held(point) ?: point
    if (isAnchor(index)) {
        val end = index == 0 || index == points.lastIndex
        val target = if (end && applied != null) to.copy(x = applied.at) else held(to)
        val shift = target - points[index]
        next[index] = target
        if (closed && end) next[if (index == 0) points.lastIndex else 0] = target
        handlesAt(index).forEach { next[it] = held(points[it] + shift) }
    } else {
        val target = held(to)
        val anchor = anchorOf(index)
        next[index] = target
        val across = oppositeHandle(anchor, index)
        if (across != null && !corner && isSmooth(anchor)) next[across] = held(swung(points[anchor], target, points[across]))
    }
    return OutlineCurve(next)
}

/** The handle at the distance of [opposite] from [anchor], pointing away from [handle]. */
private fun swung(anchor: CurvePoint, handle: CurvePoint, opposite: CurvePoint): CurvePoint {
    val out = handle - anchor
    val length = out.length
    if (length == 0f) return opposite
    val reach = (opposite - anchor).length / length
    return CurvePoint(anchor.x - out.x * reach, anchor.y - out.y * reach)
}

/** This curve with [anchor]'s second handle swung into line with its first, so the anchor turns smoothly. */
fun OutlineCurve.smoothed(anchor: Int, limit: CurveLimit? = null): OutlineCurve {
    val handles = handlesAt(anchor)
    if (handles.size < 2) return this
    val next = points.toMutableList()
    val aligned = swung(points[anchor], points[handles[0]], points[handles[1]])
    next[handles[1]] = limit?.takeIf { !closed }?.held(aligned) ?: aligned
    return OutlineCurve(next)
}

/** The sections of [other] that differ from this curve's, first to last, or null if none does or the two differ in size. */
fun OutlineCurve.changedSections(other: OutlineCurve): IntRange? {
    if (points.size != other.points.size) return null
    val changed = (0 until sections).filter { section ->
        (0..PER_SEGMENT).any { points[section * PER_SEGMENT + it] != other.points[section * PER_SEGMENT + it] }
    }
    return if (changed.isEmpty()) null else changed.first()..changed.last()
}

/**
 * The sections of this curve that gave way in [other], which may hold another number of them: those between the first and
 * last that differ, counting in from each end. Null if the two are the same.
 */
fun OutlineCurve.replacedSections(other: OutlineCurve): IntRange? {
    if (points == other.points) return null
    fun sectionAt(curve: OutlineCurve, section: Int) = curve.points.subList(section * PER_SEGMENT, (section + 1) * PER_SEGMENT + 1)
    val shorter = minOf(sections, other.sections)
    val before = (0 until shorter).firstOrNull { sectionAt(this, it) != sectionAt(other, it) } ?: shorter
    val after = (0 until shorter - before).firstOrNull { sectionAt(this, sections - 1 - it) != sectionAt(other, other.sections - 1 - it) } ?: (shorter - before)
    return before..(sections - 1 - after).coerceAtLeast(before)
}

/**
 * This curve with each run of [erased] sections replaced by one straight section between the anchors around it, so the
 * curve stays whole and its ends stay where they are. Null when that would take every section.
 */
fun OutlineCurve.withoutSections(erased: Set<Int>): OutlineCurve? {
    if ((0 until sections).all { it in erased }) return null
    val next = mutableListOf(points.first())
    var section = 0
    while (section < sections) {
        val from = section * PER_SEGMENT
        if (section in erased) {
            while (section < sections && section in erased) section++
            val to = points[section * PER_SEGMENT]
            val step = (to - points[from]) * (1f / PER_SEGMENT)
            next += points[from] + step
            next += to - step
            next += to
        } else {
            next += points.subList(from + 1, from + PER_SEGMENT + 1)
            section++
        }
    }
    return OutlineCurve(next)
}

private operator fun CurvePoint.times(by: Float) = CurvePoint(x * by, y * by)

private operator fun CurvePoint.minus(o: CurvePoint) = CurvePoint(x - o.x, y - o.y)

private operator fun CurvePoint.plus(o: CurvePoint) = CurvePoint(x + o.x, y + o.y)

private val CurvePoint.length: Float get() = sqrt(x * x + y * y)
