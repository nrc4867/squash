package dev.chieppa.squash.screens.look.compare

import java.awt.image.BufferedImage

/** The overlay's colours: what only the reference covers, what only Squash covers, what both cover, and neither. */
private const val REFERENCE_ONLY = 0xFF2F6FD6.toInt()
private const val SQUASH_ONLY = 0xFFE0532F.toInt()
private const val SHARED = 0xFF9C9C9C.toInt()
private const val NEITHER = 0xFFFFFFFF.toInt()

/** The gap between the reference and the Squash render in a pair, in pixels, and its colour. */
private const val GAP = 8
private const val GAP_COLOR = 0xFF2A1B17.toInt()

/** [left] and [right] side by side, top-aligned, with a thin gap. */
fun sideBySide(left: BufferedImage, right: BufferedImage): BufferedImage {
    val out = BufferedImage(left.width + GAP + right.width, maxOf(left.height, right.height), BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until out.height) for (x in left.width until left.width + GAP) out.setRGB(x, y, GAP_COLOR)
    out.createGraphics().apply {
        drawImage(left, 0, 0, null)
        drawImage(right, left.width + GAP, 0, null)
        dispose()
    }
    return out
}

/** Where [reference] and [squash] cover, pixel by pixel: blue the reference alone, red Squash alone, grey both, white neither. */
fun overlay(reference: Silhouette, squash: Silhouette): BufferedImage {
    val out = BufferedImage(reference.width, reference.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until reference.height) for (x in 0 until reference.width) {
        val a = reference[x, y]
        val b = squash[x, y]
        out.setRGB(x, y, if (a && b) SHARED else if (a) REFERENCE_ONLY else if (b) SQUASH_ONLY else NEITHER)
    }
    return out
}
