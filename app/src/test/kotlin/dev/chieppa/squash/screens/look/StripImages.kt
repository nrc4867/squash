package dev.chieppa.squash.screens.look

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val LABEL_SIZE_PIXELS = 16
private const val LABEL_INSET_PIXELS = 10
private val LABEL_COLOR = Color(0x80, 0x78, 0x70)

/** Keeps [pixels], [width] wide, in [folder] as the image [name], each of [labels] written at the top left of its [tile]-wide panel. */
internal fun keepLabelledStrip(folder: File, pixels: IntArray, width: Int, labels: List<String>, tile: Int, name: String) {
    val out = BufferedImage(width, pixels.size / width, BufferedImage.TYPE_INT_ARGB)
    out.setRGB(0, 0, width, out.height, pixels, 0, width)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, LABEL_SIZE_PIXELS)
    g.color = LABEL_COLOR
    labels.forEachIndexed { i, label -> g.drawString(label, i * tile + LABEL_INSET_PIXELS, LABEL_INSET_PIXELS + LABEL_SIZE_PIXELS) }
    g.dispose()
    folder.mkdirs()
    ImageIO.write(out, "png", File(folder, "$name.png"))
}
