package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.recipe.model.RecipeTarget
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProportionsTest {
    private fun box(lx: Float, ly: Float, lz: Float): List<Vec3> =
        listOf(0f, 1f).flatMap { x -> listOf(0f, 1f).flatMap { y -> listOf(0f, 1f).map { z -> Vec3((x - 0.5f) * lx, (y - 0.5f) * ly, (z - 0.5f) * lz) } } }

    @Test
    fun `a hand's length is its longest extent and its width the larger of the other two`() {
        val (length, width) = handExtent(box(0.1f, 0.4f, 0.2f))
        assertTrue(abs(length - 0.4f) < 1e-3f, "length $length")
        assertTrue(abs(width - 0.2f) < 1e-3f, "width $width")
    }

    @Test
    fun `a foot's length and width are its footprint's extents, whatever way it points`() {
        val turned = box(0.3f, 0.1f, 0.1f).map { Vec3(it.x * 0.8f - it.z * 0.6f, it.y, it.x * 0.6f + it.z * 0.8f) }
        val (length, width) = footExtent(turned)
        assertTrue(abs(length - 0.3f) < 1e-3f, "length $length")
        assertTrue(abs(width - 0.1f) < 1e-3f, "width $width")
    }

    private val report = ProportionReport(
        listOf(Proportion("handLength", 0.1f, 0.25f, 0.05f), Proportion("eyeLine", 1.4f, 0.5f, 0.9f, isPlace = true)),
        mapOf("footLength" to "no foot part"),
    )

    @Test
    fun `a size off its target by more than the tolerance is flagged, and one within it passes`() {
        val checks = checkTargets(
            listOf(RecipeTarget("handLength", "head", 0.4f), RecipeTarget("handLength", "head", 0.27f), RecipeTarget("handLength", "height", 0.06f, tolerance = 0.5f)),
            report,
        )
        assertEquals(listOf(true, false, false), checks.map { it.flagged })
        assertTrue(checks[0].line.startsWith("FLAG target handLength 0.400 of the head, built 0.250 (-38%"), checks[0].line)
    }

    @Test
    fun `a place is held to a share of the head, and what cannot be checked is flagged with its reason`() {
        val checks = checkTargets(
            listOf(RecipeTarget("eyeLine", "head", 0.53f), RecipeTarget("eyeLine", "head", 0.6f), RecipeTarget("footLength", "head", 0.4f), RecipeTarget("shoeSize", "head", 1f)),
            report,
        )
        assertEquals(listOf(false, true, true, true), checks.map { it.flagged })
        assertTrue(checks[2].line.contains("no foot part"))
        assertTrue(checks[3].line.contains("not a measured proportion"))
    }
}
