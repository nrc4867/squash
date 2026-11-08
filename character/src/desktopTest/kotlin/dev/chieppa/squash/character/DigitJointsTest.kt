package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.DigitJoints
import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A toe row's digits carry chains of joints that branch from the part's own, and the mesh tags each digit with its own. */
class DigitJointsTest {
    private fun template(id: String, version: Int): PartTemplate = shipped.parts.single { it.id.value == id && it.version == version }

    private fun tree(template: PartTemplate, params: Map<String, Float> = emptyMap()) =
        JointTree(template, PartBuilder.build(template, params).map { it.name })

    @Test
    fun aHandHasThreeJointsAFingerAndTwoForTheThumb() {
        val tree = tree(template("hand", 2))
        assertEquals(listOf("wrist"), tree.chain.map { it.name })
        assertEquals(listOf(2, 3, 3, 3, 3), tree.digits.map { it.joints.size })
        assertEquals(listOf("fingers:1.1", "fingers:1.2", "fingers:1.3"), tree.digits[1].joints.map { it.name })
        assertTrue(tree.digits.all { tree.rootOf(tree.digits.indexOf(it)) == 0 }, "every digit branches from the wrist")
        assertEquals(1 + 2 + 4 * 3, tree.specs.size)
        assertEquals(emptyList(), TemplateLint.lint(template("hand", 2)))
    }

    @Test
    fun theFirstVersionsKeepNoDigitJoints() {
        listOf("hand", "foot-bird", "paw").forEach { id ->
            assertEquals(emptyList(), tree(template(id, 1), mapOf("toes" to 4f)).digits, "$id 1 has no digit joints")
        }
    }

    @Test
    fun theBirdFootsHindToeIsTurnedBackWithTwoJoints() {
        val digits = tree(template("foot-bird", 2)).digits
        assertEquals(listOf(false, false, false, true), digits.map { it.back })
        assertEquals(listOf(3, 3, 3, 2), digits.map { it.joints.size })
    }

    @Test
    fun aPawHasAChainPerToeItBuilds() {
        val paw = template("paw", 2)
        assertEquals(emptyList(), tree(paw).digits, "no toes, no chains")
        assertEquals(List(4) { 2 }, tree(paw, mapOf("toes" to 4f)).digits.map { it.joints.size })
    }

    @Test
    fun theMeshTagsEachDigitWithItsOwnJoints() {
        val hand = template("hand", 2)
        val patches = PartBuilder.build(hand)
        val tree = JointTree(hand, patches.map { it.name })
        val mesh = PartBuilder.mesh("hand", hand, patches).patches.associateBy { it.patch.name }
        assertEquals(setOf(0), mesh.getValue("palm").bones.toSet(), "the palm follows the wrist alone")
        val index = mesh.getValue("fingers:1").bones.toSet()
        assertEquals(setOf(0) + (0 until 3).map { tree.indexOf(1, it) }, index, "the index finger follows the wrist to its knuckle, then its own three")
        val thumb = mesh.getValue("fingers:0").bones.toSet()
        assertTrue(thumb.none { it in index - 0 }, "no finger takes another's bones")
    }

    @Test
    fun jointSharesMustAscendInsideTheDigit() {
        val hand = template("hand", 2)
        val row = hand.blocks.filterIsInstance<ToeRow>().single()
        val broken = hand.copy(blocks = hand.blocks.map { if (it === row) row.copy(joints = DigitJoints(listOf(0.5f, 0.2f, 1.4f))) else it })
        val findings = TemplateLint.lint(broken).map { it.message }
        assertTrue(findings.any { "ascending" in it }, "out of order: $findings")
        assertTrue(findings.size >= 2, "a share past 1 is found too: $findings")
    }
}
