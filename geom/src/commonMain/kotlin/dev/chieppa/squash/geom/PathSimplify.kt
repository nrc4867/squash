package dev.chieppa.squash.geom

/** A join whose two sides turn by more than about five degrees is a corner, which a simplified path keeps. */
private const val SMOOTH_JOIN_COSINE = 0.996f

/** The fewest samples a run between corners is fitted to, and how far apart they lie at most, in tolerances. */
private const val MIN_RUN_SAMPLES = 8
private const val SAMPLE_SPACING_OF_TOLERANCE = 2f

/** Each refit of a simplification may stray this share of its tolerance from the path before it. */
private const val STEP_OF_TOLERANCE = 0.5f

/**
 * [path] refitted again and again, each time within half of [tolerance] of the path before, until a refit has no fewer
 * sections. The result is what a refit leaves as it is, so simplifying it again changes nothing. See [refitPath] for one
 * refit. [path] itself when the first refit would not have fewer sections.
 */
fun simplifiedPath(path: BezierPath, tolerance: Float, plane: StrokeEnds.CutAtPlane? = null): BezierPath {
    var current = path
    while (true) {
        val next = refitPath(current, tolerance * STEP_OF_TOLERANCE, plane)
        if (next.segments.size >= current.segments.size) return current
        current = next
    }
}

/**
 * [path] refitted with as few sections as stay within [tolerance] of it: its corners and ends stay where they are and as
 * sharp, and each run between them is sampled evenly and fitted again by [fitFewCubics], leaving and reaching each corner
 * along the old directions. A closed path with no corner keeps its seam and the direction through it. With [plane], what is
 * fitted stays on that plane's kept side. [path] itself when the refit would not have fewer sections.
 */
fun refitPath(path: BezierPath, tolerance: Float, plane: StrokeEnds.CutAtPlane? = null): BezierPath {
    val segments = path.segments
    if (segments.size < 2) return path
    val closed = segments.first().p0 == segments.last().p3
    val corners = segments.indices.filter { i ->
        if (i == 0 && !closed) return@filter false
        val before = segments[(i - 1 + segments.size) % segments.size]
        (before.arriving() dotWith segments[i].leaving()) > -SMOOTH_JOIN_COSINE
    }
    val start = if (closed) corners.firstOrNull() ?: 0 else 0
    val ring = segments.subList(start, segments.size) + segments.subList(0, start)
    val cuts = (listOf(0) + corners.map { (it - start + segments.size) % segments.size } + ring.size).distinct().sorted()
    val refit = cuts.zipWithNext().flatMap { (from, to) -> refitRun(ring.subList(from, to), tolerance) }
    if (refit.size >= segments.size) return path
    val kept = if (plane == null || closed) refit else keptOnPlaneSide(refit, plane, freeStart = false, freeEnd = false)
    return BezierPath(kept)
}

private fun refitRun(run: List<CubicBezier>, tolerance: Float): List<CubicBezier> {
    if (run.size < 2) return run
    val runPath = BezierPath(run)
    val length = arcLength(runPath.flatten().let { flat -> flat.xs.indices.map { Vec2(flat.xs[it], flat.ys[it]) } })
    val spacing = minOf(SAMPLE_SPACING_OF_TOLERANCE * tolerance, length / MIN_RUN_SAMPLES)
    if (spacing <= 0f) return run
    val points = CurveSamples(runPath, spacing).points
    val fitted = fitFewCubics(points, tolerance, run.first().leaving(), run.last().arriving()).toMutableList()
    fitted[0] = fitted.first().copy(p0 = run.first().p0)
    fitted[fitted.lastIndex] = fitted.last().copy(p3 = run.last().p3)
    return fitted
}
