package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `foot` 6 and `paw` 7 are `foot` 5 and `paw` 6 with a `spread` that multiplies their toes' fan angle. */
class ToeSpreadTest {
    private val pairs = listOf("foot" to (5 to 6), "paw" to (6 to 7))

    private fun template(id: String, version: Int) = shipped.parts.single { it.id.value == id && it.version == version }

    private fun points(patches: List<Patch>) = patches.flatMap { patch -> (0 until patch.nu * patch.nv).map { patch.point(it) } }

    /** How far apart sideways the first and last toes' tips stand. */
    private fun tipSpan(patches: List<Patch>): Float {
        val toes = patches.filter { it.name.startsWith("toes:") }
        val tipZ = toes.map { toe -> points(listOf(toe)).maxBy { it.x }.z }
        return tipZ.max() - tipZ.min()
    }

    @Test
    fun theDefaultSpreadBuildsTheToesOfTheVersionBefore() {
        for ((id, versions) in pairs) {
            val before = PartBuilder.build(template(id, versions.first), emptyMap())
            val now = PartBuilder.build(template(id, versions.second), emptyMap())
            assertEquals(points(before), points(now), id)
            assertEquals(emptyList(), TemplateLint.lint(template(id, versions.second)), id)
        }
    }

    @Test
    fun aSmallerSpreadBringsTheToeTipsCloserAndALargerOneApart() {
        for ((id, versions) in pairs) {
            val t = template(id, versions.second)
            val spans = listOf(0.3f, 0.6f, 1f, 1.5f).map { tipSpan(PartBuilder.build(t, mapOf("spread" to it))) }
            assertTrue(spans.zipWithNext().all { (a, b) -> a < b }, "$id tip spans grow with spread: $spans")
        }
    }

    @Test
    fun theMeshStaysCleanAcrossTheRange() {
        for ((id, versions) in pairs) {
            val t = template(id, versions.second)
            for (spread in listOf(0.3f, 1.8f)) for (patch in PartBuilder.build(t, mapOf("spread" to spread))) {
                assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(patch) { 0 }, emptySet()), "$id spread $spread ${patch.name}")
            }
        }
    }
}
