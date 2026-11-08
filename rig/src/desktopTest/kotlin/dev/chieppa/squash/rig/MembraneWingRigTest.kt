package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val WING_L = PartId("wing-l")
private const val SKIN = "skin:3"

/** How much nearer each other the four fingertips come at a whole curl at least, as a share of their spread at rest. */
private const val FOLDS = 0.5f

/** The dragon's membrane wings: the skin rides the last finger and the arm, and a whole curl folds the fan shut. */
class MembraneWingRigTest {
    private val dragon get() = dragonRig

    private fun point(positions: FloatArray, vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

    @Test
    fun theSkinFollowsTheLastFingerAndTheArm() {
        val wing = assertNotNull(dragon.part(WING_L))
        val skin = wing.vertices.indices.filter { wing.vertices[it].patch == SKIN }
        assertTrue(skin.isNotEmpty())
        val followed = skin.flatMap { v -> wing.weights.of(v).map { (bone, _) -> dragon.bones[bone].name.joint } }.toSet()
        assertTrue("fingers:3.1" in followed, "the skin follows its finger: $followed")
        assertTrue("shoulder" in followed && "elbow" in followed, "the skin follows the arm along its length: $followed")
    }

    @Test
    fun aWholeCurlFoldsTheFingersTogether() {
        val wing = assertNotNull(dragon.part(WING_L))
        val tips = (0..3).map { i -> wing.vertices.indices.filter { wing.vertices[it].patch == "fingers:$i" }.maxBy { wing.vertices[it].u } }
        fun spread(positions: FloatArray) = tips.zipWithNext { a, b -> (point(positions, a) - point(positions, b)).length }.sum()
        val folded = dragon.posed(dragon.curled(Pose(), listOf(WING_L), 1f)).parts.getValue(WING_L)
        assertEquals(1f, assertNotNull(dragon.curlOf(dragon.curled(Pose(), listOf(WING_L), 1f), WING_L)), 1e-4f)
        assertTrue(spread(folded) < spread(wing.rest) * FOLDS, "the fan closes: ${spread(wing.rest)} to ${spread(folded)}")
    }
}
