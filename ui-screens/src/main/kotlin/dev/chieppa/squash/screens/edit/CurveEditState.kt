package dev.chieppa.squash.screens.edit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.DEFAULT_OVERSKETCH_FALLOFF
import dev.chieppa.squash.geom.DEFAULT_STROKE_SMOOTH
import dev.chieppa.squash.geom.DEFAULT_STROKE_STABILIZER
import dev.chieppa.squash.geom.OversketchOptions
import dev.chieppa.squash.geom.StrokeMark
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.screens.draw.centreLine
import dev.chieppa.squash.screens.sheet.DEFAULT_GHOST

/** The point of an outline that is selected: an index into its points. */
data class CurveSelection(val part: PartId, val view: View, val index: Int)

/** How much of the mesh an edit rebuilt: the [SECTIONS] it touched, the [CLOSED_ENDS] a Front cap blends into, or the [WHOLE_PART]. */
enum class RefitReach { SECTIONS, CLOSED_ENDS, WHOLE_PART }

/** The last edit that replaced part of an outline. [curve] is the outline it made, so the readouts show it only while that is still what the document holds. */
data class Refit(
    val part: PartId,
    val view: View,
    val curve: OutlineCurve,
    val firstSection: Int,
    val sections: Int,
    val reach: RefitReach,
)

/** What the curve tools remember above the document. Never saved or undone. */
@Stable
class CurveEditState {
    var selection by mutableStateOf<CurveSelection?>(null)
    var falloff by mutableStateOf(DEFAULT_OVERSKETCH_FALLOFF)
    var snapEnds by mutableStateOf(true)
    var smooth by mutableStateOf(DEFAULT_STROKE_SMOOTH)
    var snapLine by mutableStateOf(true)
    var closeEnd by mutableStateOf(true)
    var holdSnap by mutableStateOf(true)
    var stabilizer by mutableStateOf(DEFAULT_STROKE_STABILIZER)
    var snapGuides by mutableStateOf(true)
    var halfOutline by mutableStateOf(false)

    /** The Hint section: the template's dashed outline where a view is not drawn, and how strongly the rest of the character shows. */
    var templateHint by mutableStateOf(true)
    var ghost by mutableStateOf(DEFAULT_GHOST)

    /** The view a stroke is being drawn in, which reads as Drawing. */
    var drawing by mutableStateOf<View?>(null)

    /** The view the Single layout's camera is snapped to, which the status bar names; null while it is free or in the panes. */
    var snapped by mutableStateOf<View?>(null)

    /** The pressure of the pen drawing a stroke, 0 to 1; null while no pen is. */
    var penPressure by mutableStateOf<Float?>(null)

    /** How many places the selected part's views disagree, as the panes highlight them. */
    var conflicts by mutableStateOf(0)

    /** Whether a handle drag holds a gesture open on the history. */
    var dragging by mutableStateOf(false)

    var refit by mutableStateOf<Refit?>(null)

    /** Anchors marked Corner, whose handles move alone though they happen to line up. */
    private val corners = mutableStateMapOf<CurveSelection, Boolean>()

    /** How far each fitted outline strays from its stroke, in screen pixels. Outlines not fitted here have no entry. */
    private val fitErrors = mutableStateMapOf<OutlineCurve, Float>()

    val strokeOptions: StrokeOptions get() = StrokeOptions(smooth, snapLine, closeEnd)

    val options: OversketchOptions get() = OversketchOptions(falloff, snapEnds, strokeOptions)

    /** [strokeOptions] for a stroke in [view] with [marks] forced along it. */
    fun strokeOptions(view: View, marks: List<StrokeMark>): StrokeOptions =
        strokeOptions.copy(half = centreLine(view).takeIf { halfOutline }, marks = marks)

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

/**
 * How much of [template]'s mesh going from [before] to [after] in [view] rebuilds. Front sets every ring when it sets the
 * section, and only the rings near the closed ends when it sets the cap. Side and Top reach every ring when an end moves.
 */
fun refitReach(view: View, before: OutlineCurve, after: OutlineCurve, template: PartTemplate): RefitReach {
    if (view == View.FRONT) return if (template.views[View.FRONT] == Sets.CAP) RefitReach.CLOSED_ENDS else RefitReach.WHOLE_PART
    val old = before.toPath().flatten().xs
    val new = after.toPath().flatten().xs
    return if (old.min() != new.min() || old.max() != new.max()) RefitReach.WHOLE_PART else RefitReach.SECTIONS
}
