package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.hair.fit.fitPictures
import dev.chieppa.squash.character.hair.fit.fitResiduals
import dev.chieppa.squash.document.Character
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Prints, and writes to [file], how far each head's drawn hair outline lies from its hair's silhouette as [assembled] builds it, view by
 * view, mean and worst, in the head's drawn units, and beside it a picture a view of the outline over the silhouette
 * (`hair-fit-<head>-<view>.png`); nothing for a character with no hair outline.
 */
internal fun hairFitReport(character: Character, assembled: AssembledCharacter?, file: File) {
    if (character.hairOutlines.isEmpty() || assembled == null) return
    val lines = character.hairOutlines.flatMap { outline ->
        fitResiduals(character, assembled, outline.on).map { r ->
            String.format(Locale.ROOT, "%s %s: mean %.4f, worst %.4f head units", outline.on.value, r.view.name.lowercase(), r.mean, r.worst)
        }
    }
    file.writeText(lines.joinToString("\n", postfix = "\n"))
    println("hair fit: ${file.absolutePath}")
    lines.forEach { println("  $it") }
    for (outline in character.hairOutlines) {
        for (picture in fitPictures(character, assembled, outline.on)) {
            val image = BufferedImage(picture.width, picture.height, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, picture.width, picture.height, picture.pixels, 0, picture.width)
            val png = File(file.parentFile, "hair-fit-${outline.on.value}-${picture.view.name.lowercase()}.png")
            ImageIO.write(image, "png", png)
            println("  ${png.absolutePath}")
        }
    }
}
