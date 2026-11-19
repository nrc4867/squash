package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.recipe.model.RecipeColor
import java.awt.image.BufferedImage

/** An image's flat background as ARGB: [ground] as `#rrggbb` when given, else its top-left pixel. */
internal fun groundOf(image: BufferedImage, ground: String?): Int = ground?.let { RecipeColor.parse(it).argb() } ?: image.getRGB(0, 0)

private fun RecipeColor.argb(): Int = (0xFF shl 24) or (byte(red) shl 16) or (byte(green) shl 8) or byte(blue)

private fun byte(channel: Float) = Math.round(channel * 255f)
