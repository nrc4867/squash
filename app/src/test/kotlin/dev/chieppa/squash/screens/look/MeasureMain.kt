package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.look.measure.armInside
import dev.chieppa.squash.screens.look.measure.bareSkin
import dev.chieppa.squash.screens.look.measure.digitTipGaps
import dev.chieppa.squash.screens.look.measure.hipSpread
import dev.chieppa.squash.screens.look.measure.profile
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
 * Prints the stage's checks for examples, as numbers, through the real graph and rig with no GPU and no test runner: per outfit at
 * rest, and in each named pose in the first of them. Run as `./gradlew :app:measure -Pmeasure="example=woman-dressed outfits=all poses=all"`.
 */
fun main(args: Array<String>) {
    val look = LookArgs(args)
    val took = measureTime { look.examples.forEach { measure(it, look) } }
    println("measure: ${look.examples.joinToString()} in ${took.inWholeMilliseconds} ms")
}

private fun measure(example: String, look: LookArgs) = TestWork().use { work ->
    val session = CharacterSession(exampleDocument(example, look.fromFile, look.examplesFolder), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
    SessionPose(session)
    work.settle(session)
    val head = LookParts(session.document.value.character).head
    val outfits = session.document.value.character.wardrobe.outfits.map { it.id }.filter { look.outfits == null || it.value in look.outfits }.ifEmpty { listOf(null) }
    val poses = examplePoses(example).filterKeys { look.poses == null || it in look.poses }
    for (outfit in outfits) {
        if (outfit != null) session.send(ShowOutfit(outfit))
        val name = "$example ${outfit?.value ?: "as saved"}"
        val rest = posed(session, work)
        report(name, rest, head?.let(rest::part), dressed = outfit != null)
        if (outfit != outfits.first()) continue
        for ((pose, set) in poses) {
            session.send(SetPose(set))
            println("$name, $pose: arm volume inside the torso ${armInside(posed(session, work)).format()}")
        }
        if (poses.isNotEmpty()) session.send(SetPose(Pose()))
    }
}

private fun posed(session: CharacterSession, work: TestWork): AssembledCharacter {
    work.settle(session)
    return checkNotNull(session.posedBody.output.value)
}

private fun report(name: String, body: AssembledCharacter, head: PlacedPart?, dressed: Boolean) {
    val grid = RayGrid(shownCorners(body))
    val seen: (Vec3) -> Boolean = { grid.seen(it) }
    println("$name: arm volume inside the torso ${armInside(body).format()}")
    println("$name: hip socket spread ${hipSpread(body)?.let { "%.4f".format(it) } ?: "none"}")
    println("$name: smallest gap between digit tips ${digitTipGaps(body).format()}")
    println("$name: socket ring length, all / seen ${socketRings(body, seen).mapValues { (_, v) -> "%.3f / %.3f".format(v.first, v.second) }}")
    head?.let(::profile)?.let { println("$name: profile notch brow to chin %.4f (%.3f of the head's height) ${it.forward.map { f -> "%.3f".format(f) }}".format(it.notch, it.share)) }
    head?.let { skullShown(it, seen) }?.let { (shown, all) -> println("$name: skull vertices seen outside the hair $shown of $all") }
    if (dressed) bareSkin(body, seen).let { println("$name: bare skin seen on covered parts ${it.values.sum()} ${it}") }
    terminator(body, grid, PartMaterial.DEFAULT_TO_LIGHT, VIEWS.map { probeAzimuth(it.second) }).forEachIndexed { i, t ->
        println("$name: terminator ${VIEWS[i].first} length %.2f in ${t.pieces} pieces, ${t.blotches.values.sum()} blotches ${t.blotches}".format(t.length))
    }
}

private fun <K> Map<K, Float>.format(): String = entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=%.5f".format(v) }
