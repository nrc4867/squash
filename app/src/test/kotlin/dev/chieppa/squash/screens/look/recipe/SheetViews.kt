package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.screens.look.compare.ModelTriangles
import dev.chieppa.squash.screens.look.compare.fill
import dev.chieppa.squash.screens.smoke.shownState
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** Pixels across the longest extent of the character in any view. */
private const val LONGEST_PIXELS = 720

/** Blank pixels round the character in each view. */
private const val MARGIN = 24

private const val INK = 0x000000
private const val PAPER = 0xFFFFFF

/** One view of the flat sheet: its name, and how a body point (x forward, y up, z right) lands across and down it. */
private enum class FlatView(val across: (Float, Float, Float) -> Float, val down: (Float, Float, Float) -> Float) {
    /** From in front: the character's left on the image's right. */
    FRONT({ _, _, z -> -z }, { _, y, _ -> -y }),

    /** From its left: forward on the image's right. */
    SIDE({ x, _, _ -> x }, { _, y, _ -> -y }),

    /** From above, forward up the image: the character's left on the image's left. */
    TOP({ _, _, z -> z }, { x, _, _ -> -x }),
}

/**
 * A built character's flat sheet: its silhouette at rest drawn black on white straight from the front, from its left and from above,
 * every view at one scale, as `front.png`, `side.png` and `top.png` in [folder], with `views.json` saying the scale and where the ground
 * and the body's middle fall in each. A sheet whose answer is the character itself.
 */
class SheetViews(private val folder: File) {
    fun keep(open: OpenSession) {
        folder.mkdirs()
        val shown = open.session.document.value.character.displayPose
        open.session.send(SetDisplayPose(Pose()))
        open.work.settle(open.session)
        val model = ModelTriangles.of(open.session.shownState())
        open.session.send(SetDisplayPose(shown))
        open.work.settle(open.session)
        val p = model.positions
        val count = model.pointCount
        fun extent(axis: Int) = (0 until count).minOf { p[it * 3 + axis] } to (0 until count).maxOf { p[it * 3 + axis] }
        val (x0, x1) = extent(0)
        val (y0, y1) = extent(1)
        val (z0, z1) = extent(2)
        val longest = maxOf(x1 - x0, y1 - y0, z1 - z0)
        val scale = LONGEST_PIXELS / longest
        val meta = StringBuilder()
        meta.append("{\n  \"format\": \"squash-sheet-views/1\",\n  \"pixelsPerUnit\": ${"%.4f".format(scale)},\n")
        meta.append("  \"height\": ${"%.4f".format(y1 - y0)},\n  \"views\": {\n")
        FlatView.entries.forEachIndexed { i, view ->
            val across = FloatArray(count) { view.across(p[it * 3], p[it * 3 + 1], p[it * 3 + 2]) }
            val down = FloatArray(count) { view.down(p[it * 3], p[it * 3 + 1], p[it * 3 + 2]) }
            val left = across.min()
            val top = down.min()
            val width = ((across.max() - left) * scale).roundToInt() + 2 * MARGIN
            val height = ((down.max() - top) * scale).roundToInt() + 2 * MARGIN
            val xs = FloatArray(count) { (across[it] - left) * scale + MARGIN }
            val ys = FloatArray(count) { (down[it] - top) * scale + MARGIN }
            val covered = BooleanArray(width * height)
            val idx = model.indices
            for (t in 0 until idx.size / 3) {
                val a = idx[t * 3]
                val b = idx[t * 3 + 1]
                val c = idx[t * 3 + 2]
                fill(xs[a], ys[a], xs[b], ys[b], xs[c], ys[c], covered, width, height)
            }
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, if (covered[y * width + x]) INK else PAPER)
            val name = view.name.lowercase()
            val file = File(folder, "$name.png")
            ImageIO.write(image, "png", file)
            println("  ${file.absolutePath}")
            // The middle (x 0 in the front and top views) and the ground (y at the lowest point) in the image's pixels.
            val middle = (0f - left) * scale + MARGIN
            val ground = (-y0 - top) * scale + MARGIN
            val origin = (0f - top) * scale + MARGIN
            val comma = if (i < FlatView.entries.lastIndex) "," else ""
            meta.append("    \"$name\": { \"file\": \"$name.png\", \"middle\": ${"%.1f".format(middle)}, \"ground\": ${"%.1f".format(ground)}, \"origin\": ${"%.1f".format(origin)} }$comma\n")
        }
        meta.append("  }\n}\n")
        File(folder, "views.json").writeText(meta.toString())
        println("sheet views: ${folder.absolutePath}")
    }
}
