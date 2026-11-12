package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `foot` 4 and `paw` 5 join their toes to the foot's field as `hand` 5 joins its fingers: one surface that rides the toes' joints, with
 * toenails or claws, pads under a paw, a spread of toes for each count, and the same row merged into a mitten or turned off for a shoe.
 */
class JoinedFootPawTest {
    private fun template(id: String, version: Int): PartTemplate = shipped.parts.single { it.id.value == id && it.version == version }

    private val foot = template("foot", 4)
    private val paw = template("paw", 5)

    private fun assertClosed(patches: List<Patch>) = patches.forEach { patch ->
        assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(patch) { 0 }, emptySet()), patch.name)
    }

    private fun farthest(patch: Patch): Float = (0 until patch.nu * patch.nv).maxOf { patch.point(it).x }

    @Test
    fun theFootsToesAreRunsOnItsFieldWithNailsAndTwoJointsEach() {
        assertEquals(emptyList(), TemplateLint.lint(foot))
        val patches = PartBuilder.build(foot)
        assertEquals(listOf("body") + List(5) { "toes:$it" }, patches.map { it.name })
        assertClosed(patches)
        assertTrue(patches.all { it.rides != null }, "every run rides the runs round it")
        assertEquals(listOf("toes:0.1", "toes:0.2"), JointTree(foot, patches.map { it.name }).digits[0].joints.map { it.name })
        val nails = PartBuilder.areas(foot, emptyMap()).filter { it.name.startsWith("nail:") }
        assertEquals(List(5) { "toes:$it" }, nails.map { it.patch })
        assertTrue(nails.all { it.round && it.color == "nails" })
    }

    @Test
    fun aShoeWithNoToesBuildsTheFieldAloneClosedAtItsToe() {
        val shod = PartBuilder.build(foot, mapOf("toes" to 0f, "toeBox" to 1f))
        assertEquals(listOf("body"), shod.map { it.name })
        val body = shod.single()
        assertTrue(!body.openEnds && body.isPole(0) && body.isPole(body.nu - 1), "the field closes round at heel and toe")
        assertClosed(shod)
    }

    @Test
    fun aPawBuildsTheSpreadOfToesItsCountNames() {
        assertEquals(emptyList(), TemplateLint.lint(paw))
        val tips = listOf(3, 4, 5).map { count ->
            val patches = PartBuilder.build(paw, mapOf("toes" to count.toFloat()))
            assertEquals(List(count) { "toes:$it" }, patches.drop(1).map { it.name }, "$count toes, numbered from 0")
            patches[1].point(patches[1].index(patches[1].nu - 1, 0)).z
        }
        assertTrue(tips.zipWithNext().all { (a, b) -> a != b }, "each count builds its own spread")
        assertEquals(listOf("body"), PartBuilder.build(paw, mapOf("toes" to 0f)).map { it.name })
    }

    @Test
    fun aPawCarriesClawsAndPadsThatItsSwitchesTurnOff() {
        val names = PartBuilder.areas(paw, emptyMap()).map { it.name }
        assertTrue("pad" in names && (0 until 4).all { "claw:$it" in names && "bean:$it" in names }, "claws, a main pad and a bean under every toe")
        val bare = PartBuilder.areas(paw, mapOf("claws" to 0f, "pads" to 0f)).map { it.name }
        assertTrue(bare.none { it == "pad" || it.startsWith("claw:") || it.startsWith("bean:") }, "retracted claws and no pads")
        val clawed = PartBuilder.build(paw)
        val retracted = PartBuilder.build(paw, mapOf("claws" to 0f))
        for (k in 1..4) assertTrue(farthest(clawed[k]) > farthest(retracted[k]), "toe ${k - 1}'s claw stands past its tip")
    }

    @Test
    fun aHighMergeBuildsAMittenOfShallowLobesWithRaisedBeans() {
        val mitten = PartBuilder.build(paw, mapOf("merge" to 1.5f, "toeLength" to 0.6f, "claws" to 0f, "beans" to 0.3f))
        assertEquals(5, mitten.size)
        assertClosed(mitten)
    }
}
