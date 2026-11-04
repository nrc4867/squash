package dev.chieppa.squash.uidesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.stretch.theme.Stretch

/** A view thumbnail's picture, in dp. */
val THUMB_WIDTH = 128.dp
val THUMB_HEIGHT = 88.dp

private val LINE_WIDTH = 2.dp
private const val PREVIEW_SHADE = 0.24f
private val EDGE_WIDTH = 2.dp
private val EDGE_DASH = 5.dp
private val HINT_DASH = 5.dp
private val HINT_GAP = 4.dp
private val LABEL_GAP = 4.dp

/** How a view thumbnail's edge reads: nothing, the view on screen, or a view with nothing drawn yet. */
enum class ThumbEdge { PLAIN, CURRENT, MISSING }

/** A small picture of one view under its [label]: [lines] solid, the [hint] dashed and each [preview] shaded inside a dashed edge, all in the picture's pixels. */
@Composable
fun ViewThumb(label: String, edge: ThumbEdge, lines: List<List<Offset>>, hint: List<List<Offset>>, modifier: Modifier = Modifier, preview: List<List<Offset>> = emptyList()) {
    val c = Stretch.colors
    val shape = Stretch.shapes.ctlShape
    val radius = Stretch.shapes.ctl
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
        BasicText(label.uppercase(), style = Stretch.type.eyebrow.merge(TextStyle(color = c.inkMuted)))
        Canvas(Modifier.size(THUMB_WIDTH, THUMB_HEIGHT).clip(shape).background(c.surfaceControl)) {
            hint.forEach { path(it, c.inkSubtle, dashed = true) }
            preview.forEach {
                shaded(it, c.affected.copy(alpha = PREVIEW_SHADE))
                path(it, c.inkStrong, dashed = true)
            }
            lines.forEach { path(it, c.inkStrong, dashed = false) }
            val edgeWidth = EDGE_WIDTH.toPx()
            val inset = Offset(edgeWidth / 2f, edgeWidth / 2f)
            val box = Size(size.width - edgeWidth, size.height - edgeWidth)
            val corner = CornerRadius(radius.toPx())
            when (edge) {
                ThumbEdge.PLAIN -> Unit
                ThumbEdge.CURRENT -> drawRoundRect(c.accent, inset, box, corner, style = Stroke(edgeWidth))
                ThumbEdge.MISSING -> {
                    val dash = EDGE_DASH.toPx()
                    drawRoundRect(c.warnEdge, inset, box, corner, style = Stroke(edgeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))))
                }
            }
        }
    }
}

private fun DrawScope.shaded(points: List<Offset>, color: Color) {
    if (points.size < 3) return
    val inside = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(inside, color)
}

private fun DrawScope.path(points: List<Offset>, color: Color, dashed: Boolean) {
    if (points.size < 2) return
    val line = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(HINT_DASH.toPx(), HINT_GAP.toPx())) else null
    drawPath(line, color, style = Stroke(LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
}
