package dev.chieppa.squash.screens.edit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.DEFAULT_OVERSKETCH_FALLOFF
import dev.chieppa.squash.geom.OversketchOptions

/** The point of an outline that is selected: an index into its points. */
data class CurveSelection(val part: PartId, val view: View, val index: Int)

/**
 * The last edit that replaced part of an outline. [curve] is the outline it made, so the readouts show it only while that
 * is still what the document holds. [wholePart] says the mesh rebuilt beyond the touched sections, which a far-end move
 * and a Front outline do by their nature.
 */
data class Refit(
    val part: PartId,
    val view: View,
    val curve: OutlineCurve,
    val firstSection: Int,
    val sections: Int,
    val wholePart: Boolean,
)

/** What the curve tools remember above the document. Never saved or undone. */
@Stable
class CurveEditState {
    var selection by mutableStateOf<CurveSelection?>(null)
    var falloff by mutableStateOf(DEFAULT_OVERSKETCH_FALLOFF)
    var snapEnds by mutableStateOf(true)

    /** Whether a handle drag holds a gesture open on the history. */
    var dragging by mutableStateOf(false)

    var refit by mutableStateOf<Refit?>(null)

    /** Anchors marked Corner, whose handles move alone though they happen to line up. */
    private val corners = mutableStateMapOf<CurveSelection, Boolean>()

    /** How far each fitted outline strays from its stroke, in screen pixels. Outlines not fitted here have no entry. */
    private val fitErrors = mutableStateMapOf<OutlineCurve, Float>()

    val options: OversketchOptions get() = OversketchOptions(falloff, snapEnds)

    fun recordFit(curve: OutlineCurve, errorPixels: Float) {
        fitErrors[curve] = errorPixels
    }

    fun fitError(curve: OutlineCurve?): Float? = curve?.let { fitErrors[it] }

    fun isMarkedCorner(anchor: CurveSelection): Boolean = corners[anchor] == true

    fun markCorner(anchor: CurveSelection, corner: Boolean) {
        if (corner) corners[anchor] = true else corners.remove(anchor)
    }

    /** The selection if it still names a point of [curve] on [part] in [view]. */
    fun selectionIn(part: PartId, view: View, curve: OutlineCurve): CurveSelection? =
        selection?.takeIf { it.part == part && it.view == view && it.index in curve.points.indices }

    fun clear() {
        selection = null
        refit = null
        corners.clear()
    }
}

/** Whether going from [before] to [after] in [view] rebuilds more of the part than the sections touched. */
fun reachesWholePart(view: View, before: OutlineCurve, after: OutlineCurve): Boolean {
    if (view == View.FRONT) return true
    val old = before.toPath().flatten().xs
    val new = after.toPath().flatten().xs
    return old.min() != new.min() || old.max() != new.max()
}
