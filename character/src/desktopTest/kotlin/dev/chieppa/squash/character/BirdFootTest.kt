package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `foot-bird` 4 is a field with its toes joined to it as a paw's are: two to four toes forward and a hind toe a flag turns on, their
 * length, thickness and spread, knuckles, webs, talons and pads each an option that changes the mesh.
 */
class BirdFootTest {
    private val foot = shipped.parts.single { it.id.value == "foot-bird" && it.version == 4 }

    private fun build(params: Map<String, Float> = emptyMap()) = PartBuilder.build(foot, params)

    private fun points(patches: List<Patch>) = patches.flatMap { patch -> (0 until patch.nu * patch.nv).map { patch.point(it) } }

    @Test
    fun itLintsAndBuildsThreeToesForwardAndAHindToeClosed() {
        assertEquals(emptyList(), TemplateLint.lint(foot))
        val patches = build()
        assertEquals(listOf("body", "toes:0", "toes:1", "toes:2", "toes:back"), patches.map { it.name })
        patches.forEach { patch -> assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(patch) { 0 }, emptySet()), patch.name) }
        val hind = patches.last()
        assertTrue(points(listOf(hind)).minOf { it.x } < points(patches.dropLast(1)).minOf { it.x }, "the hind toe reaches back past the heel")
    }

    @Test
    fun theToeCountAndTheHindToeChooseWhichToesAreBuiltAndRigged() {
        for (toes in listOf(2, 3, 4)) for (hallux in listOf(0f, 1f)) {
            val patches = build(mapOf("toes" to toes.toFloat(), "hallux" to hallux))
            val expected = List(toes) { "toes:$it" } + if (hallux == 1f) listOf("toes:back") else emptyList()
            assertEquals(expected, patches.drop(1).map { it.name }, "$toes toes, hind toe $hallux")
            val chains = JointTree(foot, patches.map { it.name }).digits
            assertEquals(expected, chains.map { it.patch }, "every toe built has its joints")
            assertEquals(hallux == 1f, chains.lastOrNull()?.back == true, "the hind toe curls the other way")
        }
        val names = PartBuilder.areas(foot, emptyMap()).map { it.name }
        assertTrue("claw:back" in names && "pad:back" in names && "claw:0" in names, "talons and pads on every toe, the hind toe's named for it")
    }

    @Test
    fun everyOptionChangesTheMesh() {
        val plain = points(build())
        val options = mapOf(
            "toeLength" to 1.5f, "toeThickness" to 1.4f, "spread" to 1.6f, "knuckle" to 0.5f, "merge" to 1.2f, "web" to 0.12f,
            "claw" to 0.9f, "clawCurl" to -0.4f, "claws" to 0f, "padSize" to 0.7f, "width" to 0.2f,
        )
        for ((name, value) in options) assertNotEquals(plain, points(build(mapOf(name to value))), "$name changes the foot")
    }
}
