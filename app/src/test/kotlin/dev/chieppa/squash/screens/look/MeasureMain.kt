package dev.chieppa.squash.screens.look

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.GarmentCover
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.screens.look.measure.SurfaceChecks
import dev.chieppa.squash.screens.look.measure.SurfaceStackTimings
import dev.chieppa.squash.screens.look.measure.SurfaceEdit
import dev.chieppa.squash.screens.look.measure.armDepthInside
import dev.chieppa.squash.screens.look.measure.armInside
import dev.chieppa.squash.screens.look.measure.digitTipGaps
import dev.chieppa.squash.screens.look.measure.faceShade
import dev.chieppa.squash.screens.look.measure.hipSpread
import dev.chieppa.squash.screens.look.measure.partBoxes
import dev.chieppa.squash.screens.look.measure.profile
import dev.chieppa.squash.screens.look.measure.propDrift
import dev.chieppa.squash.screens.look.measure.seen
import dev.chieppa.squash.screens.look.measure.shownCorners
import dev.chieppa.squash.screens.look.measure.skullShown
import dev.chieppa.squash.screens.look.measure.socketRings
import dev.chieppa.squash.screens.look.measure.terminator
import dev.chieppa.squash.screens.smoke.CloseUps
import dev.chieppa.squash.screens.smoke.RayGrid
import dev.chieppa.squash.screens.smoke.probeAzimuth
import kotlin.time.measureTime

/** The views the terminator is measured from, as the look command renders them. */
private val VIEWS = listOf("front" to 0f, "three-quarter" to 40f, "side" to 90f)

/**
 * Prints the stage's checks for examples, as numbers, through the real graph and rig with no GPU and no test runner: per outfit as
 * shown, in the display pose, with the arm volume at rest too, and in each named pose in the first of them, with `parts=true` every
 * part's origin and box as shown, heights from the ground. Run as `./gradlew :app:measure -Pmeasure="example=woman-dressed outfits=all poses=all"`.
 */
fun main(args: Array<String>) {
    val look = LookArgs(args)
    val took = measureTime { look.examples.forEach { measure(it, look) } }
    println("measure: ${look.examples.joinToString()} in ${took.inWholeMilliseconds} ms")
}

private fun measure(example: String, look: LookArgs) = measureDocument(example, exampleDocument(example, look.fromFile, look.examplesFolder), examplePoses(example), look)

/**
 * Prints [document]'s checks as [look] asks, each line headed [example] and the outfit: per outfit as shown, in the first outfit each of
 * [namedPoses], and with [timings] the one surface's build timed whole and edit by edit.
 */
fun measureDocument(example: String, document: SquashDocument, namedPoses: Map<String, Pose>, look: LookArgs, timings: Boolean = true) = TestWork().use { work ->
    val session = CharacterSession(document, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
    SessionPose(session)
    look.settings.forEach { (part, name, value) -> session.send(SetParameter(PartId(part), name, value)) }
    work.settle(session)
    val head = LookParts(session.document.value.character).head
    val outfits = session.document.value.character.wardrobe.outfits.map { it.id }.filter { look.outfits == null || it.value in look.outfits }.ifEmpty { listOf(null) }
    val poses = namedPoses.filterKeys { look.poses == null || it in look.poses }
    for (outfit in outfits) {
        if (outfit != null) session.send(ShowOutfit(outfit))
        val name = "$example ${outfit?.value ?: "as saved"}"
        val shown = posed(session, work)
        val rest = checkNotNull(session.assembly.output.value)
        println("$name: arm volume inside the torso at rest ${armInside(rest).format()}")
        if (look.stackOnly) {
            if (outfit == outfits.first()) stack(name, session, work, look)
            continue
        }
        report(name, shown, head?.let(shown::part))
        if (outfit == outfits.first()) session.characterSurface.output.value?.surface?.let { sessionSurface(name, session, work, it, look.profile, timings) }
        if (look.parts) partBoxes(shown).forEach { println("$name: part ${it.part} origin ${it.origin.format()} box ${it.low.format()} to ${it.high.format()}") }
        if (outfit != outfits.first()) continue
        for ((pose, set) in poses) {
            session.send(SetPose(set))
            val body = posed(session, work)
            println("$name, $pose: arm volume inside the torso ${armInside(body).format()}")
            propDrift(rest, body).takeIf { it.isNotEmpty() }?.let { println("$name, $pose: owned parts' drift from rigid ${it.format()}") }
        }
        if (poses.isNotEmpty()) session.send(SetPose(Pose()))
    }
}

/**
 * The one surface's checks, and with [timings] its build timed whole and after the head, a front leg and the torso are each made 2% larger and back;
 * with `profile=true`, where the time of a whole build and of each edit goes.
 */
private fun sessionSurface(name: String, session: CharacterSession, work: TestWork, surface: CharacterSurface, profile: Boolean, timings: Boolean) {
    val checks = SurfaceChecks(session, work)
    checks.report(name, surface, VIEWS.map { it.first to probeAzimuth(it.second) })
    if (!timings) return checks.close()
    val edits = surfaceEdits(session)
    if (profile) checks.profile(name, edits)
    checks.timings(name, edits, coarse = setOf("torso"))
    checks.close()
    SurfaceStackTimings(session, work).also { it.report(name, edits, coarse = setOf("torso"), profile) }.close()
}

/** The one surface's stack timed alone, over the edits [look] names, stage by stage when it asks for a profile. */
private fun stack(name: String, session: CharacterSession, work: TestWork, look: LookArgs) {
    val edits = surfaceEdits(session).filter { look.edits == null || it.label in look.edits }
    if (look.profile) SurfaceChecks(session, work).also { it.profile(name, edits) }.close()
    SurfaceStackTimings(session, work).also { it.report(name, edits, coarse = setOf("torso"), look.profile, look.record, otherCovers(session, work)) }.close()
}

/** What the first outfit after the shown one wears, as a one surface wears it, or null with no other outfit; the shown outfit stays shown. */
private fun otherCovers(session: CharacterSession, work: TestWork): List<GarmentCover>? {
    val wardrobe = session.document.value.character.wardrobe
    val shown = wardrobe.shown ?: return null
    val other = wardrobe.outfits.map { it.id }.firstOrNull { it != shown } ?: return null
    session.send(ShowOutfit(other))
    work.settle(session)
    val covers = surfaceCovers(session.document.value.character)
    session.send(ShowOutfit(shown))
    work.settle(session)
    return covers
}

/** The edits timed: the head, a front leg or an arm, and the torso, each at sizes about 2% apart. */
private fun surfaceEdits(session: CharacterSession): List<SurfaceEdit> {
    val character = session.document.value.character
    return listOf("head" to "size", "front-leg-l" to "size", "arm-l" to "size", "torso" to "size").mapNotNull { (id, parameter) ->
        character.part(PartId(id))?.let { instance ->
            val rest = character.resolved(instance).params[parameter] ?: checkNotNull(session.builtTemplate(instance).defaults[parameter])
            SurfaceEdit(id, instance.id, parameter, rest)
        }
    }
}

private fun posed(session: CharacterSession, work: TestWork): AssembledCharacter {
    work.settle(session)
    return checkNotNull(session.posedBody.output.value)
}

private fun report(name: String, body: AssembledCharacter, head: PlacedPart?) {
    val grid = RayGrid(shownCorners(body))
    val seen: (Vec3) -> Boolean = { grid.seen(it) }
    println("$name: arm volume inside the torso as shown ${armInside(body).format()}")
    println("$name: arm depth inside the torso as shown ${armDepthInside(body).format()}")
    println("$name: hip socket spread ${hipSpread(body)?.let { "%.4f".format(it) } ?: "none"}")
    println("$name: smallest gap between digit tips ${digitTipGaps(body).format()}")
    println("$name: socket ring length, all / seen ${socketRings(body, seen).mapValues { (_, v) -> "%.3f / %.3f".format(v.first, v.second) }}")
    head?.let(::profile)?.let { println("$name: profile depth brow to chin %.4f, deepest notch %.4f (%.3f of the head's height) ${it.forward.map { f -> "%.3f".format(f) }}".format(it.depth, it.notch, it.share)) }
    head?.let { skullShown(it, seen) }?.let { (shown, all) -> println("$name: skull vertices seen outside the hair $shown of $all") }
    head?.let { faceShade(it, PartMaterial.DEFAULT_TO_LIGHT, VIEWS.map { v -> probeAzimuth(v.second) }, grid::escapes) }?.let { shares ->
        println("$name: face in shade ${VIEWS.indices.joinToString { "${VIEWS[it].first} %.3f".format(shares[it]) }}")
    }
    terminator(body, grid, PartMaterial.DEFAULT_TO_LIGHT, VIEWS.map { probeAzimuth(it.second) }).forEachIndexed { i, t ->
        println("$name: terminator ${VIEWS[i].first} length %.2f in ${t.pieces} pieces, ${t.blotches.values.sum()} blotches ${t.blotches}".format(t.length))
    }
}

private fun Vec3.format() = "(%.3f, %.3f, %.3f)".format(x, y, z)

private fun <K> Map<K, Float>.format(): String = entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=%.5f".format(v) }
