package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.View
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How far into the play bow the fennec's front knees fold far enough to keep the front paws where they stood. */
private const val FOLDING_SHARE = 0.75f

/** How near a curve drawn head first bends the chain to the same curve drawn tail first, in degrees. */
private const val HEAD_FIRST_DEGREES = 0.05f

/** The play bow three quarters of the way, which every leg can follow. */
private val THREE_QUARTER_BOW: Pose = Pose(BOW.bones.map { it.copy(degrees = it.degrees * FOLDING_SHARE) })

/**
 * The body curve's solve chained with the pin solve, on the fennec standing on all four paws. A paw is held when it ends within
 * [LIMB_TOLERANCE] of where it stood.
 */
class BodyCurvePoseTest {
    private val solver = PoseSolver(fennec.rig)
    private val standing = Pose(planted = PAWS)
    private val bow by lazy { BodyCurve(listOf(strokeOver(THREE_QUARTER_BOW, View.SIDE))) }

    private fun moved(from: Pose, to: Pose): Map<String, Float> = solver.chains.limbs.associate { it.id.value to (solver.endOf(to, it) - solver.endOf(from, it)).length }

    @Test
    fun theBowHoldsEveryPlantedPawWhereItStood() {
        val bare = solveBodyCurve(solver.rig, standing, bow)
        val solved = solver.bendTo(standing, bow)
        val loose = moved(standing, bare.pose)
        val held = moved(standing, solved.pose)
        println("three quarters of the bow alone moves the paws ${loose.mapValues { "%.4f".format(it.value) }}; held, they move ${held.mapValues { "%.6f".format(it.value) }}")
        assertTrue(loose.values.max() > LIMB_TOLERANCE, "the bow alone moves a paw")
        for ((id, d) in held) assertTrue(d <= LIMB_TOLERANCE, "$id moved $d")
        assertTrue(solved.report.slid.isEmpty(), "every paw comes back: ${solved.report.slid}")
        for (bone in bare.pose.bones) assertEquals(bone, solved.pose.of(bone.part, bone.joint), "the pin solve leaves ${bone.part.value}/${bone.joint} as bent")
        assertEquals(bow, solved.pose.curve)
    }

    @Test
    fun theWholeBowHoldsTheBackPawsAndSaysHowFarTheFrontOnesStayOff() {
        val solved = solver.bendTo(standing, BodyCurve(listOf(PLAY_BOW)))
        val held = moved(standing, solved.pose)
        println("the whole bow: paws move ${held.mapValues { "%.4f".format(it.value) }}, reported off ${solved.report.slid.mapValues { "%.4f".format(it.value) }}")
        for ((id, d) in held.filterKeys { it.startsWith("back") }) assertTrue(d <= LIMB_TOLERANCE, "$id moved $d")
        for ((id, d) in solved.report.slid) assertEquals(held.getValue(id.value), d, LIMB_TOLERANCE)
    }

    @Test
    fun aPawThatIsNotPlantedRidesAlong() {
        val three = standing.withPlanted(PAWS.first(), false)
        val bare = solveBodyCurve(solver.rig, three, bow)
        val solved = solver.bendTo(three, bow)
        val limb = checkNotNull(solver.chains.limb(PAWS.first()))
        assertEquals(0f, (solver.endOf(solved.pose, limb) - solver.endOf(bare.pose, limb)).length, 1e-6f)
    }

    @Test
    fun clearingTheCurveRestsTheChainAndHoldsThePaws() {
        val bowed = solver.bendTo(standing, bow).pose
        val cleared = solver.clearCurve(bowed).pose
        assertEquals(null, cleared.curve)
        val chain = setOf(TORSO, TAIL, HEAD)
        assertTrue(cleared.bones.none { it.part in chain && !it.isRest }, "the chain rests: ${cleared.bones.filter { it.part in chain }}")
        for ((id, d) in moved(bowed, cleared)) assertTrue(d <= LIMB_TOLERANCE, "$id moved $d")
    }

    @Test
    fun theCurvesBonesAreTheTailTheTorsoAndTheHeadsNeck() {
        val bones = checkNotNull(curveBones(solver.rig))
        assertEquals(listOf("root", "tail1", "tail2", "tail3", "tail4", "tail5"), bones.behind.map { it.joint })
        assertEquals(setOf(TORSO), bones.spine.map { it.part }.toSet())
        assertEquals(BoneName(HEAD, "neck"), bones.ahead.first())
        assertTrue(BoneName(TORSO, "withers") in bones.stretched && BoneName(TAIL, "tip") in bones.stretched)
        val bends = checkNotNull(chainBends(solver.rig, BOW))
        assertEquals(18f, bends.spine, 1e-3f)
        assertEquals(-28f, checkNotNull(bends.neck), 1e-3f)
        assertEquals(-37f, checkNotNull(bends.tail), 1e-3f)
        println("the bow's head points ${bends.head} degrees from rest")
    }

    @Test
    fun aCurveDrawnHeadFirstBendsAsOneDrawnTailFirst() {
        val tailFirst = solver.bendTo(standing, BodyCurve(bow.strokes + SWAY)).pose
        val reversed = BodyCurve((bow.strokes + SWAY).map { it.copy(points = it.points.reversed()) })
        val headFirst = solver.bendTo(standing, reversed).pose
        val most = tailFirst.bones.maxOf { bone ->
            val other = headFirst.of(bone.part, bone.joint) ?: BonePose(bone.part, bone.joint)
            maxOf(abs(bone.degrees - other.degrees), abs(bone.lean - other.lean))
        }
        println("drawn head first, the bend differs by $most degrees at most")
        assertTrue(most <= HEAD_FIRST_DEGREES, "drawn head first, a bone differs by $most")
    }
}
