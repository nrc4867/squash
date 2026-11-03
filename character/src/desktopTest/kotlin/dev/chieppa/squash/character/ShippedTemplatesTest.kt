package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.golden.GoldenFixture
import java.nio.file.Path
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val RANDOM_BUILDS = 25

class ShippedTemplatesTest {
    @Test
    fun theSixStarterTemplatesAndThePlanLoadAndLint() {
        assertEquals(listOf("ear", "head", "leg", "paw", "tail", "torso"), shipped.parts.map { it.id.value })
        assertEquals(listOf("canine-quadruped"), shipped.plans.map { it.id.value })
    }

    @Test
    fun theBoardsBoneCountsHold() {
        fun bones(id: String) = shipped.parts.first { it.id.value == id }.blocks.flatMap { it.joints() }.size - 1
        assertEquals(5, bones("torso"))
        assertEquals(6, bones("tail"))
    }

    @Test
    fun everyTemplateBuildsWatertightExceptItsOpenEnd() {
        shipped.parts.forEach { assertBuildHolds(it, emptyMap()) }
    }

    @Test
    fun randomParametersInsideTheirRangesBuild() {
        val random = Random(1)
        for (template in shipped.parts) {
            repeat(RANDOM_BUILDS) {
                val params = template.params.filterValues { it.min != null && it.max != null }.mapValues { (_, spec) ->
                    spec.min!! + random.nextFloat() * (spec.max!! - spec.min)
                }
                assertBuildHolds(template, params)
            }
        }
    }

    @Test
    fun theOpenEndFacesTheSocket() {
        val leg = shipped.parts.first { it.id.value == "leg" }
        assertEquals(End.START, leg.openEnd)
        val patch = PartBuilder.build(leg).single()
        assertEquals(setOf(0), PartBuilder.openRings(leg, patch))
    }

    @Test
    fun aFlagSwitchesItsBlocksOff() {
        val paw = shipped.parts.first { it.id.value == "paw" }
        val on = PartBuilder.build(paw)
        val off = PartBuilder.build(paw, mapOf("pads" to 0f))
        assertNotEquals(on, off)
        assertTrue(on.any { it.name.startsWith("pad:") })
        assertTrue(off.none { it.name.startsWith("pad:") })
        assertEquals(on.filterNot { it.name.startsWith("pad:") }, off)
    }

    @Test
    fun defaultBuildsMatchTheirFixtures() {
        assertEquals(listOf("ear", "head", "leg", "paw", "tail", "torso"), shipped.parts.map { it.id.value })
        for (template in shipped.parts) {
            val mesh = PartBuilder.mesh(template.id.value, template, PartBuilder.build(template))
            val text = mesh.patches.joinToString("") { patch ->
                val xs = (0 until patch.vertexCount).map { patch.patch.point(it) }
                "%s vertices=%d triangles=%d x=%.3f..%.3f y=%.3f..%.3f z=%.3f..%.3f\n".format(
                    Locale.ROOT,
                    patch.patch.name,
                    patch.vertexCount,
                    patch.triangleCount,
                    xs.minOf { it.x }, xs.maxOf { it.x },
                    xs.minOf { it.y }, xs.maxOf { it.y },
                    xs.minOf { it.z }, xs.maxOf { it.z },
                )
            }
            GoldenFixture.check(Path.of("src/desktopTest/golden/${template.id.value}-${template.version}.txt"), text, "character")
        }
    }

    private fun assertBuildHolds(template: PartTemplate, params: Map<String, Float>) {
        val patches = PartBuilder.build(template, params)
        assertTrue(patches.isNotEmpty(), template.id.value)
        val mesh = PartBuilder.mesh(template.id.value, template, patches)
        for (patch in mesh.patches) {
            if (patch.patch.name == BODY_PATCH) {
                assertEquals(template.topology.rings, patch.patch.nu)
                assertEquals(template.topology.segments, patch.patch.nv)
            }
            val problems = MeshInvariants.check(patch, PartBuilder.openRings(template, patch.patch))
            assertEquals(emptyList(), problems, "${template.id.value} with $params")
        }
    }
}
