package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CreaseMarks
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.CharacterCreases
import dev.chieppa.squash.document.ClothCrease
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetCharacterCreases
import dev.chieppa.squash.document.SetGarmentCrease
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.rig.StackFixture.leg
import dev.chieppa.squash.rig.StackFixture.stackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** How far the sweater runs down the leg, past its knee. */
private const val PAST_KNEE = 0.9f

/** The knee's bend, in degrees. */
private const val BENT = 110f

/**
 * Creases on a torso, head and leg under a sweater: off until the character turns them on; at rest a faint ink line and no shade band at
 * the knee; bent, a shade band and longer ink, and a fold raised on the cloth; a garment's strengths pose again without growing its cloth again.
 */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceCreasesTest {
    private val sweater = GarmentId("sweater")

    @Test
    fun creasesDeepenWithABendAndAStrengthEditBuildsNothing() = runTest {
        val session = stackSession(legTo = PAST_KNEE)
        val pose = SessionPose(session)
        runCurrent()
        assertNull(posed(pose).creases, "creases are off by default")

        session.send(SetCharacterCreases(CharacterCreases(skin = 1f, cloth = 1f)))
        val rest = assertNotNull(posed(pose).creases)
        val restBody = assertNotNull(rest.body)
        assertTrue(restBody.lines.isNotEmpty(), "a faint line at rest")
        assertTrue(restBody.shade.max() < 0.5f, "no band at rest: ${restBody.shade.max()}")
        assertNotNull(rest.garments[sweater])

        session.send(SetPose(Pose(listOf(BonePose(leg.id, "knee", BENT)))))
        val bent = posed(pose)
        val bentBody = assertNotNull(bent.creases?.body)
        assertTrue(bentBody.shade.max() >= 0.5f, "a band once bent: ${bentBody.shade.max()}")
        assertTrue(inkLength(bentBody) > inkLength(restBody), "ink lengthens with the bend: ${inkLength(restBody)} then ${inkLength(bentBody)}")

        val cloth = assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.single().surface
        val folded = garmentPositions(bent)
        ClothCrease.entries.forEach { session.send(SetGarmentCrease(sweater, it, 0f)) }
        val flat = posed(pose)
        assertSame(cloth, assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.single().surface, "a strength edit grows no cloth")
        val rise = folded.indices.maxOf { kotlin.math.abs(folded[it] - garmentPositions(flat)[it]) }
        assertTrue(rise > 0f, "the bent knee raises a fold on the cloth")
    }

    private fun TestScope.posed(pose: SessionPose): PosedSurface {
        runCurrent()
        return assertNotNull(pose.surface.posed.output.value?.posed)
    }

    private fun garmentPositions(posed: PosedSurface): FloatArray = assertNotNull(posed.dress).garments.single().surface.positions

    private fun inkLength(marks: CreaseMarks): Float = marks.lines.sumOf { line ->
        line.points.zipWithNext { a, b -> ((b.position - a.position).length * minOf(1f, line.weights.max() * 4f)).toDouble() }.sum()
    }.toFloat()
}
