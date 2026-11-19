package dev.chieppa.squash.screens.look.recipe

import androidx.compose.ui.graphics.toArgb
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.screens.look.LookArgs
import dev.chieppa.squash.screens.look.compare.CameraFit
import dev.chieppa.squash.screens.look.compare.CameraFitResult
import dev.chieppa.squash.screens.look.compare.ModelTriangles
import dev.chieppa.squash.screens.look.compare.Silhouette
import dev.chieppa.squash.screens.look.compare.box
import dev.chieppa.squash.screens.look.compare.describe
import dev.chieppa.squash.screens.look.compare.intersectionOverUnion
import dev.chieppa.squash.screens.look.compare.overlay
import dev.chieppa.squash.screens.look.compare.sideBySide
import dev.chieppa.squash.screens.look.renderSession
import dev.chieppa.squash.screens.look.shots.REFERENCE_GROUND
import dev.chieppa.squash.screens.look.shots.ReferenceFrame
import dev.chieppa.squash.screens.look.shots.ShotCamera
import dev.chieppa.squash.screens.look.shots.groundHeight
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.shownState
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.measureTime

/** How far the render's outline reaches past the surface, in pixels: half its silhouette weight. */
private val OUTLINE_REACH = PROBE_LOOK.lines.silhouettePixels / 2f

/** The name the character's render through the reference's camera is kept under. */
private const val SHOT = "camera"

/**
 * Sets a character beside a reference image: renders it through the reference's camera at the reference's size, then writes the two side
 * by side, the silhouettes overlaid (blue the reference alone, red the character alone, grey both) and their IoU. With `fit=true` the
 * camera is first searched for (see [CameraFit]), from the camera given when there is one, and printed.
 */
class ReferenceCompare(private val options: RecipeArgs, private val errors: MutableList<String>) {
    fun compare(open: OpenSession, reference: File, folder: File) {
        if (!reference.isFile) throw RecipeException("$", "no reference image at ${reference.absolutePath}")
        val image = ImageIO.read(reference) ?: throw RecipeException("$", "${reference.name} is not an image")
        val ground = groundOf(image, options.ground)
        val a = Silhouette.keyed(image, ground)
        val given = options.eye?.let { eye ->
            val target = options.target ?: throw RecipeException("$", "a reference needs target=x,y,z, the point its camera looks at")
            ShotCamera.parse(eye, target, options.fov, "${image.width}x${image.height}")
        }
        val camera = if (options.fit) fitted(open, a, given) else given
            ?: throw RecipeException("$", "a reference needs its camera: eye=x,y,z target=x,y,z (metres, +Y up, the character facing +Z) and fov=<degrees>, or fit=true")
        val shot = render(open, camera, folder) ?: return run { errors += "no render through the reference's camera" }
        val own = ImageIO.read(shot)
        ImageIO.write(sideBySide(image, own), "png", File(folder, "pair.png"))
        val b = Silhouette.keyed(own, REFERENCE_GROUND.toArgb())
        ImageIO.write(overlay(a, b), "png", File(folder, "overlay.png"))
        val iou = intersectionOverUnion(a, b)
        File(folder, "iou.txt").writeText("silhouette IoU ${iou?.let { "%.3f".format(it) } ?: "-"}\n${camera.describe()}\n")
        println("compare: ${folder.absolutePath}, silhouette IoU ${iou?.let { "%.3f".format(it) } ?: "-"}")
        listOf(shot, File(folder, "pair.png"), File(folder, "overlay.png")).forEach { println("  ${it.absolutePath}") }
    }

    /** The character through [camera], kept in [folder]; the image, or null when none was written. */
    private fun render(open: OpenSession, camera: ShotCamera, folder: File): File? {
        val shot = listOfNotNull("eye=${vec(camera.eye)}", "target=${vec(camera.target)}", "fov=${camera.fovDegrees}", "size=${camera.width}x${camera.height}", "poses=none")
        val look = LookArgs((options.lookArgs(open.session.document.value.character.wardrobe.shown?.value).toList() + shot).toTypedArray())
        folder.mkdirs()
        folder.listFiles { f -> f.name.endsWith("-$SHOT.png") }?.forEach { it.delete() }
        renderSession(open.session, open.pose, open.work, emptyMap(), look, errors, folder)
        return folder.listFiles { f -> f.name.endsWith("-$SHOT.png") }?.firstOrNull()
    }

    /** The camera through which the character as shown best matches [reference], searched from [given] when there is one, printed. */
    private fun fitted(open: OpenSession, reference: Silhouette, given: ShotCamera?): ShotCamera {
        if (reference.box() == null) throw RecipeException("$", "the reference shows nothing on its background; give ground=#rrggbb")
        open.session.document.value.character.wardrobe.shown?.let { open.session.send(ShowOutfit(it)) }
        open.work.settle(open.session)
        val state = open.session.shownState()
        val model = ModelTriangles.of(state)
        val ground = state.character.groundHeight()
        val frame = ReferenceFrame(ground, options.scale?.toFloat() ?: 1f)
        val fov = given?.fovDegrees ?: options.fov?.toFloat() ?: ShotCamera.REFERENCE_FOV
        lateinit var fit: CameraFitResult
        val took = measureTime { fit = CameraFit(model, frame, reference, OUTLINE_REACH).fit(given, fov) }
        println("fit: ${fit.camera.describe()} (silhouette IoU %.3f on the search's own outline, ${fit.tried} cameras in ${took.inWholeMilliseconds} ms)".format(fit.iou))
        fit.mirror?.let { (camera, iou) -> println("fit: from the far side, nearly as close: ${camera.describe()} (IoU %.3f); pass it as eye= target= fov= if the reference shows that side".format(iou)) }
        return fit.camera
    }

    private fun vec(p: Vec3) = "${p.x},${p.y},${p.z}"
}
