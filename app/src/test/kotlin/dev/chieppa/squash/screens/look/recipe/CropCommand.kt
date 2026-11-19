package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.screens.look.compare.Silhouette
import dev.chieppa.squash.screens.look.compare.sheetViews
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** The background left round each view, as a share of its larger side. */
private const val MARGIN_SHARE = 0.06f

/**
 * Splits the character sheet [RecipeArgs.crop] names, several views on one flat background, into one image a view by the background's
 * gaps (see [sheetViews]), each with a margin of background, written to `out=` (default beside the sheet, in `<sheet>-views`) as
 * `view-<n>.png` in reading order. The background is the sheet's top-left pixel, or `ground=#rrggbb`.
 */
internal fun crop(options: RecipeArgs): Int {
    val sheet = requireNotNull(options.crop)
    if (!sheet.isFile) throw RecipeException("$", "no sheet image at ${sheet.absolutePath}")
    val image = ImageIO.read(sheet) ?: throw RecipeException("$", "${sheet.name} is not an image")
    val ground = groundOf(image, options.ground)
    val views = sheetViews(Silhouette.keyed(image, ground))
    if (views.isEmpty()) throw RecipeException("$", "${sheet.name} shows nothing on its background; give ground=#rrggbb")
    val out = options.out ?: File(sheet.absoluteFile.parentFile, sheet.nameWithoutExtension + "-views")
    out.mkdirs()
    out.listFiles { f -> f.name.startsWith("view-") && f.extension == "png" }?.forEach { it.delete() }
    println("crop: ${views.size} views from ${sheet.absolutePath}")
    views.forEachIndexed { i, box ->
        val margin = (maxOf(box.width, box.height) * MARGIN_SHARE).toInt()
        val piece = BufferedImage(box.width + 2 * margin, box.height + 2 * margin, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until piece.height) for (x in 0 until piece.width) {
            val sx = box.left - margin + x
            val sy = box.top - margin + y
            val inside = sx in box.left until box.right && sy in box.top until box.bottom
            piece.setRGB(x, y, if (inside) image.getRGB(sx, sy) else ground)
        }
        val file = File(out, "view-${i + 1}.png")
        ImageIO.write(piece, "png", file)
        println("  ${file.absolutePath} (${piece.width}x${piece.height}, from x ${box.left} y ${box.top})")
    }
    return 0
}
