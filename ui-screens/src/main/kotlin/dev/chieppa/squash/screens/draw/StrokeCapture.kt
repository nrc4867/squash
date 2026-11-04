package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.geom.GuideSnap
import dev.chieppa.squash.geom.SnapGuide
import dev.chieppa.squash.geom.StrokeMark
import dev.chieppa.squash.geom.StrokeMarkKind
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.guideSnap
import dev.chieppa.squash.geom.trailed

/**
 * A stroke as the pointer events drive it, in a view's plane: the line trailing the pointer on a string, the pointer's own
 * tip, and the points modifiers forced. Nothing here reads a clock, so the same events make the same stroke.
 */
@Stable
internal class StrokeCapture {
    private val line = mutableStateListOf<Vec2>()
    private val forced = mutableStateListOf<StrokeMark>()
    private var pointer by mutableStateOf<Vec2?>(null)
    private var string = 0f
    private var altHeld = false
    private var shiftHeld = false

    val isEmpty: Boolean get() = pointer == null

    /** The stroke as release would take it: the trailing line, then on to the pointer. */
    val samples: List<Vec2>
        get() {
            val tip = pointer ?: return emptyList()
            return if (line.lastOrNull() == tip) line.toList() else line + tip
        }

    val marks: List<StrokeMark> get() = forced.toList()

    /** Where the trailing line has got to. */
    val lineEnd: Vec2? get() = line.lastOrNull()

    fun begin(at: Vec2, stringLength: Float, alt: Boolean, shift: Boolean) {
        clear()
        string = stringLength
        pointer = at
        line += at
        altHeld = alt
        shiftHeld = shift
    }

    fun moveTo(at: Vec2) {
        pointer = at
        val next = trailed(line.last(), at, string)
        if (next != line.last()) line += next
    }

    /** What pressing a modifier since the last event forces, which [mark] sets at the pointer's next position. A modifier held on does nothing more. */
    fun modifiers(alt: Boolean, shift: Boolean): StrokeMarkKind? {
        val kind = when {
            alt && !altHeld -> StrokeMarkKind.CORNER
            shift && !shiftHeld -> StrokeMarkKind.SMOOTH
            else -> null
        }
        altHeld = alt
        shiftHeld = shift
        return kind
    }

    /** Forces [kind] where the pointer is: the string goes slack there, so the mark lies on the line. */
    fun mark(kind: StrokeMarkKind) {
        val at = pointer ?: return
        if (line.last() != at) line += at
        forced += StrokeMark(at, kind)
    }

    fun clear() {
        line.clear()
        forced.clear()
        pointer = null
    }
}

/** [from], the line's end [end] and [forced] as [guides] in the plane leave them, within [reach]. */
internal class SnappedStroke(from: List<Vec2>, forced: List<StrokeMark>, end: Vec2?, guides: List<SnapGuide>, reach: Float) {
    private val snap: GuideSnap = guideSnap(from, guides, reach)
    val samples: List<CurvePoint> = from.map { snap.moved(it).let { p -> CurvePoint(p.x, p.y) } }
    val marks: List<StrokeMark> = forced.map { it.copy(at = snap.moved(it.at)) }
    val lineEnd: Vec2? = end?.let(snap::moved)
    val caught: Set<Int> get() = snap.caught
}
