package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chieppa.squash.character.mouth.FlatMouth
import dev.chieppa.squash.geom.Vec2

private const val INK_STROKE = 1.5f

/** How wide a tile shows, in the mouth's widths at rest, and how far down its middle sits, a share of the tile's height. */
private const val SPAN_WIDTHS = 1.4f
private const val MIDDLE_DOWN = 0.4f

/** The colors a mouth tile draws in: its ink and its roles' fills, null where a role has none. */
internal class MouthTileColors(val ink: Color, val inner: Color?, val tongue: Color?, val teeth: Color?, val lips: Color?)

/**
 * [mouth] as a small flat drawing, framed on [rest] so every tile of one mouth shares a frame: the opening, tongue and teeth in their fills,
 * then the lips, a pressed crease and the inked pieces in [colors]' ink.
 */
@Composable
internal fun MouthShapeThumbnail(mouth: FlatMouth?, rest: FlatMouth?, colors: MouthTileColors, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val shown = mouth ?: return@Canvas
        val frame = (rest ?: shown).upper.flatten().ifEmpty { return@Canvas }
        val left = frame.minOf { it.x }
        val right = frame.maxOf { it.x }
        val middle = Vec2((left + right) / 2f, frame.map { it.y }.average().toFloat())
        val scale = size.width / ((right - left).coerceAtLeast(1e-4f) * SPAN_WIDTHS)
        val at = { p: Vec2 -> Offset(size.width / 2f + (p.x - middle.x) * scale, size.height * MIDDLE_DOWN + (p.y - middle.y) * scale) }
        fun fill(loop: List<Vec2>, color: Color?) {
            if (color == null || loop.size < 3) return
            drawPath(Path().apply { loop.map(at).forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }, color)
        }
        shown.lips.forEach { fill(it, colors.lips) }
        shown.opening?.let { fill(it, colors.inner) }
        shown.tongue.filterNot { it.over }.forEach { fill(it.loop, colors.tongue) }
        shown.teeth.filterNot { it.over }.forEach { fill(it.loop, colors.teeth) }
        shown.tongue.filter { it.over }.forEach { fill(it.loop, colors.tongue) }
        shown.teeth.filter { it.over }.forEach { fill(it.loop, colors.teeth) }
        (shown.upper + shown.lower + listOfNotNull(shown.crease)).forEach { inked(it.map(at), colors.ink) }
    }
}

private fun DrawScope.inked(line: List<Offset>, ink: Color) = line.zipWithNext().forEach { (a, b) -> drawLine(ink, a, b, INK_STROKE) }
