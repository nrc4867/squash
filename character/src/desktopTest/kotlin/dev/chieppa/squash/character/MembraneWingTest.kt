package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.MeshInvariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How near, in degrees, every finger's direction at a whole curl lies to the others'. */
private const val CLOSED = 6f

/** `wing-membrane`: four long fingers webbed to each other and the last to the arm, folding along their own paths. */
class MembraneWingTest {
    private val wing = assertNotNull(shipped.parts.singleOrNull { it.id == TemplateId("wing-membrane") })

    @Test
    fun itLintsCleanAndIsAWing() {
        assertEquals(emptyList(), TemplateLint.lint(wing))
        assertEquals(Role.WING, wing.role)
    }

    @Test
    fun itWebsEachFingerToTheNextAndTheLastToTheArm() {
        val patches = PartBuilder.build(wing)
        assertEquals(listOf("web:0", "web:1", "web:2", "skin:3"), patches.filter { it.spanned != null }.map { it.name })
        val skin = assertNotNull(patches.single { it.name == "skin:3" }.spanned)
        assertEquals("fingers:3" to BODY_PATCH, skin.first to skin.second)
        assertTrue(skin.secondUs.max() <= 0.38f + 1e-4f && skin.secondUs.min() < 0.1f, "the skin runs along the arm from the wrist to the shoulder")
        val mesh = PartBuilder.mesh("wing", wing, patches)
        mesh.patches.filter { it.patch.spanned != null }.forEach { web ->
            assertEquals(emptyList(), MeshInvariants.check(web, emptySet()), web.patch.name)
            assertTrue(MeshInvariants.signedVolume(web) > 0f, "${web.patch.name} faces out")
        }
    }

    @Test
    fun eachFingerHangsFromTheWristOnOneJointThatClosesTheFanAtItsLimit() {
        val tree = JointTree(wing, PartBuilder.build(wing).map { it.name })
        assertEquals(listOf("shoulder", "elbow", "wrist", "tip"), tree.chain.map { it.name })
        assertEquals(4, tree.digits.size)
        tree.digits.forEachIndexed { i, digit ->
            assertEquals(1, digit.joints.size)
            assertEquals(tree.chain.indexOfFirst { it.name == "wrist" }, tree.rootOf(i), "finger $i branches from the wrist")
        }
        val angles = listOf(-12f, 18f, 42f, 64f)
        val closed = tree.digits.mapIndexed { i, digit -> angles[i] + assertNotNull(digit.joints.single().limits).max }
        assertTrue(closed.max() - closed.min() < CLOSED, "at a whole curl every finger points the same way: $closed")
    }
}
