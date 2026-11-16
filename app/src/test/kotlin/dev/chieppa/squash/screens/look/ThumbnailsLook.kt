package dev.chieppa.squash.screens.look

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.Workers
import dev.chieppa.squash.screens.dress.GpuWardrobeArtist
import dev.chieppa.squash.screens.dress.WardrobeLook
import dev.chieppa.squash.screens.dress.WardrobeLooks
import dev.chieppa.squash.document.SetBranchEase
import dev.chieppa.squash.screens.dress.WardrobeShot
import dev.chieppa.squash.screens.dress.WardrobeThumbnails
import dev.chieppa.squash.screens.dress.wardrobeScene
import dev.chieppa.squash.screens.dress.wardrobeShots
import dev.chieppa.squash.screens.smoke.CloseUps
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.measureTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The art of a garment tile and of an outfit tile in a drawer about 300 dp wide at 150 %, in the app's dark workshop theme. */
private const val DENSITY = 1.5f
private val BACKGROUND = Color(0xFF1C1E22)
private val GROUND = Color(0xFF3F454E)
private val THUMB_LOOKS = WardrobeLooks(WardrobeLook(129, 105, BACKGROUND, GROUND, DENSITY), WardrobeLook(129, 69, BACKGROUND, GROUND, DENSITY))

/** The gap between tiles on a strip, and the strip's own background. */
private const val GAP = 6
private val STRIP = java.awt.Color(0x2A2D33)

/**
 * Renders [example]'s outfit and garment thumbnails as the app does, its session on the default pool and the thumbnails on the app's
 * own low-priority pool, timing them all and then the round after an edit to one garment, into a `thumbs` folder: each thumbnail, and a
 * strip of the outfits and of each slot's garments.
 */
internal fun renderThumbnails(example: String, look: LookArgs, errors: MutableList<String>, folder: File) {
    val out = File(folder, "thumbs").apply { mkdirs() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
        runBlocking {
            val session = CharacterSession(exampleDocument(example, look.fromFile, look.examplesFolder), CloseUps.shippedLibrary(), scope, Dispatchers.Default, CpuFieldReads)
            SessionPose(session)
            val built = measureTime { combine(session.graph.settled, session.viewGraph.settled) { a, b -> a && b }.first { it } }
            println("thumbs: $example open session settled in ${built.inWholeMilliseconds} ms")
            val document = session.document.value
            val shots = wardrobeShots(document.character.wardrobe).filter { wardrobeScene(document, it) != null }
            val thumbnails = WardrobeThumbnails(GpuWardrobeArtist(), Workers().thumbnails)
            val following = scope.launch { thumbnails.follow(session, THUMB_LOOKS) }
            val took = measureTime { thumbnails.thumbs.first { it.size == shots.size } }
            println("thumbs: $example ${shots.size} thumbnails in ${took.inWholeMilliseconds} ms")
            val edited = document.character.wardrobe.garments.firstOrNull { it.slot != null && it.branches.isNotEmpty() }
            if (edited != null) {
                val branch = edited.branches.first()
                val shot = WardrobeShot.OfGarment(edited.id)
                val again = measureTime {
                    session.send(SetBranchEase(edited.id, branch.part, branch.ease + 0.01f))
                    val scene = wardrobeScene(session.document.value, shot)
                    thumbnails.thumbs.first { it[shot]?.scene == scene }
                    thumbnails.rendering.first { !it }
                }
                println("thumbs: $example after an edit to ${edited.id.value} in ${again.inWholeMilliseconds} ms")
            }
            following.cancel()
            val images = thumbnails.thumbs.value.mapValues { it.value.image.toAwtImage() }
            images.forEach { (shot, image) -> ImageIO.write(image, "png", File(out, "${nameOf(shot)}.png")) }
            val wardrobe = document.character.wardrobe
            strip(wardrobe.outfits.mapNotNull { images[WardrobeShot.OfOutfit(it.id)] })?.let { ImageIO.write(it, "png", File(out, "outfits.png")) }
            for (slot in wardrobe.slots) {
                strip(wardrobe.garments.filter { it.slot == slot.id }.mapNotNull { images[WardrobeShot.OfGarment(it.id)] })
                    ?.let { ImageIO.write(it, "png", File(out, "slot-${slot.id.value}.png")) }
            }
        }
    } catch (failed: Exception) {
        errors += "$example thumbnails: $failed"
    } finally {
        scope.cancel()
    }
}

private fun nameOf(shot: WardrobeShot) = when (shot) {
    is WardrobeShot.OfOutfit -> "outfit-${shot.outfit.value}"
    is WardrobeShot.OfGarment -> "garment-${shot.garment.value}"
}

/** [images] side by side on the strip's background, or null for none. */
private fun strip(images: List<BufferedImage>): BufferedImage? {
    if (images.isEmpty()) return null
    val width = images.sumOf { it.width } + GAP * (images.size + 1)
    val height = images.maxOf { it.height } + GAP * 2
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
        val g = createGraphics()
        g.color = STRIP
        g.fillRect(0, 0, width, height)
        var x = GAP
        for (image in images) {
            g.drawImage(image, x, GAP, null)
            x += image.width + GAP
        }
        g.dispose()
    }
}
