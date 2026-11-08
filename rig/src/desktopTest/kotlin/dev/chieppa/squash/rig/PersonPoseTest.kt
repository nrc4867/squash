package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val FOOT_L = PartId("foot-l")
private val FOOT_R = PartId("foot-r")
private val HAND_L = PartId("hand-l")
private val HAND_R = PartId("hand-r")
private val PERSON_TORSO = PartId("torso")
private val PERSON_HEAD = PartId("head")

/** Where the raised hand goes from where it hangs, in body units. */
private val RAISE = Vec3(0.1f, 0.5f, 0.1f)
private const val LANDS = 1e-2f
private const val STILL = 1e-5f

/** How far forward the Side stroke carries the top of the spine, in body units, and how near the head keeps to its rest turn from the end tangent, in degrees. */
private const val LEAN = 0.16f
private const val ALONG_TANGENT = 6f

/** How far a foot may slide while the body curve tips the pelvis, its leg already straight at rest, in body units. */
private const val STRAIGHT_LEG_SLIDE = 0.02f

/** How many degrees the look must turn the head at least. */
private const val TURNED = 10f

/** The example person, rigged by a session's graph once it has settled. */
@OptIn(ExperimentalCoroutinesApi::class)
private val person: Rig by lazy {
    var rig: Rig? = null
    runTest {
        val document = readDocument(Path.of(System.getProperty("squash.examples"), "person.json").readText())
        val session = CharacterSession(document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionRig(session).rig
        runCurrent()
        check(session.graph.settled.value) { "The person's graph did not settle" }
        rig = checkNotNull(node.output.value)
    }
    checkNotNull(rig)
}

/** A biped poses by its parts' roles: its feet plant, its hands reach and never pin, its spine and head take the body curve. */
class PersonPoseTest {
    private val solver = PoseSolver(person)
    private val standing = Pose(planted = setOf(FOOT_L, FOOT_R))

    @Test
    fun theBodyStandsUprightAndItsHandsReachWhileItsFeetPlant() {
        assertTrue(person.isUpright)
        val limbs = solver.chains.limbs.associateBy { it.id }
        assertEquals(setOf(FOOT_L, FOOT_R, HAND_L, HAND_R), limbs.keys)
        assertTrue(limbs.getValue(FOOT_L).plants && limbs.getValue(FOOT_R).plants)
        assertTrue(!limbs.getValue(HAND_L).plants && !limbs.getValue(HAND_R).plants)
        val handles = solver.handles(standing)
        assertTrue(HAND_L in handles.ends && HAND_R in handles.ends, "each hand has its dot")
        assertEquals(setOf(FOOT_L, FOOT_R), handles.pins.keys, "only the feet are pinned")
    }

    @Test
    fun aRaisedHandLandsWithTheFeetUnmoved() {
        val limb = assertNotNull(solver.chains.limb(HAND_R))
        val target = solver.endOf(standing, limb) + RAISE
        val raised = solver.reach(standing, limb, target)
        assertTrue(raised.report.missed < LANDS, "the hand lands, ${raised.report.missed} off")
        listOf(FOOT_L, FOOT_R).forEach { id ->
            val foot = assertNotNull(solver.chains.limb(id))
            assertTrue((solver.endOf(standing, foot) - solver.endOf(raised.pose, foot)).length < STILL, "$id stays put")
        }
    }

    @Test
    fun aHandBroughtForwardBendsTheElbowBack() {
        val limb = assertNotNull(solver.chains.limb(HAND_R))
        val held = solver.reach(standing, limb, solver.endOf(standing, limb) + Vec3(0.15f, 0.2f, 0f))
        val work = PoseWork(person, held.pose)
        assertTrue(bentAway(work.joint(limb.upper), work.joint(limb.lower), work.joint(limb.end)) < 0f, "the elbow stands behind the line from shoulder to wrist")
    }

    @Test
    fun aLiftedFootBendsTheKneeForward() {
        val limb = assertNotNull(solver.chains.limb(FOOT_L))
        val lifted = solver.reach(standing.withPlanted(FOOT_L, false), limb, solver.endOf(standing, limb) + Vec3(-0.05f, 0.15f, 0f))
        val work = PoseWork(person, lifted.pose)
        assertTrue(bentAway(work.joint(limb.upper), work.joint(limb.lower), work.joint(limb.end)) > 0f, "the knee stands ahead of the line from hip to ankle")
    }

    @Test
    fun theBodyCurveBendsTheSpineWithTheHeadKeepingItsTurnFromTheEndTangent() {
        val bones = assertNotNull(curveBones(person))
        assertTrue(bones.behind.isEmpty())
        assertTrue(bones.spine.all { it.part == PERSON_TORSO })
        assertTrue(bones.ahead.all { it.part == PERSON_HEAD })
        val chain = bones.spine.mapNotNull { person.bone(it)?.pivot } + assertNotNull(person.bone(bones.ahead.first())).pivot
        val last = chain.lastIndex.toFloat()
        val drawn = chain.mapIndexed { i, p -> Vec2(p.x + LEAN * sin(PI * i / last / 2.0).toFloat(), p.y) }
        val stroke = BodyStroke(View.SIDE, listOf(drawn.first()).plus(drawn.zipWithNext().flatMap { (a, b) -> listOf(a + (b - a) * (1f / 3f), a + (b - a) * (2f / 3f), b) }).map { CurvePoint(it.x, it.y) })
        val bent = solver.bendTo(standing, BodyCurve(listOf(stroke), 1f))
        assertTrue(bent.pose.bones.count { it.part == PERSON_TORSO } >= 3, "the spine bends at the torso's joints")
        val tangent = drawn.last() - drawn[drawn.lastIndex - 1]
        val tangentTurn = degrees(atan2(tangent.y, tangent.x)) - degrees(atan2(1f, 0f))
        val head = assertNotNull(person.indexOf(BoneName(PERSON_HEAD, "neck")))
        val rest = person.bones[head].direction
        val now = PoseWork(person, bent.pose).turn(head) * rest
        val headTurn = degrees(atan2(now.y, now.x)) - degrees(atan2(rest.y, rest.x))
        assertTrue(tangentTurn < 0f, "the stroke leans the top of the spine forward")
        assertTrue(abs(headTurn - tangentTurn) < ALONG_TANGENT, "the head turns as the end tangent does: $headTurn against $tangentTurn")
        assertTrue(bent.report.slid.values.all { it < STRAIGHT_LEG_SLIDE }, "the straight legs hold the feet as near as they reach: ${bent.report.slid}")
    }

    @Test
    fun thePinsHoldTheFeetWhenTheBodyIsDragged() {
        val moved = solver.moveBody(standing, Vec3(0.03f, -0.08f, 0f))
        assertTrue(moved.report.slid.isEmpty(), "both feet stay where they stood: ${moved.report.slid}")
        listOf(FOOT_L, FOOT_R).forEach { id ->
            val foot = assertNotNull(solver.chains.limb(id))
            assertTrue((solver.endOf(standing, foot) - solver.endOf(moved.pose, foot)).length < LIMB_TOLERANCE * 2f, "$id stays put")
        }
    }

    @Test
    fun theLookTurnsTheHead() {
        val look = assertNotNull(solver.chains.look)
        assertEquals(assertNotNull(person.indexOf(BoneName(PERSON_HEAD, "neck"))), look.head)
        val from = PoseWork(person, standing).joint(look.head)
        val looked = solver.look(standing, from + Vec3(1f, 0.2f, -0.6f))
        assertTrue(solver.turnedBetween(standing, looked.pose, look.head) > TURNED)
    }

    private fun degrees(radians: Float) = radians * 180f / PI.toFloat()

    /** How far the middle joint stands off the line from [top] to [end], seen from the side: ahead of it above zero, behind it below. */
    private fun bentAway(top: Vec3, middle: Vec3, end: Vec3): Float {
        val line = end - top
        val off = middle - (top + end) * 0.5f
        return off.x * -line.y + off.y * line.x
    }
}
