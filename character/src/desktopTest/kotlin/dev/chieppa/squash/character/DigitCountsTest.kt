package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.DigitSegment
import dev.chieppa.squash.character.block.Digits
import dev.chieppa.squash.character.build.DigitPatches
import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `hand` 7 builds two to five digits with or without a thumb, `foot` 7 and `paw` 8 one to five toes, each count its own layout; a hand's
 * fingers fan across the palm with `spread`, and every digit goes by a patch and a number a socket can hold.
 */
class DigitCountsTest {
    private fun template(id: String, version: Int): PartTemplate = shipped.parts.single { it.id.value == id && it.version == version }

    private val hand = template("hand", 7)
    private val foot = template("foot", 7)
    private val paw = template("paw", 8)

    private fun digitsOf(template: PartTemplate, params: Map<String, Float>) =
        DigitPatches.builtPatches(template, params).filter { it.contains(':') }.sorted()

    @Test
    fun theNewVersionsLintClean() {
        listOf(hand, foot, paw).forEach { assertEquals(emptyList(), TemplateLint.lint(it), it.id.value) }
    }

    @Test
    fun aHandsDigitCountHoldsItsThumbUnlessTheThumbIsOff() {
        assertEquals(List(4) { "fingers:$it" } + "fingers:thumb", digitsOf(hand, emptyMap()))
        assertEquals(listOf("fingers:0", "fingers:1", "fingers:2", "fingers:thumb"), digitsOf(hand, mapOf("digits" to 4f)))
        assertEquals(listOf("fingers:0", "fingers:1", "fingers:thumb"), digitsOf(hand, mapOf("digits" to 3f)))
        assertEquals(listOf("fingers:0", "fingers:thumb"), digitsOf(hand, mapOf("digits" to 2f)))
        assertEquals(List(4) { "fingers:$it" }, digitsOf(hand, mapOf("digits" to 4f, "thumb" to 0f)))
        assertEquals(List(5) { "fingers:$it" }, digitsOf(hand, mapOf("thumb" to 0f)))
    }

    @Test
    fun aFootAndAPawBuildOneToFiveToes() {
        for (count in 0..5) {
            assertEquals(List(count) { "toes:$it" }, digitsOf(foot, mapOf("toes" to count.toFloat())), "foot, $count toes")
            assertEquals(List(count) { "toes:$it" }, digitsOf(paw, mapOf("toes" to count.toFloat())), "paw, $count toes")
        }
    }

    @Test
    fun threeFingersAreLaidOutForThreeNotLeftFromFive() {
        val row = hand.blocks.filterIsInstance<Digits>().single()
        fun fingers(digits: Float) = row.builtSegments(hand.defaults + mapOf("digits" to digits)).filter { it.second.name == null }.map { it.second }
        val three = fingers(4f)
        val four = fingers(5f)
        assertTrue(three.minOf { it.radius } > four.maxOf { it.radius }, "three fingers are each thicker than any of four")
        val threeAcross = three.maxOf { it.root[1] } - three.minOf { it.root[1] }
        val fourAcross = four.maxOf { it.root[1] } - four.minOf { it.root[1] }
        assertTrue(threeAcross > fourAcross * 0.8f, "three fingers still span the palm: $threeAcross against $fourAcross")
    }

    @Test
    fun spreadFansAHandsFingersAcrossThePalmNotThroughIt() {
        val finger = DigitSegment(listOf(0.4f, 0.1f, 0f), listOf(0.98f, 0.17f, 0f), 0.3f, 0.04f)
        val wide = finger.direction(2f, across = 1)
        assertEquals(0f, wide.z, 1e-6f)
        assertEquals(2f * atan2(0.17f, 0.98f), atan2(wide.y, wide.x), 1e-4f)
        val toe = DigitSegment(listOf(0.6f, -0.08f, 0.1f), listOf(0.95f, -0.1f, 0.2f), 0.15f, 0.06f)
        assertEquals(toe.direction(), toe.direction(1f))
        assertEquals(toe.direction(-0.1f, across = 2).y, toe.direction(-0.1f).y)
    }

    @Test
    fun theDefaultHandsFingersStandApartAtTheirTips() {
        val patches = PartBuilder.build(hand, mapOf("digits" to 4f))
        assertEquals(listOf("body", "fingers:0", "fingers:1", "fingers:2", "fingers:thumb"), patches.map { it.name })
        val tree = JointTree(hand, patches.map { it.name })
        assertEquals(listOf("fingers:thumb.1", "fingers:thumb.2", "fingers:thumb.3"), tree.digits.last().joints.map { it.name })
        val tips = patches.filter { it.name.startsWith("fingers:") && it.name != "fingers:thumb" }.map(::tip)
        val radius = hand.blocks.filterIsInstance<Digits>().single().digits.filter { 4 in it.counts.orEmpty() }.maxOf { it.radius } * hand.axis.length
        tips.zipWithNext().forEach { (a, b) -> assertTrue((a - b).length > radius * 2f, "tips ${(a - b).length} apart, more than a finger's width $radius") }
    }

    @Test
    fun aDigitGoesByANumberEitherWay() {
        assertEquals("fingers:2", DigitPatches.patchOf(hand, 2))
        assertEquals("fingers:thumb", DigitPatches.patchOf(hand, -1))
        assertEquals(-1, DigitPatches.digitOf(hand, "fingers:thumb"))
        assertEquals(0, DigitPatches.digitOf(paw, "toes:0"))
        assertNull(DigitPatches.digitOf(hand, "body"))
        assertNull(DigitPatches.digitOf(hand, "fingers:pinky"))
        assertNull(DigitPatches.patchOf(template("blob", 1), 0))
    }

    /** The middle of [patch]'s last open ring before its tip closes. */
    private fun tip(patch: Patch): Vec3 {
        val ring = patch.nu - 3
        return (0 until patch.nv).fold(Vec3.ZERO) { sum, s -> sum + patch.point(patch.index(ring, s)) } * (1f / patch.nv)
    }
}
