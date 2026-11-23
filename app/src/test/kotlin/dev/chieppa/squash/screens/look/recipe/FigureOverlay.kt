package dev.chieppa.squash.screens.look.recipe

import androidx.compose.ui.graphics.toArgb
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.recipe.build.FigureReport
import dev.chieppa.squash.recipe.build.figureStroke
import dev.chieppa.squash.recipe.figure.FigureView
import dev.chieppa.squash.recipe.figure.MISS_TOLERANCE
import dev.chieppa.squash.recipe.figure.P2
import dev.chieppa.squash.recipe.figure.STANDING_VIEWS
import dev.chieppa.squash.recipe.figure.StrokeSample
import dev.chieppa.squash.recipe.figure.polyline
import dev.chieppa.squash.screens.look.LookArgs
import dev.chieppa.squash.screens.look.compare.Silhouette
import dev.chieppa.squash.screens.look.renderSession
import dev.chieppa.squash.screens.look.shots.REFERENCE_GROUND
import dev.chieppa.squash.screens.look.shots.groundHeight
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.shownState
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt

/** How far off the camera stands, in body units: far enough that the shot is all but flat. */
private const val DISTANCE = 30f

/** How far off straight down the shot from above leans back, as a share of its distance, so the camera keeps an up direction. */
private const val FROM_ABOVE_TILT = 0.05f

/** The widest shot, in pixels, for a body long from the side. */
private const val MOST_WIDTH = 2400

/** The shot's height in pixels, and how much taller than the figure its view is. */
private const val HEIGHT = 1000
private const val SPAN_SHARE = 1.1f

/** How far the render's outline reaches past the surface, in pixels: half its silhouette weight. */
private val OUTLINE_REACH = PROBE_LOOK.lines.silhouettePixels / 2f

/** How far along a sample's normal the shot's edge is looked for, in pixels. */
private const val EDGE_SEARCH = 40

private val OUTLINE_COLOR = Color(0, 170, 60)
private val MISS_COLOR = Color(220, 40, 40)
private const val OUTLINE_WIDTH = 2f
private const val MISS_WIDTH = 6f
private const val DASH = 6f

/**
 * A recipe's figure laid over its build: the character in its display pose from a far, narrow camera straight in front and from its left,
 * so the shot is nearly flat at a known scale, the figure outline drawn over it, and each stretch where the shot's edge lies further off the
 * outline than [MISS_TOLERANCE] of the figure's height marked on it in place. The character is shown in the pose the figure is drawn in. Writes `figure-front.png` and `figure-side.png` into [folder], and `figure-top.png` for a figure drawn from above too.
 */
class FigureOverlay(private val options: RecipeArgs, private val errors: MutableList<String>) {
    fun keep(open: OpenSession, report: FigureReport, folder: File) {
        folder.mkdirs()
        val figure = requireNotNull(report.recipe.figure)
        val strokes = (figure.front.map { FigureView.FRONT to it } + figure.side.map { FigureView.SIDE to it } + figure.top.map { FigureView.TOP to it })
        val top = STANDING_VIEWS.map { report.fit.split.getValue(it) }.flatMap { split -> split.taken.map { it.sample.at.y } + split.unread.map { it.at.y } }.maxOrNull() ?: 1.0
        val span = (top * SPAN_SHARE).toFloat()
        val middle = span / 2f / SPAN_SHARE * 1.05f
        val display = open.session.document.value.character.displayPose
        figure.pose?.let { open.session.send(SetDisplayPose(Pose(it))) }
        val ground = open.session.shownState().character.groundHeight()
        val tolerance = MISS_TOLERANCE * top
        // The outline's heights are above the ground under the root; the shot's are above the lowest point of the build.
        val lift = (-figure.rootHeight - ground).toDouble()
        for (view in FigureView.entries) {
            if (view == FigureView.TOP && figure.top.isEmpty()) continue
            val drawn = strokes.filter { it.first == view }.flatMap { (_, stroke) -> figureStroke(stroke).polyline() }
            val reach = if (view == FigureView.TOP) (drawn.maxOf { abs(it.y) } * 2 * SPAN_SHARE).toFloat() else span
            val ppu = HEIGHT / reach
            val across = drawn.maxOfOrNull { abs(it.x) }?.let { (2 * it * SPAN_SHARE * ppu).toInt() } ?: 0
            val width = maxOf(if (view == FigureView.FRONT) HEIGHT * 7 / 10 else HEIGHT / 2, minOf(MOST_WIDTH, across))
            val eye = when (view) {
                FigureView.FRONT -> "0,$middle,$DISTANCE"
                FigureView.SIDE -> "$DISTANCE,$middle,0"
                FigureView.TOP -> "${-DISTANCE * FROM_ABOVE_TILT},${DISTANCE - figure.rootHeight},0"
            }
            val target = if (view == FigureView.TOP) "0,${-figure.rootHeight},0" else "0,$middle,0"
            val fov = Math.toDegrees(2.0 * atan((reach / 2f / DISTANCE).toDouble())).toFloat()
            val name = "figure-${view.name.lowercase()}"
            val shot = render(open, listOf("eye=$eye", "target=$target", "fov=$fov", "size=${width}x$HEIGHT", "poses=none"), folder, name) ?: run {
                errors += "no figure shot from the ${view.name.lowercase()}"
                return@run null
            } ?: continue
            val image = ImageIO.read(shot)
            val silhouette = Silhouette.keyed(image, REFERENCE_GROUND.toArgb())
            val facing = if (view == FigureView.SIDE) -1.0 else 1.0
            fun pixel(p: P2): P2 =
                if (view == FigureView.TOP) P2(width / 2.0 - p.x * ppu, HEIGHT / 2.0 - p.y * ppu) else P2(width / 2.0 + facing * p.x * ppu, HEIGHT / 2.0 - (p.y + lift - middle) * ppu)
            val samples = report.fit.split.getValue(view).let { split -> split.taken.map { it.sample } + split.unread }.sortedWith(compareBy({ it.stroke }, { it.along }))
            val sideways = if (view == FigureView.TOP) -1.0 else facing
            val misses = samples.filter { s -> missAt(silhouette, pixel(s.at), P2(sideways * s.normal.x, -s.normal.y))?.let { abs(it) / ppu > tolerance } == true }
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = MISS_COLOR
            g.stroke = BasicStroke(MISS_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            drawRuns(misses, samples) { a, b -> pixel(a) to pixel(b) }.forEach { (a, b) -> g.drawLine(a.x.roundToInt(), a.y.roundToInt(), b.x.roundToInt(), b.y.roundToInt()) }
            g.color = OUTLINE_COLOR
            g.stroke = BasicStroke(OUTLINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(DASH, DASH), 0f)
            for ((strokeView, stroke) in strokes) {
                if (strokeView != view) continue
                val line = figureStroke(stroke).polyline().map(::pixel)
                line.zipWithNext().forEach { (a, b) -> g.drawLine(a.x.roundToInt(), a.y.roundToInt(), b.x.roundToInt(), b.y.roundToInt()) }
            }
            g.dispose()
            val out = File(folder, "$name.png")
            ImageIO.write(image, "png", out)
            shot.delete()
            println("  ${out.absolutePath}: ${misses.size} of ${samples.size} samples off by more than $MISS_TOLERANCE")
        }
    }

    /** The character through the camera [shot] names, kept in [folder] as [name]; the image, or null when none was written. */
    private fun render(open: OpenSession, shot: List<String>, folder: File, name: String): File? {
        val look = LookArgs((options.lookArgs(open.session.document.value.character.wardrobe.shown?.value).toList() + shot).toTypedArray())
        val scratch = File(folder, name).apply { mkdirs() }
        renderSession(open.session, open.pose, open.work, emptyMap(), look, errors, scratch)
        val made = scratch.listFiles { f -> f.name.endsWith("-camera.png") }?.firstOrNull() ?: return null
        return File(folder, "$name-shot.png").also { made.renameTo(it); scratch.deleteRecursively() }
    }

    /** How far, in pixels, the shot's edge lies out past [at] along [normal], an image direction; null where none is found near. */
    private fun missAt(silhouette: Silhouette, at: P2, normal: P2): Double? {
        val n = normal.unit()
        fun covered(s: Int): Boolean? {
            val x = (at.x + n.x * s).roundToInt()
            val y = (at.y + n.y * s).roundToInt()
            return if (x in 0 until silhouette.width && y in 0 until silhouette.height) silhouette[x, y] else null
        }
        var best: Int? = null
        for (s in -EDGE_SEARCH..EDGE_SEARCH) {
            val here = covered(s) ?: continue
            val next = covered(s + 1) ?: continue
            if (here && !next && (best == null || abs(s) < abs(best))) best = s
        }
        return best?.let { it + 0.5 - OUTLINE_REACH }
    }

    /** Neighbouring [misses] along each stroke, as the segments joining them in [samples]' order. */
    private fun drawRuns(misses: List<StrokeSample>, samples: List<StrokeSample>, toPixels: (P2, P2) -> Pair<P2, P2>): List<Pair<P2, P2>> {
        val missed = misses.toSet()
        return samples.zipWithNext().filter { (a, b) -> a in missed && b in missed && a.stroke == b.stroke }.map { (a, b) -> toPixels(a.at, b.at) }
    }
}
