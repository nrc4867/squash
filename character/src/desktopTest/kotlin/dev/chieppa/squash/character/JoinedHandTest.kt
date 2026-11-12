package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.DigitNail
import dev.chieppa.squash.character.block.Digits
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `hand` 5 joins its fingers to the palm's field: the palm and each finger are runs on one surface, buried where another shows it, every
 * vertex rides the bones of the runs round it, each finger carries a nail, and the same row grows claws or merges into a mitten.
 */
class JoinedHandTest {
    private val hand: PartTemplate = shipped.parts.single { it.id.value == "hand" && it.version == 5 }

    private fun withRow(change: (Digits) -> Digits) = hand.copy(blocks = hand.blocks.map { if (it is Digits) change(it) else it })

    private fun farthest(patch: Patch): Float = (0 until patch.nu * patch.nv).maxOf { patch.point(it).length }

    @Test
    fun thePalmAndFingersAreRunsOnOneSurfaceThatRideEachOther() {
        assertEquals(emptyList(), TemplateLint.lint(hand))
        val patches = PartBuilder.build(hand)
        assertEquals(listOf("body") + List(5) { "fingers:$it" }, patches.map { it.name })
        patches.forEach { patch ->
            assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(patch) { 0 }, emptySet()), patch.name)
            val rides = requireNotNull(patch.rides) { "${patch.name} rides the runs round it" }
            assertEquals(patches.map { it.name }, rides.patches)
            for (vertex in 0 until rides.vertexCount) {
                val total = rides.patches.indices.sumOf { rides.share(vertex, it).toDouble() }
                assertTrue(abs(total - 1.0) < 1e-4, "${patch.name} vertex $vertex shares sum to one")
            }
        }
        assertTrue(patches.drop(1).all { it.buried != null }, "each finger's root lies buried in the palm")
    }

    @Test
    fun eachFingerCarriesARoundNail() {
        val nails = hand.blocks.filterIsInstance<Digits>().single().areas()
        assertEquals(List(5) { "nail:$it" }, nails.map { it.name })
        assertEquals(List(5) { "fingers:$it" }, nails.map { it.patch })
        assertTrue(nails.all { it.round && it.color == "nails" })
    }

    @Test
    fun clawsStandOutPastTheTipsAndAreFilledToThePoint() {
        val clawed = withRow { it.copy(nail = it.nail?.copy(claw = Param.of(0.3f), curl = Param.of(0.3f))) }
        val plain = PartBuilder.build(hand)
        val built = PartBuilder.build(clawed)
        for (k in 1..4) assertTrue(farthest(built[k]) > farthest(plain[k]), "finger ${k - 1}'s claw reaches past its tip")
        assertTrue(clawed.blocks.filterIsInstance<Digits>().single().areas().none { it.round })
    }

    @Test
    fun aHighMergeBuildsAMittenOfShallowLobes() {
        val mitten = withRow { it.copy(merge = Param.of(1.5f)) }
        assertEquals(emptyList(), TemplateLint.lint(mitten))
        PartBuilder.build(mitten).forEach { patch ->
            assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(patch) { 0 }, emptySet()), patch.name)
        }
    }
}
