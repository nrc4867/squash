package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.look.LookArgs.Companion.FACE
import dev.chieppa.squash.screens.look.LookArgs.Companion.FOOT
import dev.chieppa.squash.screens.look.LookArgs.Companion.HAND
import dev.chieppa.squash.screens.smoke.CloseUps
import dev.chieppa.squash.screens.smoke.WomanViews
import dev.chieppa.squash.screens.look.shots.ReferenceShot
import dev.chieppa.squash.screens.look.shots.SHOTS_FOLDER
import dev.chieppa.squash.screens.look.shots.ShotViews
import dev.chieppa.squash.screens.look.shots.shotList
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.measureTime

/** The face close-up's sides and size: front, three-quarter and side, square tiles. */
private val FACE_AZIMUTHS = listOf(0f, 40f, 90f)
private const val FACE_TILE = 600

/**
 * Renders examples through the real graph, rig and scene on the GPU to PNGs, with no test runner: per outfit, the whole character
 * from the front, three-quarter and side in its display pose, at rest when that differs, and in each named pose, and the face and foot close-ups; the bare hand close-up
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
        if (look.shots != null) return@use renderShots(session, work, example, look, errors, folder)
        val views = WomanViews(work, errors, folder)
        val camera = look.camera?.let { ShotViews(work, errors, folder, look.unitsPerMetre) to it }
        val closeUps = CloseUps(work, errors, folder)
        val parts = LookParts(session.document.value.character)
        val poses = examplePoses(example).filterKeys { look.poses == null || it in look.poses }
        if (HAND in look.close) parts.hand?.let { closeUps.keep(session, it, "hand") }
        for (outfit in outfitsOf(session, look)) {
            val name = outfit?.value ?: "as-saved"
            if (outfit != null) session.send(ShowOutfit(outfit))
            if (camera != null) {
                camera.first.keep(session, camera.second, "$name-camera")
                for ((pose, set) in poses) {
                    session.send(SetPose(set))
                    camera.first.keep(session, camera.second, "$name-pose-$pose-camera")
                }
                session.send(SetPose(Pose()))
                continue
            }
            views.keep(session, name)
            val display = session.document.value.character.displayPose
            if (!display.isRest) {
                session.send(SetDisplayPose(Pose()))
                views.keep(session, "$name-rest")
                session.send(SetDisplayPose(display))
            }
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

/**
 * Renders [example] through each shot of the list [look] names, into a `shots` folder beside its views, under the shots' own names: each
 * shot's outfit, then its pose from the example's named poses, rest for none.
 */
private fun renderShots(session: CharacterSession, work: TestWork, example: String, look: LookArgs, errors: MutableList<String>, folder: File) {
    val shots = shotList(requireNotNull(look.shots))
    val views = ShotViews(work, errors, File(folder, SHOTS_FOLDER), look.unitsPerMetre)
    val poses = examplePoses(example)
    val outfits = session.document.value.character.wardrobe.outfits.associateBy { it.id.value }
    for (shot in shots) {
        outfits[shot.outfit]?.let { session.send(ShowOutfit(it.id)) }
        val pose = if (shot.examplePose == ReferenceShot.REST) Pose() else poses[shot.examplePose]
        if (pose == null) {
            errors += "$example has no pose ${shot.examplePose} for ${shot.name}"
            continue
        }
        session.send(SetPose(pose))
        views.keep(session, shot.camera, shot.name)
    }
    session.send(SetPose(Pose()))
}

/** The outfits asked for that the example has, or its shown state alone when it has none. */
private fun outfitsOf(session: CharacterSession, look: LookArgs) =
    session.document.value.character.wardrobe.outfits.map { it.id }.filter { look.outfits == null || it.value in look.outfits }.ifEmpty { listOf(null) }
