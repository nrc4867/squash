package dev.chieppa.squash.screens.look

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** How much each shape's views are scaled down on the contact sheet. */
private const val SHEET_SCALE = 0.5f

/** The label's size and inset on each row of the sheet, in pixels. */
private const val LABEL_SIZE = 28
private const val LABEL_INSET = 12

/**
 * The contact sheet of a face saying every shape: each of [images] (a shape's word to its kept face close-up in [folder]) as a row, scaled
 * down, labelled with its word, top to bottom; kept as [name] in [folder]. Rows whose image is missing are left out.
 */
fun keepShapeSheet(folder: File, images: List<Pair<String, String>>, name: String) {
    val rows = images.mapNotNull { (word, image) -> File(folder, "$image.png").takeIf { it.exists() }?.let { word to ImageIO.read(it) } }
    if (rows.isEmpty()) return
    val width = (rows.maxOf { it.second.width } * SHEET_SCALE).toInt()
    val heights = rows.map { (it.second.height * SHEET_SCALE).toInt() }
    val sheet = BufferedImage(width, heights.sum(), BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, LABEL_SIZE)
    var top = 0
    rows.forEachIndexed { i, (word, image) ->
        g.drawImage(image, 0, top, (image.width * SHEET_SCALE).toInt(), heights[i], null)
        g.color = Color.BLACK
        g.drawString(word, LABEL_INSET, top + LABEL_INSET + LABEL_SIZE)
        top += heights[i]
    }
    g.dispose()
    ImageIO.write(sheet, "png", File(folder, "$name.png"))
}
