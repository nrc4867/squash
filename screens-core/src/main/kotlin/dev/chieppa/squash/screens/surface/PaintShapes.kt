package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.shape.ChartPlaces
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.SurfaceView
import kotlin.math.cos
import kotlin.math.sin

/** The shape a paint drag lays on a surface: a brush stroke, a straight brush stroke, or what lies inside an ellipse, a box or a drawn loop. */
enum class PaintShape {
    BRUSH, LINE, ELLIPSE, BOX, LASSO;

    /** Whether it lays what lies inside an outline on screen, through the whole body, rather than a brush's reach on the surface. */
    val outlined: Boolean get() = this == ELLIPSE || this == BOX || this == LASSO
}

/** How many points an ellipse's outline takes. */
private const val ELLIPSE_POINTS = 48

private const val TWO_PI = 6.2831855f

/** The outline [shape] drawn by a drag from [from] through [path] to its last point makes on screen; empty for a brush or a line. */
fun outlineOf(shape: PaintShape, from: Offset, path: List<Offset>): List<Offset> {
    val to = path.lastOrNull() ?: from
    return when (shape) {
        PaintShape.BOX -> listOf(from, Offset(to.x, from.y), to, Offset(from.x, to.y))
        PaintShape.ELLIPSE -> {
            val middle = (from + to) / 2f
            val rx = (to.x - from.x) / 2f
            val ry = (to.y - from.y) / 2f
            List(ELLIPSE_POINTS) { i ->
                val angle = TWO_PI * i / ELLIPSE_POINTS
                middle + Offset(cos(angle) * rx, sin(angle) * ry)
            }
        }
        PaintShape.LASSO -> listOf(from) + path
        PaintShape.BRUSH, PaintShape.LINE -> emptyList()
    }
}

/** Whether [at] lies inside the closed [outline], by the even-odd rule. */
fun insideOutline(outline: List<Offset>, at: Offset): Boolean {
    if (outline.size < 3) return false
    var inside = false
    var j = outline.lastIndex
    for (i in outline.indices) {
        val a = outline[i]
        val b = outline[j]
        if ((a.y > at.y) != (b.y > at.y) && at.x < (b.x - a.x) * (at.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** The places along the straight line from [from] to [to] on screen a brush of [radius] pixels stamps at, half its radius apart. */
fun stampsAlong(from: Offset, to: Offset, radius: Float): List<Offset> {
    val length = (to - from).getDistance()
    val steps = (length / (radius / 2f).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
    return List(steps + 1) { i -> from + (to - from) * (i.toFloat() / steps) }
}

/** The cells of [places] whose middle [sight] shows inside [outline], front and back alike. */
fun cellsInside(places: ChartPlaces, sight: SurfaceView, outline: List<Offset>): SurfaceMask =
    places.inside({ point: Vec3 -> sight.toPixel(point)?.let { Vec2(it.x, it.y) } }) { insideOutline(outline, Offset(it.x, it.y)) }

/**
 * The cells a paint drag of [shape] from [from] through [path] on screen lays, of [places] seen through [sight]: for a brush, those within
 * [radius] body units of each place [hitAt] finds under the drag; for a line, of each place along the straight line from [from] to the
 * drag's last point, half [pixelRadius], the brush's radius on screen, apart; for an outline, those it holds (see [cellsInside]).
 */
fun strokeOf(
    shape: PaintShape,
    places: ChartPlaces,
    sight: SurfaceView,
    from: Offset,
    path: List<Offset>,
    radius: Float,
    pixelRadius: Float,
    hitAt: (Offset) -> Vec3?,
): SurfaceMask = when (shape) {
    PaintShape.BRUSH -> places.within((listOf(from) + path).mapNotNull(hitAt), radius)
    PaintShape.LINE -> places.within(stampsAlong(from, path.lastOrNull() ?: from, pixelRadius).mapNotNull(hitAt), radius)
    PaintShape.ELLIPSE, PaintShape.BOX, PaintShape.LASSO -> cellsInside(places, sight, outlineOf(shape, from, path))
}
