package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES = (PI / 180.0).toFloat()

/**
 * A head seen from one of a hair outline's views, as an orthographic camera: a place in body space to [flat], x along [right] and y along
 * [up] in the head's drawn units (body units over its [scale]) from its [origin], and how far toward the viewer it lies ([depth]). From
 * the front it is the frame face places use.
 */
class HeadView(val view: OutlineView, val origin: Vec3, val right: Vec3, val up: Vec3, val toViewer: Vec3, val scale: Float) {
    /** Where [p] shows, in drawn units. */
    fun flat(p: Vec3): Vec2 {
        val d = p - origin
        return Vec2((d dot right) / scale, (d dot up) / scale)
    }

    /** How far [p] lies toward the viewer from the head's origin, in drawn units. */
    fun depth(p: Vec3): Float = ((p - origin) dot toViewer) / scale

    /** The place in body space that shows at [at], [depth] drawn units toward the viewer from the head's origin. */
    fun body(at: Vec2, depth: Float = 0f): Vec3 = origin + right * (at.x * scale) + up * (at.y * scale) + toViewer * (depth * scale)

    /** A body-space length in drawn units. */
    fun drawn(length: Float): Float = length / scale

    /** This view with its camera turned [degrees] further round the head's up toward the character's left; it keeps its [view]'s name. */
    fun turned(degrees: Float): HeadView {
        val turn = degrees * DEGREES
        val toward = (toViewer * cos(turn) + (up cross toViewer) * sin(turn)).normalized()
        return HeadView(view, origin, up cross toward, up, toward, scale)
    }

    companion object {
        /** [head], a head [size] times its drawn size, seen from [view]. */
        fun of(head: PlacedPart, size: Float, view: OutlineView): HeadView {
            val ahead = PartBuilder.facing(head.template).let { if (head.mirrored) it.mirrored else it }.normalized()
            val up = (Vec3.Y - ahead * (Vec3.Y dot ahead)).normalized()
            val left = up cross ahead
            val turn = view.turn * DEGREES
            val toViewer = (ahead * cos(turn) + left * sin(turn)).normalized()
            val right = up cross toViewer
            return HeadView(view, head.origin, right, up, toViewer, size.coerceAtLeast(1e-4f))
        }

        /** How many times its drawn size [head] is built, its size parameter. */
        fun sizeOf(character: Character, head: PlacedPart): Float {
            val instance = head.id?.let(character::part)
            val params = instance?.let { character.resolvedShape(it).params }.orEmpty()
            return params[SIZE_PARAM] ?: head.template.defaults[SIZE_PARAM] ?: 1f
        }
    }
}
