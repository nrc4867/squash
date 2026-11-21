package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.convexHull
import dev.chieppa.squash.geom.outsideHull

/**
 * How far round the head, in degrees from the front toward the character's left, a fit reads the head and its mass from: a view and the
 * one opposite it see the same silhouette, so these four stand for every side.
 */
private val ENVELOPE_TURNS = listOf(0f, 45f, 90f, 135f)

/** The head and its hair mass seen from [view]: the convex hull of their [points] in its flat frame, in drawn units. */
internal class HeadEnvelope(val view: HeadView, points: List<Vec3>) {
    private val hull: List<Vec2> = convexHull(points.map(view::flat))

    /** How high the hull reaches, and how tall it stands. */
    val top: Float = hull.maxOfOrNull { it.y } ?: 0f
    val height: Float = (top - (hull.minOfOrNull { it.y } ?: 0f)).coerceAtLeast(1e-4f)

    /** How far [p], in body space, shows outside the head and its mass, 0 over them. */
    fun outside(p: Vec3): Float = outsideHull(hull, view.flat(p))

    /** [outside] out to the sides and below, 0 where [p] stands over the top of the head and mass, where spikes stand. */
    fun aside(p: Vec3): Float = if (view.flat(p).y > top) 0f else outside(p)

    companion object {
        /** The head and mass's [points] seen from each of [ENVELOPE_TURNS] round from [front], the front first. */
        fun around(front: HeadView, points: List<Vec3>): List<HeadEnvelope> = ENVELOPE_TURNS.map { HeadEnvelope(front.turned(it), points) }
    }
}
