package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `hand` 4 and `foot` 3 build a swept body from a smooth union of solids and separate digits with named joints: every patch is a
 * closed grid with slices, fingertips and toes stand apart, and the foot's shoe parameters close a toe box and lay the forefoot flat.
 */
class BlendedHandFootTest {
    private fun template(id: String, version: Int): PartTemplate = shipped.parts.single { it.id.value == id && it.version == version }

    private fun tip(patch: Patch): Vec3 = patch.point(patch.index(patch.nu - 1, 0))

    @Test
    fun theHandIsABlendedPalmAndFiveDigitsWithNamedJoints() {
        val hand = template("hand", 4)
        assertEquals(emptyList(), TemplateLint.lint(hand))
        val patches = PartBuilder.build(hand)
        assertEquals(listOf("body", "fingers:0", "fingers:1", "fingers:2", "fingers:3", "fingers:4"), patches.map { it.name })
        patches.forEach { patch ->
            assertTrue(patch.slices != null && patch.isPole(0) && patch.isPole(patch.nu - 1), "${patch.name} closes at both ends")
            assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(patch) { 0 }, emptySet()), patch.name)
        }
        val tree = JointTree(hand, patches.map { it.name })
        assertEquals(listOf("wrist"), tree.chain.map { it.name })
        assertEquals(listOf("fingers:1.1", "fingers:1.2", "fingers:1.3"), tree.digits[1].joints.map { it.name })
        val tips = patches.drop(1).take(4).map(::tip)
        val radius = 0.0085f / 0.22f * hand.axis.length
        tips.zipWithNext().forEach { (a, b) -> assertTrue((a - b).length > 2.5f * radius, "neighbouring fingertips stand apart") }
    }

    @Test
    fun theFootIsABlendedHeelArchAndBallWithFiveToesAndAFlatSole() {
        val foot = template("foot", 3)
        assertEquals(emptyList(), TemplateLint.lint(foot))
        val patches = PartBuilder.build(foot)
        assertEquals(listOf("body") + List(5) { "toes:$it" }, patches.map { it.name })
        val body = patches.first()
        val lowest = (0 until body.nu * body.nv).minOf { body.point(it).y }
        val ground = -0.069f / 0.27f * foot.axis.length
        assertEquals(ground, lowest, 0.008f * foot.axis.length / 0.27f)
        assertEquals(listOf("body"), PartBuilder.build(foot, mapOf("toes" to 0f)).map { it.name })
    }

    @Test
    fun aPitchedShoeLaysItsToeBoxFlat() {
        val foot = template("foot", 3)
        val flat = PartBuilder.build(foot, mapOf("toes" to 0f, "toeBox" to 1f)).first()
        val bent = PartBuilder.build(foot, mapOf("toes" to 0f, "toeBox" to 1f, "pitch" to 24f)).first()
        assertTrue(tip(bent).y > tip(flat).y + 0.02f * foot.axis.length, "the toe box turns up about the toe joint")
        assertTrue(tip(flat).x > PartBuilder.build(foot, mapOf("toes" to 0f)).first().let(::tip).x, "the toe box reaches past the ball")
    }
}
