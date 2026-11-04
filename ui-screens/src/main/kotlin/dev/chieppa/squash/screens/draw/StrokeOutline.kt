package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.character.build.outlineEnds
import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.CentreLine
import dev.chieppa.squash.geom.OutlineReading
import dev.chieppa.squash.geom.OversketchOptions
import dev.chieppa.squash.geom.OversketchPreview
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.geom.StrokePreview
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.cleanStroke
import dev.chieppa.squash.geom.crossingsReading
import dev.chieppa.squash.geom.oversketchPreview
import dev.chieppa.squash.geom.sectionReading
import dev.chieppa.squash.geom.strokePreview

/** A stroke as the outline it makes, with how far that strays from the stroke at worst, in screen pixels. */
data class FittedOutline(val curve: OutlineCurve, val errorPixels: Float)

/** How a stroke in [view] ends on a part whose template opens at [openEnd]. */
fun strokeEnds(view: View, openEnd: End?): StrokeEnds = outlineEnds(view, openEnd)

/** The line Half outline mirrors across in [view]: the part's axis where the view runs along it, and straight down the middle where it looks along it. */
fun centreLine(view: View): CentreLine = if (view == View.FRONT) CentreLine.ALONG_Y else CentreLine.ALONG_X

/** What the build reads of [shape] in [view], as [template] says that view is read; null where it reads nothing of it. */
fun outlineReading(shape: List<Vec2>, view: View, template: PartTemplate, pixel: Float): OutlineReading? = when (template.views[view]) {
    Sets.HEIGHT, Sets.WIDTH -> crossingsReading(shape, strokeEnds(view, template.openEnd), pixel)
    Sets.SECTION, Sets.CAP -> sectionReading(shape)
    null -> null
}

/** [samples] in a view's plane, cleaned up as [ends] says, or null if they make no outline. [pixel] is a screen pixel's size in the plane. */
fun fittedOutline(samples: List<CurvePoint>, ends: StrokeEnds, pixel: Float, options: StrokeOptions = StrokeOptions()): FittedOutline? {
    val stroke = cleanStroke(samples.map { Vec2(it.x, it.y) }, ends, pixel, options) ?: return null
    return FittedOutline(stroke.path.toOutline(), stroke.maxError / pixel)
}

/** What [fittedOutline] would make of [samples] if the stroke ended now. */
fun strokeCompletion(samples: List<CurvePoint>, ends: StrokeEnds, pixel: Float, options: StrokeOptions): StrokePreview =
    strokePreview(samples.map { Vec2(it.x, it.y) }, ends, pixel, options)

/** What [samples] would redraw of [outline] if the stroke ended now, or null while it is too far to join. */
fun strokeOverOutline(
    outline: OutlineCurve,
    samples: List<CurvePoint>,
    ends: StrokeEnds,
    pixel: Float,
    options: OversketchOptions,
): OversketchPreview? = oversketchPreview(outline.toPath(), samples.map { Vec2(it.x, it.y) }, ends, pixel, options)
