package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.look.LookArgs.Companion.FACE
import dev.chieppa.squash.screens.look.LookArgs.Companion.FOOT
import dev.chieppa.squash.screens.look.LookArgs.Companion.HAND
import dev.chieppa.squash.screens.smoke.CloseUps
import dev.chieppa.squash.screens.smoke.WomanViews
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.measureTime

/** The face close-up's sides and size: front, three-quarter and side, square tiles. */
private val FACE_AZIMUTHS = listOf(0f, 40f, 90f)
private const val FACE_TILE = 600

/**
 * Renders examples through the real graph, rig and scene on the GPU to PNGs, with no test runner: per outfit, the whole character
 * from the front, three-quarter and side at rest and in each named pose, and the face and foot close-ups; the bare hand close-up
 * once. Run as `./gradlew :app:look -Plook="example=woman-dressed outfits=all poses=all close=face,hand,foot"`.
 */
fun main(args: Array<String>) {
    val look = LookArgs(args)
    val errors = mutableListOf<String>()
    val took = measureTime { look.examples.forEach { render(it, look, errors) } }
    errors.forEach { System.err.println(it) }
    println("look: ${look.examples.joinToString()} in ${took.inWholeMilliseconds} ms into ${look.imagesFolder.absolutePath}")
    exitProcess(if (errors.isEmpty()) 0 else 1)
}

private fun render(example: String, look: LookArgs, errors: MutableList<String>) {
    val folder = File(look.imagesFolder, example)
    TestWork().use { work ->
        val session = CharacterSession(exampleDocument(example, look.fromFile, look.examplesFolder), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
        SessionPose(session)
        work.settle(session)
        val views = WomanViews(work, errors, folder)
        val closeUps = CloseUps(work, errors, folder)
        val parts = LookParts(session.document.value.character)
        val poses = examplePoses(example).filterKeys { look.poses == null || it in look.poses }
        if (HAND in look.close) parts.hand?.let { closeUps.keep(session, it, "hand") }
        for (outfit in outfitsOf(session, look)) {
            val name = outfit?.value ?: "as-saved"
            if (outfit != null) session.send(ShowOutfit(outfit))
            views.keep(session, name)
            if (FACE in look.close) parts.head?.let { views.keep(session, "$name-face", parts.faceFramed(it), FACE_AZIMUTHS, FACE_TILE, FACE_TILE) }
            if (FOOT in look.close) parts.foot?.let { closeUps.keepAround(session, it, "$name-foot") }
            for ((pose, set) in poses) {
                session.send(SetPose(set))
                views.keep(session, "$name-pose-$pose")
            }
            if (poses.isNotEmpty()) session.send(SetPose(Pose()))
        }
    }
}

/** The outfits asked for that the example has, or its shown state alone when it has none. */
private fun outfitsOf(session: CharacterSession, look: LookArgs) =
    session.document.value.character.wardrobe.outfits.map { it.id }.filter { look.outfits == null || it.value in look.outfits }.ifEmpty { listOf(null) }
