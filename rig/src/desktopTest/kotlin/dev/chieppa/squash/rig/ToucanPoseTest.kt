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
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val FOOT_L = PartId("foot-l")
private val FOOT_R = PartId("foot-r")
private val WING_L = PartId("wing-l")
private val WING_R = PartId("wing-r")
private val TOUCAN_TORSO = PartId("torso")
private val TOUCAN_HEAD = PartId("head")
private val TOUCAN_TAIL = PartId("tail")

/** Which way the spread wing reaches from its shoulder: out to its side, a little up and back. */
private val SPREAD = Vec3(-0.3f, 0.35f, -1f)

/** How far out the spread wrist reaches, as a share of its folded distance from the shoulder. */
private const val REACH = 0.95f
private const val LANDS = 1e-2f
private const val STILL = 1e-5f

/** How far the Side stroke carries the chest forward of the vent, in body units. */
private const val LEAN = 0.12f

/** How many degrees the look must turn the head at least. */
private const val TURNED = 10f

/** The example toucan, rigged by a session's graph once it has settled. */
@OptIn(ExperimentalCoroutinesApi::class)
private val toucan: Rig by lazy {
    var rig: Rig? = null
    runTest {
        val document = readDocument(Path.of(System.getProperty("squash.examples"), "toucan.json").readText())
        val session = CharacterSession(document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionRig(session).rig
        runCurrent()
        check(session.graph.settled.value) { "The toucan's graph did not settle" }
        rig = checkNotNull(node.output.value)
    }
    checkNotNull(rig)
}

/** A bird poses by its parts' roles: its feet plant, its wings spread by the wrist and never pin, its head looks and the body curve runs from its tail to its head. */
class ToucanPoseTest {
    private val solver = PoseSolver(toucan)
    private val perched = Pose(planted = setOf(FOOT_L, FOOT_R))

    @Test
    fun theFeetPlantAndTheWingsReachByTheWristWithNoPin() {
        assertTrue(toucan.isUpright)
        val limbs = solver.chains.limbs.associateBy { it.id }
        assertEquals(setOf(FOOT_L, FOOT_R, WING_L, WING_R), limbs.keys)
        assertTrue(limbs.getValue(FOOT_L).plants && limbs.getValue(FOOT_R).plants)
        assertTrue(!limbs.getValue(WING_L).plants && !limbs.getValue(WING_R).plants)
        assertEquals(null, limbs.getValue(WING_L).paw, "a wing has no end part")
        assertEquals(BoneName(WING_L, "wrist"), toucan.bones[limbs.getValue(WING_L).end].name, "a wing is dragged by its wrist")
        val handles = solver.handles(perched)
        assertTrue(WING_L in handles.ends && WING_R in handles.ends, "each wing has its dot")
        assertEquals(setOf(FOOT_L, FOOT_R), handles.pins.keys, "only the feet are pinned")
    }

    @Test
    fun aSpreadWingMovesOnlyItsOwnBonesAndKeepsTheFeet() {
        val wing = assertNotNull(solver.chains.limb(WING_L))
        val shoulder = toucan.bones[wing.upper].pivot
        val target = shoulder + SPREAD.normalized() * ((solver.endOf(perched, wing) - shoulder).length * REACH)
        val spread = solver.reach(perched, wing, target)
        assertTrue(spread.report.missed < LANDS, "the wrist lands, ${spread.report.missed} off")
        assertTrue(spread.pose.bones.isNotEmpty())
        assertEquals(setOf(WING_L), spread.pose.bones.map { it.part }.toSet(), "only the wing's bones turn")
        listOf(FOOT_L, FOOT_R).forEach { id ->
            val foot = assertNotNull(solver.chains.limb(id))
            assertTrue((solver.endOf(perched, foot) - solver.endOf(spread.pose, foot)).length < STILL, "$id stays put")
        }
        assertTrue(solver.endOf(spread.pose, wing).z < solver.endOf(perched, wing).z, "the wing opens out to its side")
    }

    @Test
    fun theLookTurnsTheHead() {
        val look = assertNotNull(solver.chains.look)
        assertEquals(assertNotNull(toucan.indexOf(BoneName(TOUCAN_HEAD, "neck"))), look.head)
        val from = toucan.bones[look.head].pivot
        val looked = solver.look(perched, from + Vec3(0.8f, 0.15f, -0.7f))
        assertTrue(solver.turnedBetween(perched, looked.pose, look.head) > TURNED)
        assertEquals(setOf(FOOT_L, FOOT_R), looked.pose.planted)
    }

    @Test
    fun theBodyCurveRunsFromTheTailThroughTheTorsoToTheHeadAndBendsThem() {
        val bones = assertNotNull(curveBones(toucan))
        assertTrue(bones.behind.isNotEmpty() && bones.behind.all { it.part == TOUCAN_TAIL })
        assertTrue(bones.spine.all { it.part == TOUCAN_TORSO })
        assertTrue(bones.ahead.isNotEmpty() && bones.ahead.all { it.part == TOUCAN_HEAD })
        val chain = bones.spine.mapNotNull { toucan.bone(it)?.pivot } + assertNotNull(toucan.bone(bones.spine.last())).let { it.pivot + it.direction * it.length }
        val last = chain.lastIndex.toFloat()
        val drawn = chain.mapIndexed { i, p -> Vec2(p.x + LEAN * sin(PI * i / last / 2.0).toFloat(), p.y) }
        val stroke = BodyStroke(View.SIDE, listOf(drawn.first()).plus(drawn.zipWithNext().flatMap { (a, b) -> listOf(a + (b - a) * (1f / 3f), a + (b - a) * (2f / 3f), b) }).map { CurvePoint(it.x, it.y) })
        val bent = solver.bendTo(perched, BodyCurve(listOf(stroke), 1f))
        assertTrue(bent.pose.bones.count { it.part == TOUCAN_TORSO } >= 3, "the body bends at the torso's joints")
        assertTrue(bent.pose.bones.any { it.part == TOUCAN_HEAD }, "the neck at the head's base turns with it")
    }
}
