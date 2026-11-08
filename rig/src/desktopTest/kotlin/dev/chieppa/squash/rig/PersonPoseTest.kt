package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
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

/** How far any point of a planted foot may move while the body curve tips the pelvis or a drag moves the body, in body units. */
private const val FOOT_HELD = LIMB_TOLERANCE * 2f

/** A body drag that bends the knees without taking an ankle past its limits, in body units. */
private val CROUCH = Vec3(0.02f, -0.04f, 0f)

/** How many degrees the look must turn the head at least. */
private const val TURNED = 10f

private val person: Rig get() = personRig

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
        assertTrue(bent.report.slid.isEmpty(), "both feet come back: ${bent.report.slid}")
    }

    @Test
    fun theBodyCurveHoldsThePlantedFeetAsABodyDragDoes() {
        val bones = assertNotNull(curveBones(person))
        val chain = bones.spine.mapNotNull { person.bone(it)?.pivot } + assertNotNull(person.bone(bones.ahead.first())).pivot
        val last = chain.lastIndex.toFloat()
        val drawn = chain.mapIndexed { i, p -> Vec2(p.x + LEAN * sin(PI * i / last / 2.0).toFloat(), p.y) }
        val stroke = BodyStroke(View.SIDE, listOf(drawn.first()).plus(drawn.zipWithNext().flatMap { (a, b) -> listOf(a + (b - a) * (1f / 3f), a + (b - a) * (2f / 3f), b) }).map { CurvePoint(it.x, it.y) })
        val bent = solver.bendTo(standing, BodyCurve(listOf(stroke), 1f)).pose
        val hips = assertNotNull(person.indexOf(BoneName(PERSON_TORSO, "hips")))
        assertTrue(solver.turnedBetween(standing, bent, hips) > TURNED, "the curve tips the pelvis")
        val dragged = solver.moveBody(standing, CROUCH).pose
        val curved = footShift(standing, bent)
        val moved = footShift(standing, dragged)
        println("feet under the body curve move ${"%.5f".format(curved)}, under a body drag ${"%.5f".format(moved)}")
        assertTrue(moved < FOOT_HELD, "a body drag holds the feet: $moved")
        assertTrue(curved < FOOT_HELD, "the body curve holds the feet: $curved")
        assertTrue(solver.clearCurve(bent).pose.let { footShift(standing, it) } < FOOT_HELD, "clearing the curve holds them too")
    }

    /**
     * The furthest any point of either foot moves from [from] to [to], carried by the foot's own bone: how far the foot slides or
     * rolls on the ground, leaving out the ankle's skin, which the shin carries as the knee bends.
     */
    private fun footShift(from: Pose, to: Pose): Float {
        val a = boneTransforms(person, from)
        val b = boneTransforms(person, to)
        return listOf(FOOT_L, FOOT_R).maxOf { foot ->
            val bone = person.bones.indexOfFirst { it.name.part == foot }
            val rest = assertNotNull(person.part(foot)).rest
            (rest.indices step 3).maxOf { Vec3(rest[it], rest[it + 1], rest[it + 2]).let { v -> (b[bone](v) - a[bone](v)).length } }
        }
    }

    private fun degrees(radians: Float) = radians * 180f / PI.toFloat()

    /** How far the middle joint stands off the line from [top] to [end], seen from the side: ahead of it above zero, behind it below. */
    private fun bentAway(top: Vec3, middle: Vec3, end: Vec3): Float {
        val line = end - top
        val off = middle - (top + end) * 0.5f
        return off.x * -line.y + off.y * line.x
    }
}
