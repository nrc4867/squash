package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.golden.GoldenFixture
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val RANDOM_BUILDS = 25

class ShippedTemplatesTest {
    @Test
    fun theSixStarterTemplatesAndThePlanLoadAndLint() {
        assertEquals(listOf("ear", "head", "leg", "paw", "tail", "torso"), shipped.parts.map { it.id.value }.distinct().sorted())
        assertEquals(listOf("canine-quadruped"), shipped.plans.map { it.id.value })
    }

    @Test
    fun everyVersionShipsSoASavedCharacterKeepsBuilding() {
        val versions = shipped.parts.groupBy({ it.id.value }, { it.version }).mapValues { (_, it) -> it.sorted() }
        assertEquals(mapOf("ear" to listOf(1), "head" to listOf(1, 2), "leg" to listOf(1), "paw" to listOf(1), "tail" to listOf(1, 2), "torso" to listOf(1, 2)), versions)
    }

    @Test
    fun theSpineAndTailBendSidewaysAndTheHeadTurnsAtItsBase() {
        fun joints(id: String, version: Int) = shipped.parts.single { it.id.value == id && it.version == version }.blocks.flatMap { it.joints() }
        assertTrue(joints("torso", 2).all { it.hinge == Hinge.X })
        assertTrue(joints("tail", 2).all { it.hinge == Hinge.X })
        assertEquals(listOf("neck" to 0f, "jaw" to 0.6f), joints("head", 2).map { it.name to it.u })
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
                val params = template.params.filterValues { it.values != null || (it.min != null && it.max != null) }.mapValues { (_, spec) ->
                    spec.values?.random(random) ?: (spec.min!! + random.nextFloat() * (spec.max!! - spec.min))
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
        assertEquals(listOf("ear", "head", "leg", "paw", "tail", "torso"), shipped.parts.map { it.id.value }.distinct().sorted())
        for (template in shipped.parts) {
            val mesh = PartBuilder.mesh(template.id.value, template, PartBuilder.build(template))
            GoldenFixture.check(Path.of("src/desktopTest/golden/${template.id.value}-${template.version}.txt"), templateFixture(mesh), "character")
        }
    }

    @Test
    fun aFixtureHoldsEveryVertexSoAnInteriorOneMovingFails() {
        val paw = template("paw")
        val patches = PartBuilder.build(paw)
        val body = patches.first()
        val moved = body.points.copyOf().also { it[body.index(8, 5) * Patch.COMPONENTS + 1] += FIXTURE_STEP * 2f }
        val changed = patches.map { if (it === body) Patch(it.name, it.nu, it.nv, it.closedV, it.slices, moved, it.boneU) else it }
        assertNotEquals(templateFixture(PartBuilder.mesh("paw", paw, patches)), templateFixture(PartBuilder.mesh("paw", paw, changed)))
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
