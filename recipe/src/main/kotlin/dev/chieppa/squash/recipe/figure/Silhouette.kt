package dev.chieppa.squash.recipe.figure

/** Steps an edge is looked for in, out from a place inside the body, before it is bisected. */
private const val EDGE_STEP = 0.002

/** Bisections an edge is narrowed by. */
private const val BISECTIONS = 9

/** The silhouette a body casts in a figure's front or side view, in the view's places: x across, y up from the ground. */
interface Silhouette {
    /** Whether the view place [q] lies inside the body's silhouette in [view]. */
    fun inside(view: FigureView, q: P2): Boolean

    /** The silhouette the surface of the parts named [parts] casts alone, as the document names them; null where none can be read. */
    fun alone(parts: Set<String>): Silhouette? = null

    /** How far along [direction] from [at], a place inside the silhouette, its edge lies, within [reach]; null from a place outside or with no edge that near. */
    fun reachFrom(view: FigureView, at: P2, direction: P2, reach: Double): Double? {
        if (!inside(view, at)) return null
        var inner = 0.0
        var outer: Double? = null
        while (outer == null && inner < reach) {
            val t = minOf(reach, inner + EDGE_STEP)
            if (inside(view, at + direction * t)) inner = t else outer = t
        }
        var out = outer ?: return null
        repeat(BISECTIONS) {
            val mid = (inner + out) / 2
            if (inside(view, at + direction * mid)) inner = mid else out = mid
        }
        return (inner + out) / 2
    }

    /**
     * How far along [normal] from [at] the silhouette's edge lies, within [reach] either way: positive where the body stands out past
     * [at], negative where it falls short; null where no edge crosses that stretch. The edge is the first one out from [at]. In the
     * front view the search outward stops at the body's middle, the edge of its drawn half: a gap drawn between two limbs that the body
     * closes all the way stands out by as far as the middle.
     */
    fun edge(view: FigureView, at: P2, normal: P2, reach: Double): Double? {
        var inner = -reach
        if (!inside(view, at + normal * inner)) return null
        var outer: Double
        if (!inside(view, at)) {
            outer = 0.0
        } else {
            val limit = if (view == FigureView.FRONT && at.x > 0 && normal.x < 0) minOf(reach, at.x / -normal.x) else reach
            var t = 0.0
            var out: Double? = null
            while (t < limit && out == null) {
                inner = t
                t = minOf(limit, t + EDGE_STEP)
                if (!inside(view, at + normal * t)) out = t
            }
            outer = out ?: return if (limit < reach) limit else null
        }
        repeat(BISECTIONS) {
            val mid = (inner + outer) / 2
            if (inside(view, at + normal * mid)) inner = mid else outer = mid
        }
        return (inner + outer) / 2
    }
}
