package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val ON = 1f

/** A web of skin between the digits of a hand, a foot, a paw or a bird's foot: off by default, a full or half web, thin, closed, riding its two digits. */
class DigitWebTest {
    private fun newest(id: String): PartTemplate = shipped.parts.filter { it.id == TemplateId(id) }.maxBy { it.version }

    private val templates = listOf("hand", "foot", "paw", "foot-bird").map(::newest)

    private fun webs(template: PartTemplate, params: Map<String, Float> = emptyMap()): List<Patch> =
        PartBuilder.build(template, mapOf("webbing" to ON) + params).filter { it.name.startsWith("webbing:") }

    @Test
    fun theNewestHandsAndFeetLintCleanAndBuildNoWebUntilAsked() {
        templates.forEach { template ->
            assertEquals(emptyList(), TemplateLint.lint(template), template.id.value)
            assertTrue(PartBuilder.build(template).none { it.name.startsWith("webbing") }, "${template.id.value} builds no web by default")
        }
    }

    @Test
    fun aWebSpansEachNeighbouringPairOfCountedDigitsAndIsAClosedThinSheet() {
        templates.forEach { template ->
            val patches = PartBuilder.build(template, mapOf("webbing" to ON))
            val digits = patches.count { it.name.substringAfter(':').toIntOrNull() != null && it.slices != null && !it.name.startsWith("webbing") }
            val webs = patches.filter { it.name.startsWith("webbing:") }
            assertEquals(digits - 1, webs.size, "${template.id.value}: one web between each two counted digits")
            webs.forEach { web ->
                val spanned = assertNotNull(web.spanned, web.name)
                assertTrue(spanned.first != spanned.second, "${web.name} rides two digits")
                val mesh = PartBuilder.mesh(template.id.value, template, listOf(web)).patches.single()
                assertEquals(emptyList(), MeshInvariants.check(mesh, emptySet()), "${template.id.value} ${web.name}")
            }
        }
    }

    @Test
    fun aHalfWebStopsHalfWayUpTheDigits() {
        val foot = newest("foot-bird")
        fun reach(params: Map<String, Float>) = webs(foot, params).maxOf { web -> assertNotNull(web.spanned).firstUs.max() }
        val full = reach(emptyMap())
        val half = reach(mapOf("webReach" to 0.5f))
        val root = webs(foot).minOf { web -> assertNotNull(web.spanned).firstUs.min() }
        assertTrue(half < full, "a half web reaches less far: $half against $full")
        assertTrue(half - root < (full - root) * 0.7f, "a half web stops near half way: ${half - root} of ${full - root}")
    }

    @Test
    fun aScallopedEdgeDipsBetweenTheDigitsAndAStraightOneDoesNot() {
        val paw = newest("paw")
        fun dip(edge: Float): Float {
            val web = webs(paw, mapOf("webEdge" to edge)).first()
            val spanned = assertNotNull(web.spanned)
            val middle = web.nu / 2
            val atMiddle = (0 until web.nv).maxOf { spanned.firstUs[web.index(middle, it)] }
            val atRail = (0 until web.nv).maxOf { spanned.firstUs[web.index(1, it)] }
            return atRail - atMiddle
        }
        assertTrue(dip(0f) < 1e-4f, "a straight edge reaches as far between the digits as at them")
        assertTrue(dip(0.4f) > 0.01f, "a scalloped edge dips between them")
    }

    @Test
    fun aWebIsThinnerThanTheDigitsItJoins() {
        val hand = newest("hand")
        val patches = PartBuilder.build(hand, mapOf("webbing" to ON))
        val web = patches.first { it.name.startsWith("webbing:") }
        val finger = patches.first { it.name == assertNotNull(web.spanned).first }
        val fingerThickness = assertNotNull(finger.slices).maxOf { it.height }
        val middle = web.nu / 2
        val points = (0 until web.nv).map { web.point(web.index(middle, it)) }
        val centre = points.fold(Vec3.ZERO) { a, b -> a + b } * (1f / points.size)
        val thickness = points.maxOf { (it - centre).length }
        assertTrue(thickness > 0f)
        val faceGap = (web.point(web.index(middle, web.nv / 4)) - web.point(web.index(middle, web.nv - 1 - web.nv / 4))).length
        assertTrue(faceGap < fingerThickness * 0.5f, "the web's faces lie $faceGap apart, a finger is $fingerThickness thick")
    }
}
