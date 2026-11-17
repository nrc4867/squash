package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.geom.LineShape
import dev.chieppa.squash.geom.StrokeShape
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.ellipseInBox

/**
 * The shape a held modifier turns a stroke into while it is held: Ctrl a straight line from where the stroke began to the
 * pointer, Ctrl and Shift together the ellipse filling the box between them. Alt and Shift alone force points along a
 * freehand stroke, so neither snaps on its own.
 */
enum class ModifierSnap {
    LINE,
    ELLIPSE;

    /** This snap's shape from [start] to [pointer], or null while they make none. */
    fun shape(start: Vec2, pointer: Vec2): StrokeShape? = when (this) {
        LINE -> if (start == pointer) null else LineShape(start, pointer)
        ELLIPSE -> ellipseInBox(start, pointer)
    }

    companion object {
        /** The snap the held [ctrl] and [shift] ask for, or null for a freehand stroke. */
        fun of(ctrl: Boolean, shift: Boolean): ModifierSnap? = when {
            ctrl && shift -> ELLIPSE
            ctrl -> LINE
            else -> null
        }
    }
}
