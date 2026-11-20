package dev.chieppa.squash.geom

import kotlin.math.max

private const val AXIS_KNOTS = 65
private const val MIN_EXTENT_OF_LENGTH = 0.002f
private const val MIN_SECTION_SPAN = 1e-4f

/**
 * What drawn views set on a part's slices. u runs linearly over the outline's range along the rest axis, and a slice lies
 * in its plane a = constant, so its ends are the height view's crossings at that a and two rings can never meet. A slice
 * reads only those crossings, so changing a span of an outline moves only the slices over it. Moving the outline's far
 * end rescales u for every slice. With [sideways], the width view's midline moves a slice toward the part's side as the height
 * view's moves it up; without, the width view sets width alone. The axis is a local spline through the midline at fixed knots; its tangent sets only a slice's proxy normal frame.
 */
class ViewSizing private constructor(
    private val axes: PartAxes,
    private val height: OutlineProfile?,
    private val width: OutlineProfile?,
    private val start: Float,
    private val end: Float,
    private val scale: Float,
    private val openStart: Boolean,
    private val openEnd: Boolean,
    private val keel: ProfileKeel?,
    private val sideways: Boolean,
) {
    /** The drawn length of the part. */
    val length: Float = (end - start) * scale

    /** The spline through the height view's midline, or straight along the rest axis when only width is drawn. */
    val axis: AxisSpline = AxisSpline(
        List(AXIS_KNOTS) { k ->
            val a = stationOf(k.toFloat() / (AXIS_KNOTS - 1))
            axes.point(a * scale, (height?.midAt(a) ?: 0f) * scale, sideOf(a))
        },
        FloatArray(AXIS_KNOTS) { it.toFloat() / (AXIS_KNOTS - 1) },
    )

    private fun stationOf(u: Float): Float = start + (end - start) * u

    /** How far toward the part's side, in body units, the width view's midline lies at station [a]: its y runs the other way. */
    private fun sideOf(a: Float): Float = if (sideways) -(width?.midAt(a) ?: 0f) * scale else 0f

    /** The u whose plane lies [a] along the rest axis from the socket plane, in body units: below 0 or past 1 off the drawing. */
    fun uAt(a: Float): Float = (a / scale - start) / (end - start)

    private fun floored(extent: Float): Float = max(extent, length * MIN_EXTENT_OF_LENGTH)

    /** How far along the rest axis from the socket plane, in body units, the plane of [u] lies: the inverse of [uAt]. */
    fun alongAt(u: Float): Float = stationOf(u) * scale

    /** Whether the height view is drawn, so [upAt] reads a drawing. */
    val hasHeight: Boolean get() = height != null

    /** How far toward the part's up, in body units, the height view's midline lies at [u]; 0 where that view is not drawn. */
    fun upAt(u: Float): Float = (height?.midAt(stationOf(u)) ?: 0f) * scale

    /** How far toward the part's side, in body units, the width view's midline lies at [u]; 0 where that view is not drawn. */
    fun sideAt(u: Float): Float = sideOf(stationOf(u))

    /** The full height the height view gives at [u], or null if that view is not drawn. */
    fun heightAt(u: Float): Float? = height?.let { floored(it.extentAt(stationOf(u)) * scale) }

    /** The full width the width view gives at [u], or null if that view is not drawn. */
    fun widthAt(u: Float): Float? = width?.let { floored(it.extentAt(stationOf(u)) * scale) }

    /** The point on the midline in the plane of [u]: halfway between the height view's crossings at that a, and between the width view's. */
    fun centreAt(u: Float): Vec3 {
        val a = stationOf(u)
        return axes.point(a * scale, (height?.midAt(a) ?: 0f) * scale, sideOf(a))
    }

    /**
     * The slice at [u] with [section], multiplied by [closure]. The section's own spans map to the drawn extents, so the
     * silhouette is the drawing's; a view not drawn takes the other's scale times [widthRatio], the run's undrawn width as a
     * fraction of its height, keeping its undrawn proportions. A section off centre in y moves the frame so its ends still land on
     * the crossings.
     */
    fun slice(u: Float, drawnSection: ClosedSpline, closure: Float, widthRatio: Float = 1f): Slice {
        val drawn = drawnAt(u, drawnSection, widthRatio)
        val frame = Frame(centreAt(u) - axes.up * (drawn.lift * closure / 2f), axes.along, axes.up)
        return Slice(u, frame, drawn.height * closure, drawn.width * closure, drawn.section, proxyNormalFrame(u, frame.center))
    }

    /**
     * The slice at [u] as [slice] sizes it, stood in [frame] in place of the plane of [u]: the drawing's extents along a curved axis,
     * so the views give the part its width along its length and the axis its path.
     */
    fun sliceIn(u: Float, drawnSection: ClosedSpline, closure: Float, frame: Frame, widthRatio: Float = 1f): Slice {
        val drawn = drawnAt(u, drawnSection, widthRatio)
        val placed = Frame(frame.center - frame.up * (drawn.lift * closure / 2f), frame.tangent, frame.up)
        return Slice(u, placed, drawn.height * closure, drawn.width * closure, drawn.section, placed.rotation)
    }

    /** A drawn section at a u, its full extents, and twice how far its middle sits off the frame's centre toward up. */
    private class Drawn(val section: ClosedSpline, val height: Float, val width: Float, val lift: Float)

    private fun drawnAt(u: Float, drawnSection: ClosedSpline, widthRatio: Float): Drawn {
        val section = keelAt(u, drawnSection)
        val spans = section.spans()
        val tall = heightAt(u)?.let { 2f * it / max(spans.y, MIN_SECTION_SPAN) }
        val wide = widthAt(u)?.let { 2f * it / max(spans.x, MIN_SECTION_SPAN) }
        val fullHeight = requireNotNull(tall ?: wide?.let { it / widthRatio }) { "Sizing with no drawn view" }
        val lift = if (tall == null) 0f else section.middleY() * fullHeight
        return Drawn(section, fullHeight, wide ?: (fullHeight * widthRatio), lift)
    }

    /** [section] with the profile's ridge at [u] standing out of its up side, when the height view is read as a profile. */
    private fun keelAt(u: Float, section: ClosedSpline): ClosedSpline {
        val keel = keel ?: return section
        val profile = height ?: return section
        val a = stationOf(u)
        return keel.shape(section, keel.depthAt(a), profile.extentAt(a))
    }

    /** Along the axis at [u], except that a ring on the socket plane faces straight out of it. */
    private fun proxyNormalFrame(u: Float, center: Vec3): Mat3 {
        val onSocket = (openStart && u <= 0f) || (openEnd && u >= 1f)
        val tangent = if (onSocket) axes.along else axis.tangentAt(u)
        return Frame(center, tangent, tangent cross axes.side).rotation
    }

    companion object {
        /**
         * Sizing from whichever of [height] and [width] are drawn, or null if neither is or the part would have no
         * length. u follows [height]'s range when drawn, else [width]'s. An open end sits on the socket plane, a = 0.
         * [scale] multiplies the drawing. [profile] reads the height view's up side as a profile, its short features standing out as a ridge.
         * [sideways] lets the width view's midline move the part's middle toward its side.
         */
        fun of(
            axes: PartAxes,
            height: OutlineProfile?,
            width: OutlineProfile?,
            openStart: Boolean,
            openEnd: Boolean,
            scale: Float,
            sideways: Boolean = false,
            profile: ((OutlineProfile) -> ProfileKeel?)? = null,
        ): ViewSizing? {
            val ruler = height ?: width ?: return null
            val start = if (openStart) 0f else ruler.start
            val end = if (openEnd) 0f else ruler.end
            if (end <= start) return null
            return ViewSizing(axes, height, width, start, end, scale, openStart, openEnd, height?.let { profile?.invoke(it) }, sideways)
        }
    }
}
