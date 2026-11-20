package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.geom.Vec3
import kotlin.math.roundToInt

/** How finely a view's weights are told apart: a camera moving less than this share of a key's weight shows what it showed. */
private const val WEIGHT_STEPS = 256f

/** How many views' keyed characters are kept: a few cameras, and the steps a turning camera passes through. */
private const val KEPT_VIEWS = 6

/**
 * [shapes] blended for views, each view's result kept while its weights, and the body, lines and surface it was blended over, stay
 * the same, so a camera that holds still, and every pane and line asking for the same view, blend once.
 */
class ViewKeyCache(val shapes: ViewKeyShapes) {
    private class Key(val body: AssembledCharacter, val lines: BodyLines, val surface: PosedSurface?, val weights: List<Int>) {
        override fun equals(other: Any?) = other is Key && other.body === body && other.lines === lines && other.surface === surface && other.weights == weights
        override fun hashCode() = weights.hashCode() * 31 + System.identityHashCode(body)
    }

    private val kept = object : LinkedHashMap<Key, KeyedView>(KEPT_VIEWS, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, KeyedView>) = size > KEPT_VIEWS
    }

    /** [body], its [lines], [work] and one [surface] as a viewer whose way from a point is [toViewer] sees them with the head cheats. */
    fun keyed(body: AssembledCharacter, lines: BodyLines, work: BodySurfaceWork, surface: PosedSurface?, toViewer: (Vec3) -> Vec3): KeyedView {
        if (shapes.isEmpty) return KeyedView(body, lines, surface)
        val steps = shapes.weightsFor(body, toViewer).mapValues { (_, w) -> IntArray(w.size) { (w[it] * WEIGHT_STEPS).roundToInt() } }
        val key = Key(body, lines, surface, shapes.headOrder.flatMap { steps[it]?.toList().orEmpty() })
        synchronized(kept) { kept[key]?.let { return it } }
        val weights = steps.mapValues { (_, s) -> FloatArray(s.size) { s[it] / WEIGHT_STEPS } }
        val made = shapes.keyed(body, lines, work, surface, weights)
        synchronized(kept) { kept[key] = made }
        return made
    }
}
