package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.withHangingPushed
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
import dev.chieppa.squash.screens.look.measure.faceShade
import dev.chieppa.squash.screens.look.measure.hipSpread
import dev.chieppa.squash.screens.look.measure.armInsideSkirt
import dev.chieppa.squash.screens.look.measure.legOutsideSkirt
import dev.chieppa.squash.screens.look.measure.partBoxes
import dev.chieppa.squash.screens.look.measure.skirtInsideLegs
import dev.chieppa.squash.screens.look.measure.profile
import dev.chieppa.squash.screens.look.measure.propDrift
import dev.chieppa.squash.screens.look.measure.propsUnderCloth
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
        val shown = posed(session, work)
        val rest = checkNotNull(session.assembly.output.value)
        println("$name: arm volume inside the torso at rest ${armInside(rest).format()}")
        legOutsideSkirt(rest).takeIf { it.isNotEmpty() }?.let { println("$name: leg volume outside the skirt at rest ${it.format()}, skirt vertices inside a leg ${skirtInsideLegs(rest)}") }
        report(name, shown, head?.let(shown::part), dressed = outfit != null)
        if (look.parts) partBoxes(shown).forEach { println("$name: part ${it.part} origin ${it.origin.format()} box ${it.low.format()} to ${it.high.format()}") }
        if (outfit != outfits.first()) continue
        for ((pose, set) in poses) {
            session.send(SetPose(set))
            val body = posed(session, work)
            println("$name, $pose: arm volume inside the torso ${armInside(body).format()}")
            if (outfit != null) RayGrid(shownCorners(body)).let { grid -> bareSkin(body) { grid.seen(it) } }.let { println("$name, $pose: bare skin seen on covered parts ${it.values.sum()} $it") }
            propDrift(rest, body).takeIf { it.isNotEmpty() }?.let { println("$name, $pose: owned parts' drift from rigid ${it.format()}") }
            legOutsideSkirt(body).takeIf { it.isNotEmpty() }?.let {
                println("$name, $pose: leg volume outside the skirt ${it.format()}, skirt vertices inside a leg ${skirtInsideLegs(body)}")
                println("$name, $pose: hanging branches pushed in %.2f ms".format(pushTime(body)))
                armInsideSkirt(body).takeIf { it.isNotEmpty() }?.let { println("$name, $pose: arm and hand vertices inside the skirt $it") }
            }
        }
        if (poses.isNotEmpty()) session.send(SetPose(Pose()))
    }
}

/** How long pushing [body]'s hanging branches out of what hangs inside them takes, the median of [TIMED_RUNS] runs once warm, in ms. */
private fun pushTime(body: AssembledCharacter): Double {
    repeat(TIMED_RUNS) { body.withHangingPushed() }
    val runs = List(TIMED_RUNS) { measureTime { body.withHangingPushed() }.inWholeMicroseconds / 1000.0 }
    return runs.sorted()[TIMED_RUNS / 2]
}

private const val TIMED_RUNS = 21

private fun posed(session: CharacterSession, work: TestWork): AssembledCharacter {
    work.settle(session)
    return checkNotNull(session.posedBody.output.value)
}

private fun report(name: String, body: AssembledCharacter, head: PlacedPart?, dressed: Boolean) {
    val grid = RayGrid(shownCorners(body))
    val seen: (Vec3) -> Boolean = { grid.seen(it) }
    println("$name: arm volume inside the torso as shown ${armInside(body).format()}")
    legOutsideSkirt(body).takeIf { it.isNotEmpty() }?.let { println("$name: leg volume outside the skirt as shown ${it.format()}, skirt vertices inside a leg ${skirtInsideLegs(body)}") }
    armInsideSkirt(body).takeIf { it.isNotEmpty() }?.let { println("$name: arm and hand vertices inside the skirt as shown $it") }
    println("$name: hip socket spread ${hipSpread(body)?.let { "%.4f".format(it) } ?: "none"}")
    println("$name: smallest gap between digit tips ${digitTipGaps(body).format()}")
    println("$name: socket ring length, all / seen ${socketRings(body, seen).mapValues { (_, v) -> "%.3f / %.3f".format(v.first, v.second) }}")
    head?.let(::profile)?.let { println("$name: profile depth brow to chin %.4f, deepest notch %.4f (%.3f of the head's height) ${it.forward.map { f -> "%.3f".format(f) }}".format(it.depth, it.notch, it.share)) }
    head?.let { skullShown(it, seen) }?.let { (shown, all) -> println("$name: skull vertices seen outside the hair $shown of $all") }
    if (dressed) bareSkin(body, seen).let { println("$name: bare skin seen on covered parts ${it.values.sum()} ${it}") }
    propsUnderCloth(body, seen).takeIf { it.isNotEmpty() }?.let { println("$name: owned part vertices under cloth, all / seen ${it.mapValues { (_, v) -> "${v.first} / ${v.second}" }}") }
    head?.let { faceShade(it, PartMaterial.DEFAULT_TO_LIGHT, VIEWS.map { v -> probeAzimuth(v.second) }, grid::escapes) }?.let { shares ->
        println("$name: face in shade ${VIEWS.indices.joinToString { "${VIEWS[it].first} %.3f".format(shares[it]) }}")
    }
    terminator(body, grid, PartMaterial.DEFAULT_TO_LIGHT, VIEWS.map { probeAzimuth(it.second) }).forEachIndexed { i, t ->
        println("$name: terminator ${VIEWS[i].first} length %.2f in ${t.pieces} pieces, ${t.blotches.values.sum()} blotches ${t.blotches}".format(t.length))
    }
}

private fun Vec3.format() = "(%.3f, %.3f, %.3f)".format(x, y, z)

private fun <K> Map<K, Float>.format(): String = entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=%.5f".format(v) }
