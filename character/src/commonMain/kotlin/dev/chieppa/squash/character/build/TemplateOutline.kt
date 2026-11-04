package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.CentreLine
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.silhouetteCurve

/**
 * How an outline in [view] ends on a part whose template opens at [openEnd]. Side and Top run along the axis, so they meet
 * the socket plane at along = 0; Front looks down the axis and never does.
 */
fun outlineEnds(view: View, openEnd: End?): StrokeEnds = when {
    view == View.FRONT || openEnd == null -> StrokeEnds.Closed
    else -> StrokeEnds.CutAtPlane(above = openEnd == End.START)
}

/** The line Half outline mirrors across in [view]: the part's axis where the view runs along it, and straight down the middle where it looks along it. */
fun centreLine(view: View): CentreLine = if (view == View.FRONT) CentreLine.ALONG_Y else CentreLine.ALONG_X

/** A point of a part in the plane of [view], undoing [pointOnView]. */
fun PartAxes.viewPoint(view: View, point: Vec3): Vec2 = when (view) {
    View.SIDE -> Vec2(point dot along, point dot up)
    View.TOP -> Vec2(point dot along, -(point dot side))
    View.FRONT -> Vec2(point dot side, point dot up)
}

/**
 * The outline of a part of [unplaced] resolving to [data], as the build shows it in [view] with nothing drawn there: its
 * template hint as an ordinary curve. Null if the part has no extent in the view.
 */
fun templateOutline(unplaced: PartTemplate, data: PartData, view: View): OutlineCurve? {
    val template = unplaced.placedBy(data.params)
    val axes = PartBuilder.axes(template)
    val scale = 1f / ((template.defaults + data.params)[SIZE_PARAM] ?: 1f)
    val mesh = PartBuilder.mesh(template.id.value, template, PartBuilder.build(template, data.params, data.outlines - view))
    val stroke = silhouetteCurve(mesh.patches, { axes.viewPoint(view, it * scale) }, outlineEnds(view, template.openEnd))
    return stroke?.path?.toOutline()
}
