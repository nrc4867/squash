package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SetTemplate
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.look.LookArgs.Companion.ANCHORS
import dev.chieppa.squash.screens.look.LookArgs.Companion.FACE
import dev.chieppa.squash.screens.look.LookArgs.Companion.FEET
import dev.chieppa.squash.screens.look.LookArgs.Companion.FOOT
import dev.chieppa.squash.screens.look.LookArgs.Companion.HAND
import dev.chieppa.squash.screens.look.LookArgs.Companion.HANDS
import dev.chieppa.squash.screens.look.LookArgs.Companion.DRAG
import dev.chieppa.squash.screens.look.LookArgs.Companion.JOINS
import dev.chieppa.squash.screens.look.LookArgs.Companion.RELEASE
import dev.chieppa.squash.screens.look.LookArgs.Companion.SURFACE
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
 * once, with `close=hands` the bare hand at rest, curled and open, and with `close=feet` the bare foot or paw at rest, curled, open and spread. Run as `./gradlew :app:look -Plook="example=woman-dressed outfits=all poses=all close=face,hand,foot"`.
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
    if (look.thumbs) return renderThumbnails(example, look, errors, folder)
    renderDocument(exampleDocument(example, look.fromFile, look.examplesFolder), examplePoses(example), look, errors, folder)
}

/**
 * Renders [document] into [folder] as [look] asks, on a session of its own (see [renderSession]).
 */
fun renderDocument(document: SquashDocument, namedPoses: Map<String, Pose>, look: LookArgs, errors: MutableList<String>, folder: File) {
    TestWork().use { work ->
        val session = CharacterSession(document, CloseUps.shippedLibrary(), work.scope, work.dispatcher, look.reads)
        renderSession(session, SessionPose(session), work, namedPoses, look, errors, folder)
    }
}

/**
 * Renders [session]'s character into [folder] as [look] asks: per outfit, the whole character in its display pose (with `around=true`
 * its back and top views too), at rest when that differs and in each of [namedPoses], and the close-ups, by word or by part id; with
 * `show=` only those parts and no close-ups; or the shots or explicit camera [look] names instead. [sessionPose] is the session's pose
 * node; its work runs on [work].
 */
fun renderSession(
    session: CharacterSession,
    sessionPose: SessionPose,
    work: TestWork,
    namedPoses: Map<String, Pose>,
    look: LookArgs,
    errors: MutableList<String>,
    folder: File,
) {
    val document = session.document.value
    look.templates.forEach { (part, ref) -> session.send(SetTemplate(PartId(part), TemplateRef(TemplateId(ref.substringBefore(':')), ref.substringAfter(':').toInt()))) }
    look.settings.forEach { (part, name, value) -> session.send(SetParameter(PartId(part), name, value)) }
    work.settle(session)
    look.outlineDrags.forEach { it.apply(session, work) }
    if (look.paintTest) paintTestOf(session, document.character.id.value)
    work.settle(session)
    if (look.shots != null) return renderShots(session, work, namedPoses, look, errors, folder)
    val views = WomanViews(work, errors, folder)
    val camera = look.camera?.let { ShotViews(work, errors, folder, look.unitsPerMetre) to it }
    val closeUps = CloseUps(work, errors, folder)
    val parts = LookParts(session.document.value.character)
    val poses = namedPoses.filterKeys { look.poses == null || it in look.poses }
    val only = look.show?.map(::PartId)?.toSet()?.also { requireParts(session, it, "show") }
    val close = if (only == null) look.close else emptySet()
    val closeParts = if (only == null) look.closeParts.map(::PartId).also { requireParts(session, it, "close") } else emptyList()
    if (HAND in close) parts.hand?.let { closeUps.keep(session, it, "hand") }
    if (HANDS in close) parts.hand?.let { DigitCloseUps(closeUps, work, HAND).keep(session, sessionPose, it) }
    if (FEET in close) parts.foot?.let { DigitCloseUps(closeUps, work, FOOT, spreads = true).keep(session, sessionPose, it) }
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
        views.keep(session, name, only = only)
        if (look.around) views.keep(session, "$name$BACK_TOP_SUFFIX", toViewers = BACK_AND_TOP, only = only)
        val display = session.document.value.character.displayPose
        if (!display.isRest) {
            session.send(SetDisplayPose(Pose()))
            views.keep(session, "$name-rest", only = only)
            session.send(SetDisplayPose(display))
        }
        if (FACE in close) parts.head?.let { views.keep(session, "$name-face", parts.faceFramed(it), FACE_AZIMUTHS, FACE_TILE, FACE_TILE) }
        if (FOOT in close) parts.foot?.let { closeUps.keepAround(session, it, "$name-foot") }
        closeParts.forEach { closeUps.keepAround(session, it, "$name-${it.value}") }
        if (JOINS in close) {
            parts.head?.let { closeUps.keepAround(session, it, "$name-neck") }
            parts.arm?.let { closeUps.keepAround(session, it, "$name-shoulder") }
            parts.leg?.let { closeUps.keepAround(session, it, "$name-hip") }
        }
        if (SURFACE in close) SurfaceCloseUps(work, errors, folder).keepAll(session, name)
        if (ANCHORS in close) AnchorsLook(work, errors, folder).keep(session, name)
        if (RELEASE in close) parts.arm?.let { SurfaceCloseUps(work, errors, folder).keepRelease(session, name, it, parts.head?.let(parts::faceFramed)) }
        if (DRAG in close) session.document.value.character.parts.firstOrNull { it.socket == null }?.let { SurfaceCloseUps(work, errors, folder).keepDrag(session, name, it.id) }
        for ((pose, set) in poses) {
            session.send(SetPose(set))
            views.keep(session, "$name-pose-$pose", only = only)
        }
        if (poses.isNotEmpty()) session.send(SetPose(Pose()))
    }
}

/**
 * Renders the character through each shot of the list [look] names, into a `shots` folder beside its views, under the shots' own names: each
 * shot's outfit, then its pose from [poses], rest for none.
 */
private fun renderShots(session: CharacterSession, work: TestWork, poses: Map<String, Pose>, look: LookArgs, errors: MutableList<String>, folder: File) {
    val shots = shotList(requireNotNull(look.shots))
    val views = ShotViews(work, errors, File(folder, SHOTS_FOLDER), look.unitsPerMetre)
    val example = session.document.value.character.id.value
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

/** Fails naming the [key] option when any of [ids] is not a part of [session]'s character, listing the parts it has. */
private fun requireParts(session: CharacterSession, ids: Collection<PartId>, key: String) {
    val character = session.document.value.character
    val missing = ids.filter { character.part(it) == null }
    require(missing.isEmpty()) {
        "$key=${missing.joinToString(",") { it.value }}: no such part or close-up; parts: ${character.parts.joinToString { it.id.value }}; close-ups: ${LookArgs.CLOSE_WORDS.sorted().joinToString()}"
    }
}

/** The outfits asked for that the example has, or its shown state alone when it has none. */
private fun outfitsOf(session: CharacterSession, look: LookArgs) =
    session.document.value.character.wardrobe.outfits.map { it.id }.filter { look.outfits == null || it.value in look.outfits }.ifEmpty { listOf(null) }

/** The test regions over [session]'s character, the eyeliner over its first painted eye. */
fun paintTestOf(session: CharacterSession, example: String) {
    val character = session.document.value.character
    val eye = character.paintedEyes.firstOrNull()?.anchor?.let { it.u to it.v }
    val base = if (example.startsWith("woman")) "skin" else "fur"
    paintTest(session, eye, "ink", "cream", base)
}
