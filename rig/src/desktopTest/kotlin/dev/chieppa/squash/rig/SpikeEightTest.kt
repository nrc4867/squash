package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun curve(length: Float = 1f, vararg strokes: BodyStroke = arrayOf(PLAY_BOW)) = BodyCurve(strokes.toList(), length)

/**
 * Spike 8, body curve posing: one stroke in Side poses tail, spine, neck and head while planted paws stay put. The joints
 * land on the stroke, the head lies along its end, the legs keep their pose, nothing inverts or smears, and volume holds.
 */
class SpikeEightTest {
    private val rig = fennec.rig
    private val planted = Pose(planted = PAWS)

    private fun placedPath(fit: BodyCurveFit) = PLAY_BOW.path().placed(fit.scale, fit.offset)

    /** Every joint of the chain as the solved pose takes it, but the head's far end, which is no joint. */
    private fun joints(fit: BodyCurveFit): List<Pair<String, Vec2>> {
        val chain = checkNotNull(bodyChain(rig))
        val transforms = boneTransforms(rig, fit.pose)
        return chain.links.filterNot { it.ahead }.flatMap { link ->
            listOf(link.from, link.to).map { rig.bones[link.owner].name.toString() to transforms[link.owner](it).let { p -> Vec2(p.x, p.y) } }
        }
    }

    @Test
    fun theChainsJointsLandOnTheStroke() {
        val fit = solveBodyCurve(rig, planted, curve())
        println("play bow: ${fit.pose.bones.joinToString { "${it.part.value}/${it.joint} ${"%.1f".format(it.degrees)}" }}")
        val path = placedPath(fit)
        val off = joints(fit).map { (name, p) -> name to path.distanceTo(p) }
        println("joints off the stroke: ${off.maxOf { it.second }} of a ${path.length} stroke; ${off.joinToString { "${it.first} ${"%.4f".format(it.second)}" }}")
        for ((name, d) in off) assertTrue(d <= ON_STROKE, "$name lies $d off the stroke")
        val most = BOW.bones.maxOf { abs(it.degrees - (fit.pose.of(it.part, it.joint)?.degrees ?: 0f)) }
        println("the bow comes back within $most degrees")
        assertTrue(most <= BOW_DEGREES, "a bone of the bow comes back $most degrees off")
    }

    @Test
    fun theHeadLiesAlongTheStrokesEnd() {
        val fit = solveBodyCurve(rig, planted, curve())
        val neck = checkNotNull(rig.indexOf(BoneName(HEAD, "neck")))
        val along = boneTransforms(rig, fit.pose)[neck].linear * rig.bones[neck].direction
        val end = placedPath(fit).endTangent
        val degrees = Math.toDegrees(acos(((along.x * end.x + along.y * end.y) / hypot(along.x, along.y)).toDouble().coerceIn(-1.0, 1.0)))
        println("head against the stroke's end: $degrees degrees")
        assertTrue(degrees <= HEAD_DEGREES, "the head points $degrees degrees off the stroke's end")
    }

    @Test
    fun legsJawAndEarsKeepTheirPoseAndPlantedPawsAreReported() {
        val fit = solveBodyCurve(rig, planted, curve())
        val kept = setOf(TORSO, TAIL, HEAD)
        val touched = fit.pose.bones.filter { it.part !in kept || it.joint == "jaw" }
        assertEquals(emptyList(), touched)
        assertEquals(PAWS, fit.paws.map { it.part }.toSet())
        for (paw in fit.paws) println("${paw.part.value} moves ${(paw.after - paw.before).length}")
        assertEquals(curve(), fit.pose.curve)
        assertEquals(PAWS, fit.pose.planted)
    }

    @Test
    fun nothingInvertsOrSmearsOutsideTheBands() {
        val fit = solveBodyCurve(rig, planted, curve())
        val transforms = boneTransforms(rig, fit.pose)
        val posed = rig.posed(fit.pose)
        for (id in listOf(TORSO, TAIL, HEAD)) {
            val part = checkNotNull(rig.part(id))
            val moved = checkNotNull(posed.parts[id])
            val single = edges(part.indices).filter { (a, b) -> part.weights.of(a).size == 1 && part.weights.of(b).size == 1 }
            val rigid = single.maxOfOrNull { edgeChange(part, moved, it) } ?: 0f
            val inverted = invertedTriangles(part, moved, transforms)
            println("${id.value}: $inverted inverted, rigid edges change by $rigid")
            assertEquals(0, inverted, "$id has inverted triangles")
            assertTrue(rigid <= RIGID_EDGE, "$id: an edge outside every band changed by $rigid")
        }
    }

    @Test
    fun aShorterOrLongerCurveSquashesOrStretchesTheTorsoKeepingVolume() {
        val part = checkNotNull(rig.part(TORSO))
        val rest = enclosedVolume(part.rest, part.indices)
        fun volume(fit: BodyCurveFit) = enclosedVolume(checkNotNull(rig.posed(fit.pose).parts[TORSO]), part.indices)
        val bowed = volume(solveBodyCurve(rig, planted, curve()))
        println("the bow at its rest length changes the torso's volume by ${bowed / rest - 1f}")
        assertTrue(abs(bowed / rest - 1f) <= VOLUME, "the bow changed the torso's volume by ${bowed / rest - 1f}")
        for (length in listOf(SQUASH, STRETCH)) {
            val fit = solveBodyCurve(rig, planted, curve(length))
            val posed = rig.posed(fit.pose)
            val change = volume(fit) / bowed - 1f
            val transforms = boneTransforms(rig, fit.pose)
            val inverted = listOf(TORSO, TAIL, HEAD).sumOf { invertedTriangles(checkNotNull(rig.part(it)), checkNotNull(posed.parts[it]), transforms) }
            val off = joints(fit).maxOf { placedPath(fit).distanceTo(it.second) }
            println("length $length: torso volume changes by $change against the bow, ${volume(fit) / rest - 1f} against rest, $inverted inverted, joints off the stroke by $off")
            assertTrue(abs(change) <= VOLUME, "at length $length the torso's volume changed by $change")
            assertEquals(0, inverted)
            assertTrue(off <= ON_STROKE, "at length $length a joint lies $off off the stroke")
            assertEquals(length, fit.pose.of(TORSO, "withers")?.stretch)
            assertEquals(length, fit.pose.of(TAIL, "tip")?.stretch)
            assertEquals(null, fit.pose.of(HEAD, "jaw"))
        }
    }

    @Test
    fun aStrokeThroughTheRestJointsLeavesTheBodyAtRest() {
        val chain = checkNotNull(bodyChain(rig))
        val points = (chain.links.map { it.from } + chain.links.last().to).map { Vec2(it.x, it.y) }
        val stroke = BodyStroke(View.SIDE, listOf(points.first()).plus(points.zipWithNext().flatMap { (a, b) -> listOf(a + (b - a) * (1f / 3f), a + (b - a) * (2f / 3f), b) }).map { CurvePoint(it.x, it.y) })
        val fit = solveBodyCurve(rig, Pose(), BodyCurve(listOf(stroke)))
        val most = fit.pose.bones.maxOfOrNull { abs(it.degrees) } ?: 0f
        println("a stroke through the rest joints turns a bone by $most degrees at most, carried by ${fit.offset}")
        assertTrue(most <= REST_DEGREES, "a stroke through the rest joints turns a bone by $most")
    }

    @Test
    fun aTopStrokeBendsTheChainSidewaysWithoutUndoingTheSideBend() {
        val side = solveBodyCurve(rig, planted, curve())
        val both = solveBodyCurve(rig, planted, curve(1f, PLAY_BOW, SWAY))
        val tip = both.pose.bones.maxOf { abs(it.degrees - (side.pose.of(it.part, it.joint)?.degrees ?: 0f)) }
        val lean = both.pose.bones.maxOf { abs(it.lean) }
        println("a Top stroke leans the chain by $lean degrees at most and changes no tip by more than $tip; ${both.pose.bones.joinToString { "${it.part.value}/${it.joint} ${"%.1f".format(it.lean)}" }}")
        assertEquals(0f, tip, "the Side bend stays")
        assertTrue(lean >= MIN_SWAY, "the chain leans sideways")
        for (bone in both.pose.bones) {
            val reach = checkNotNull(rig.bone(BoneName(bone.part, bone.joint))).limits?.turnReach() ?: continue
            assertTrue(abs(bone.lean) <= reach + 1e-3f, "${bone.part.value}/${bone.joint} leans ${bone.lean} past $reach")
        }
        val path = SWAY.path().placed(both.top.scale, both.top.offset)
        val off = topJoints(both).map { (name, p) -> name to path.distanceTo(p) }
        println("joints off the Top stroke: ${off.maxOf { it.second }} of a ${path.length} stroke; ${off.joinToString { "${it.first} ${"%.4f".format(it.second)}" }}")
        for ((name, d) in off) assertTrue(d <= ON_TOP, "$name lies $d off the Top stroke")
        assertEquals(SWAY, both.pose.curve?.stroke(View.TOP))
        val back = SWAYED.bones.maxOf { abs(it.lean - (both.pose.of(it.part, it.joint)?.lean ?: 0f)) }
        println("the sway comes back within $back degrees")
        assertTrue(back <= SWAY_DEGREES, "a bone of the sway comes back $back degrees off")
    }

    @Test
    fun aTopStrokeAloneKeepsTheBendTheChainHad() {
        val bowed = BOW.copy(planted = PAWS)
        val fit = solveBodyCurve(rig, bowed, curve(1f, SWAY))
        val tip = BOW.bones.maxOf { abs(it.degrees - (fit.pose.of(it.part, it.joint)?.degrees ?: 0f)) }
        assertEquals(0f, tip, "the bend the chain had stays")
        assertTrue(fit.pose.bones.maxOf { abs(it.lean) } >= MIN_SWAY, "the chain leans sideways")
        assertEquals(curve(1f, SWAY), fit.pose.curve)
    }

    /** Every joint of the chain as the solved pose takes it, seen from above, but the head's far end. */
    private fun topJoints(fit: BodyCurveFit): List<Pair<String, Vec2>> {
        val chain = checkNotNull(bodyChain(rig))
        val transforms = boneTransforms(rig, fit.pose)
        return chain.links.filterNot { it.ahead }.flatMap { link ->
            listOf(link.from, link.to).map { rig.bones[link.owner].name.toString() to transforms[link.owner](it).let { p -> Vec2(p.x, p.z) } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theSameCurveAfterAMovedKneeGivesTheSameAngles() = runTest {
        val moved = rigOf(SetParameter(PartId("back-leg-l"), "move.joint.knee", MOVED_KNEE).apply(savedFennec()).document)
        val most = differences(solveBodyCurve(rig, planted, curve()).pose, solveBodyCurve(moved, planted, curve()).pose)
        println("after a moved knee the angles differ by $most degrees at most")
        assertTrue(most <= SAME_DEGREES, "after a moved knee a bone's angle moved by $most")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theSameCurveOnATallerTorsoStillLiesOnTheStroke() = runTest {
        val document = savedFennec()
        val outline = checkNotNull(document.character.resolved(TORSO)?.outlines?.get(View.SIDE))
        val taller = OutlineCurve(outline.points.map { CurvePoint(it.x, it.y * REFIT_SCALE) })
        val refitted = rigOf(SetOutline(TORSO, View.SIDE, taller).apply(document).document)
        val before = solveBodyCurve(rig, planted, curve()).pose
        val fit = solveBodyCurve(refitted, planted, curve())
        val most = differences(before, fit.pose)
        val path = PLAY_BOW.path().placed(fit.scale, fit.offset)
        val chain = checkNotNull(bodyChain(refitted))
        val transforms = boneTransforms(refitted, fit.pose)
        val off = chain.links.filterNot { it.ahead }.flatMap { listOf(it.from, it.to).map { p -> transforms[it.owner](p) } }.maxOf { path.distanceTo(Vec2(it.x, it.y)) }
        println("on a torso $REFIT_SCALE as tall the angles differ by $most degrees at most and the joints lie $off off the stroke")
        assertEquals(before.bones.map { it.part to it.joint }.toSet(), fit.pose.bones.map { it.part to it.joint }.toSet())
        assertTrue(off <= ON_STROKE, "on a taller torso a joint lies $off off the stroke")
        assertTrue(most <= REFIT_DEGREES, "on a taller torso a bone's angle moved by $most")
    }

    private fun differences(before: Pose, after: Pose): Float =
        before.bones.maxOf { bone -> abs(bone.degrees - (after.of(bone.part, bone.joint)?.degrees ?: 0f)) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun kotlinx.coroutines.test.TestScope.rigOf(document: SquashDocument): Rig {
        val session = CharacterSession(document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionRig(session).rig
        runCurrent()
        return checkNotNull(node.output.value)
    }

    private companion object {
        const val ON_STROKE = 2e-3f
        const val BOW_DEGREES = 0.5f
        const val HEAD_DEGREES = 0.5
        const val RIGID_EDGE = 1e-4f
        const val VOLUME = 0.035f
        const val SQUASH = 0.92f
        const val STRETCH = 1.1f
        const val REST_DEGREES = 0.5f
        const val REFIT_SCALE = 1.1f
        const val REFIT_DEGREES = 8f
        const val MOVED_KNEE = 0.6f
        const val SAME_DEGREES = 1e-3f
        const val ON_TOP = 2e-3f
        const val SWAY_DEGREES = 1f
        const val MIN_SWAY = 2f
    }
}
