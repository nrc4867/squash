package dev.chieppa.squash.geom

/** A mesh's reach across a view counts as this many pixels, so a fit's tolerances scale with the part. */
private const val PIXELS_ACROSS = 300f

/** The outermost crossings of [patches]' swept surfaces in a view that takes a point to the plane with [onPlane], or null if they have no extent there. */
fun meshProfile(patches: List<PatchMesh>, onPlane: (Vec3) -> Vec2): OutlineProfile? {
    val edges = ArrayList<Pair<Vec2, Vec2>>()
    for (mesh in patches) {
        if (mesh.patch.slices == null) continue
        fun at(corner: Int) = onPlane(mesh.patch.point(mesh.indices[corner]))
        for (t in 0 until mesh.triangleCount) {
            val a = at(t * 3)
            val b = at(t * 3 + 1)
            val c = at(t * 3 + 2)
            edges += a to b
            edges += b to c
            edges += c to a
        }
    }
    return OutlineProfile.ofEdges(edges)
}

/**
 * The curve a stroke tracing [patches]' silhouette would make, fitted as a stroke is, between corners, and ending as [ends]
 * says. It reads outermost crossings, which is all an outline can set, so an overhang in the view is filled. Null if the
 * patches have no extent in the view or none on the kept side of a plane.
 */
fun silhouetteCurve(patches: List<PatchMesh>, onPlane: (Vec3) -> Vec2, ends: StrokeEnds): CleanStroke? {
    val profile = meshProfile(patches, onPlane) ?: return null
    val run = profile.run()
    val reach = maxOf(profile.end - profile.start, run.maxOf { it.y } - run.minOf { it.y })
    val pixel = reach / PIXELS_ACROSS
    val spacing = strokeSpacing(pixel)
    val passes = smoothPasses(DEFAULT_STROKE_SMOOTH)
    val stroke = if (ends is StrokeEnds.CutAtPlane) {
        val line = smoothed(resample(run, spacing), closed = false, passes)
        val kept = cutAtPlane(line, closed = false, ends, spacing) ?: return null
        fitCleaned(resample(kept.line, spacing), closed = false, pixel)
    } else {
        val line = smoothed(resample(run + run.first(), spacing), closed = true, passes)
        fitCleaned(resample(line, spacing), closed = true, pixel)
    }
    val cut = ends as? StrokeEnds.CutAtPlane
    val low = if (cut?.above == true) cut.plane else profile.start
    val high = if (cut?.above == false) cut.plane else profile.end
    return stroke.copy(path = stroke.path.flushedTo(low, high, pixel))
}

/**
 * This path with every point within [reach] of [low] or [high] in x moved onto it. A flat end then lies exactly in its
 * plane, so a profile reads the whole of it there rather than the one point that happens to reach furthest.
 */
private fun BezierPath.flushedTo(low: Float, high: Float, reach: Float): BezierPath {
    fun flush(p: Vec2) = when {
        p.x - low <= reach -> Vec2(low, p.y)
        high - p.x <= reach -> Vec2(high, p.y)
        else -> p
    }
    return BezierPath(segments.map { CubicBezier(flush(it.p0), flush(it.p1), flush(it.p2), flush(it.p3)) })
}
