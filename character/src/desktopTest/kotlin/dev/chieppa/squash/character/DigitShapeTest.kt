package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Membrane
import dev.chieppa.squash.character.block.MembranePair
import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SectionSpec
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WEBS = "webs"
private const val OFF = 0f

/** Webs span a toe row's neighbouring digits through the slice build, and digits taper and swell at their joints, on the newer versions only. */
class DigitShapeTest {
    private fun template(id: String, version: Int): PartTemplate = shipped.parts.single { it.id.value == id && it.version == version }

    private fun webs(template: PartTemplate, params: Map<String, Float> = emptyMap()) = PartBuilder.build(template, params).filter { it.name.startsWith("web:") }

    @Test
    fun eachShapedTemplateWebsItsNeighbouringDigitsOnAFixedGrid() {
        val expected = mapOf(("hand" to 3) to listOf("web:0", "web:1", "web:2", "web:3"), ("foot-bird" to 3) to listOf("web:0", "web:1"), ("foot" to 2) to listOf("web:0", "web:1", "web:2", "web:3"))
        for ((ref, names) in expected) {
            val template = template(ref.first, ref.second)
            assertEquals(emptyList(), TemplateLint.lint(template))
            val patches = PartBuilder.build(template)
            val webs = patches.filter { it.name.startsWith("web:") }
            assertEquals(names, webs.map { it.name }, "${ref.first}: the hind toe takes no web")
            val membrane = template.blocks.filterIsInstance<Membrane>().single()
            webs.forEach { assertEquals(membrane.rings to membrane.segments, it.nu to it.nv) }
            val mesh = PartBuilder.mesh(ref.first, template, patches)
            mesh.patches.filter { it.patch.name.startsWith("web:") }.forEach { web ->
                assertEquals(emptyList(), MeshInvariants.check(web, emptySet()), web.patch.name)
                assertTrue(MeshInvariants.signedVolume(web) > 0f, "${web.patch.name} faces out")
            }
        }
    }

    @Test
    fun aPawWebsOnlyTheToesItHas() {
        val paw = template("paw", 3)
        assertEquals(emptyList(), webs(paw).map { it.name })
        assertEquals(listOf("web:0", "web:1"), webs(paw, mapOf("toes" to 3f)).map { it.name })
        assertEquals(listOf("web:0", "web:1", "web:2", "web:3"), webs(paw, mapOf("toes" to 5f)).map { it.name })
        assertEquals(emptyList(), webs(paw, mapOf("toes" to 5f, WEBS to OFF)).map { it.name })
    }

    @Test
    fun earlierVersionsBuildNoWebs() {
        for ((id, version) in listOf("hand" to 2, "paw" to 2, "foot-bird" to 2, "foot" to 1)) {
            assertEquals(emptyList(), webs(template(id, version), mapOf("toes" to 5f)).map { it.name }, "$id $version")
        }
    }

    @Test
    fun aWebsVerticesTakeTheBonesOfTheDigitTheyRideTheMore() {
        val hand = template("hand", 3)
        val patches = PartBuilder.build(hand)
        val tree = JointTree(hand, patches.map { it.name })
        val mesh = PartBuilder.mesh("hand", hand, patches)
        val web = mesh.patches.single { it.patch.name == "web:2" }
        val spanned = requireNotNull(web.patch.spanned)
        assertEquals("fingers:2" to "fingers:3", spanned.first to spanned.second)
        for (vertex in 0 until web.vertexCount) {
            assertEquals(tree.boneAt(spanned.nearer(vertex), spanned.nearerU(vertex)), web.bones[vertex])
        }
        assertTrue(web.bones.toSet().size > 2, "the web follows the wrist and both fingers' first joints")
    }

    @Test
    fun fingersTaperAndSwellAtTheirJoints() {
        val hand = template("hand", 3)
        val finger = PartBuilder.build(hand).single { it.name == "fingers:2" }
        val slices = requireNotNull(finger.slices)
        val row = hand.blocks.filterIsInstance<ToeRow>().single()
        val span = slices.last().u - slices.first().u
        fun heightAt(share: Float) = slices.minBy { kotlin.math.abs((it.u - slices.first().u) / span - share) }.height
        val joints = requireNotNull(row.joints).at
        assertTrue(heightAt(joints[2] + 0.12f) < heightAt(joints[1] + 0.12f), "the finger thins toward its tip")
        assertTrue(heightAt(joints[1]) > heightAt(joints[1] + 0.1f), "the finger swells at its middle joint")
        assertTrue(slices[slices.size / 2].width > slices[slices.size / 2].height, "the finger is wider than it is thick")
    }

    @Test
    fun theLintCatchesAMembraneOnNoRowAndAMisfitSection() {
        val hand = template("hand", 3)
        val membrane = hand.blocks.filterIsInstance<Membrane>().single()
        val row = hand.blocks.filterIsInstance<ToeRow>().single()
        val broken = hand.copy(
            blocks = hand.blocks.map {
                when (it) {
                    membrane -> membrane.copy(on = "palm", segments = 15, between = listOf(MembranePair(1, 1)))
                    row -> row.copy(section = SectionSpec.Points(listOf(Vec2(0f, 1f), Vec2(1f, 0f), Vec2(0f, -1f))))
                    else -> it
                }
            },
        )
        val fields = TemplateLint.lint(broken).map { it.path.substringAfterLast('.') }
        assertTrue(listOf("on", "segments", "between[0]", "section").all { it in fields }, "$fields")
        assertEquals(SectionSpec.Named(SectionProfile.EGG), row.section)
    }
}
