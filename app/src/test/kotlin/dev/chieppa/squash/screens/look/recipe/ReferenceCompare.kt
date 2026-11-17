package dev.chieppa.squash.screens.look.recipe

import androidx.compose.ui.graphics.toArgb
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.model.RecipeColor
import dev.chieppa.squash.screens.look.LookArgs
import dev.chieppa.squash.screens.look.compare.Silhouette
import dev.chieppa.squash.screens.look.compare.intersectionOverUnion
import dev.chieppa.squash.screens.look.compare.overlay
import dev.chieppa.squash.screens.look.compare.sideBySide
import dev.chieppa.squash.screens.look.renderDocument
import dev.chieppa.squash.screens.look.shots.REFERENCE_GROUND
import java.io.File
import javax.imageio.ImageIO

/** The name the character's render through the reference's camera is kept under. */
private const val SHOT = "camera"

/** The byte that makes a colour opaque in ARGB. */
private const val OPAQUE = 0xFF shl 24

/**
 * Sets a character beside a reference image: renders it through the reference's camera at the reference's size, then writes the two side
 * by side, the silhouettes overlaid (blue the reference alone, red the character alone, grey both) and their IoU.
 */
class ReferenceCompare(private val options: RecipeArgs, private val errors: MutableList<String>) {
    fun compare(document: SquashDocument, reference: File, folder: File) {
        if (!reference.isFile) throw RecipeException("$", "no reference image at ${reference.absolutePath}")
        val eye = options.eye ?: throw RecipeException("$", "a reference needs its camera: eye=x,y,z target=x,y,z (metres, +Y up, the character facing +Z) and fov=<degrees>")
        val target = options.target ?: throw RecipeException("$", "a reference needs target=x,y,z, the point its camera looks at")
        val image = ImageIO.read(reference) ?: throw RecipeException("$", "${reference.name} is not an image")
        val camera = listOfNotNull("eye=$eye", "target=$target", options.fov?.let { "fov=$it" }, "size=${image.width}x${image.height}", "poses=none")
        val look = LookArgs((options.lookArgs(document.character.wardrobe.shown?.value).toList() + camera).toTypedArray())
        folder.mkdirs()
        renderDocument(document, emptyMap(), look, errors, folder)
        val shot = folder.listFiles { f -> f.name.endsWith("-$SHOT.png") }?.firstOrNull() ?: return run { errors += "no render through the reference's camera" }
        val own = ImageIO.read(shot)
        ImageIO.write(sideBySide(image, own), "png", File(folder, "pair.png"))
        val ground = options.ground?.let { RecipeColor.parse(it).let { c -> OPAQUE or (byte(c.red) shl 16) or (byte(c.green) shl 8) or byte(c.blue) } } ?: image.getRGB(0, 0)
        val a = Silhouette.keyed(image, ground)
        val b = Silhouette.keyed(own, REFERENCE_GROUND.toArgb())
        ImageIO.write(overlay(a, b), "png", File(folder, "overlay.png"))
        val iou = intersectionOverUnion(a, b)
        File(folder, "iou.txt").writeText("silhouette IoU ${iou?.let { "%.3f".format(it) } ?: "-"}\n")
        println("compare: ${folder.absolutePath}, silhouette IoU ${iou?.let { "%.3f".format(it) } ?: "-"}")
        listOf(shot, File(folder, "pair.png"), File(folder, "overlay.png")).forEach { println("  ${it.absolutePath}") }
    }

    private fun byte(channel: Float) = Math.round(channel * 255f)
}
