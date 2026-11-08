package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Blend
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The fingers into the palm, which keeps its place: not one finger into the next, nor into the webs. */
private val INTO_THE_PALM = listOf(listOf("palm", "fingers"))

/** A test-only variant of [template]: a blend block switched on, filleting the fingers into the palm. */
private fun blendedVariant(template: PartTemplate): PartTemplate =
    template.copy(blocks = template.blocks + Blend(radius = Param.of(0.12f), between = INTO_THE_PALM, still = listOf("palm")))

/** The blend block fillets where a part's patches cross, after the slice build, keeping every grid and every far vertex. */
class ImplicitBlendBuildTest {
    private val hand = shipped.parts.single { it.id.value == "hand" && it.version == 3 }

    private fun Patch.moves(from: Patch): List<Float> = (0 until nu * nv).map { (point(it) - from.point(it)).length }

    @Test
    fun aBlendedHandKeepsItsGridsAndMovesOnlyNearItsCrossings() {
        val variant = blendedVariant(hand)
        assertEquals(emptyList(), TemplateLint.lint(variant))
        val plain = PartBuilder.build(variant, PartData())
        val blended = PartBuilder.blended(variant, PartData())
        assertEquals(plain.map { Triple(it.name, it.nu, it.nv) }, blended.map { Triple(it.name, it.nu, it.nv) })
        assertEquals(plain.map { it.slices }, blended.map { it.slices })
        val mesh = PartBuilder.mesh("hand", variant, blended)
        for (patch in mesh.patches) assertEquals(emptyList(), MeshInvariants.check(patch, PartBuilder.openRings(variant, patch.patch)), patch.patch.name)
        val moves = blended.zip(plain).associate { (after, before) -> after.name to after.moves(before) }
        assertTrue(moves.filterKeys { !it.startsWith("fingers:") }.values.all { m -> m.all { it == 0f } }, "the palm and the webs keep their place")
        val finger = moves.getValue("fingers:2")
        assertTrue(finger.any { it > 0f }, "the middle finger's root moves")
        val tipRing = blended.single { it.name == "fingers:2" }.let { it.nu - 1 }
        assertTrue(finger.drop(tipRing * 12).all { it == 0f }, "its tip stays")
    }

    @Test
    fun withoutTheBlockNothingMoves() {
        assertEquals(PartBuilder.build(hand, PartData()), PartBuilder.blended(hand, PartData()))
    }
}
