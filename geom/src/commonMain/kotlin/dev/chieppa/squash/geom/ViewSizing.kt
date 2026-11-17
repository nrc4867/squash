package dev.chieppa.squash.geom

import kotlin.math.max

private const val AXIS_KNOTS = 65
private const val MIN_EXTENT_OF_LENGTH = 0.002f
private const val MIN_SECTION_SPAN = 1e-4f

/**
 * What drawn views set on a part's slices. u runs linearly over the outline's range along the rest axis, and a slice lies
 * in its plane a = constant, so its ends are the height view's crossings at that a and two rings can never meet. A slice
 * reads only those crossings, so changing a span of an outline moves only the slices over it. Moving the outline's far
 * end rescales u for every slice. The axis is a local spline through the midline at fixed knots, in the plane of
 * [PartAxes.along] and [PartAxes.up]; its tangent sets only a slice's proxy normal frame.
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
) {
    /** The drawn length of the part. */
    val length: Float = (end - start) * scale

    /** The spline through the height view's midline, or straight along the rest axis when only width is drawn. */
    val axis: AxisSpline = AxisSpline(
        List(AXIS_KNOTS) { k ->
            val a = stationOf(k.toFloat() / (AXIS_KNOTS - 1))
            axes.point(a * scale, (height?.midAt(a) ?: 0f) * scale, 0f)
        },
        FloatArray(AXIS_KNOTS) { it.toFloat() / (AXIS_KNOTS - 1) },
    )

    private fun stationOf(u: Float): Float = start + (end - start) * u

    /** The u whose plane lies [a] along the rest axis from the socket plane, in body units: below 0 or past 1 off the drawing. */
    fun uAt(a: Float): Float = (a / scale - start) / (end - start)

    private fun floored(extent: Float): Float = max(extent, length * MIN_EXTENT_OF_LENGTH)

    /** How far along the rest axis from the socket plane, in body units, the plane of [u] lies: the inverse of [uAt]. */
    fun alongAt(u: Float): Float = stationOf(u) * scale

    /** Whether the height view is drawn, so [upAt] reads a drawing. */
    val hasHeight: Boolean get() = height != null

    /** How far toward the part's up, in body units, the height view's midline lies at [u]; 0 where that view is not drawn. */
    fun upAt(u: Float): Float = (height?.midAt(stationOf(u)) ?: 0f) * scale

    /** The full height the height view gives at [u], or null if that view is not drawn. */
    fun heightAt(u: Float): Float? = height?.let { floored(it.extentAt(stationOf(u)) * scale) }

    /** The full width the width view gives at [u], or null if that view is not drawn. */
    fun widthAt(u: Float): Float? = width?.let { floored(it.extentAt(stationOf(u)) * scale) }

    /** The point on the midline in the plane of [u]: halfway between the height view's crossings at that a. */
    fun centreAt(u: Float): Vec3 {
        val a = stationOf(u)
        return axes.point(a * scale, (height?.midAt(a) ?: 0f) * scale, 0f)
    }

    /**
     * The slice at [u] with [section], multiplied by [closure]. The section's own spans map to the drawn extents, so the
     * silhouette is the drawing's; a view not drawn takes the other's scale, keeping the section's proportions. A section
     * off centre in y moves the frame so its ends still land on the crossings.
     */
    fun slice(u: Float, drawnSection: ClosedSpline, closure: Float): Slice {
        val section = keelAt(u, drawnSection)
        val spans = section.spans()
        val tall = heightAt(u)?.let { 2f * it / max(spans.y, MIN_SECTION_SPAN) }
        val wide = widthAt(u)?.let { 2f * it / max(spans.x, MIN_SECTION_SPAN) }
        val fullHeight = requireNotNull(tall ?: wide) { "Sizing with no drawn view" }
        val lift = if (tall == null) 0f else section.middleY() * fullHeight * closure / 2f
        val frame = Frame(centreAt(u) - axes.up * lift, axes.along, axes.up)
        return Slice(u, frame, fullHeight * closure, (wide ?: fullHeight) * closure, section, proxyNormalFrame(u, frame.center))
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
         */
        fun of(
            axes: PartAxes,
            height: OutlineProfile?,
            width: OutlineProfile?,
            openStart: Boolean,
            openEnd: Boolean,
            scale: Float,
            profile: ((OutlineProfile) -> ProfileKeel?)? = null,
        ): ViewSizing? {
            val ruler = height ?: width ?: return null
            val start = if (openStart) 0f else ruler.start
            val end = if (openEnd) 0f else ruler.end
            if (end <= start) return null
            return ViewSizing(axes, height, width, start, end, scale, openStart, openEnd, height?.let { profile?.invoke(it) })
        }
    }
}
