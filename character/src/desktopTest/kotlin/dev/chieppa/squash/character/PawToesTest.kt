package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private val DRAWN_SIDE = outline(0f to 0.3f, 0.4f to 0.32f, 0.8f to 0.2f, 1f to 0f, 0.8f to -0.2f, 0.4f to -0.32f, 0f to -0.3f)
private val DRAWN_TOP = outline(0f to 0.25f, 0.5f to 0.3f, 0.9f to 0.2f, 1f to 0f, 0.9f to -0.2f, 0.5f to -0.3f, 0f to -0.25f)
private val DRAWN_FRONT = outline(-0.4f to 0f, 0f to 0.1f, 0.4f to 0f, 0f to -0.1f, -0.4f to 0f)
private val TOE_SETTINGS = listOf(0f, 3f, 4f, 5f)

/** The toe count: none, a plain rounded block, or a row of three to five. */
class PawToesTest {
    private val paw = template("paw")
    private val axes = PartBuilder.axes(paw)
    private val body = { patches: List<Patch> -> patches.first { it.name == "body" } }

    private fun assertWatertight(template: PartTemplate, patches: List<Patch>, label: String) {
        for (patch in PartBuilder.mesh(template.id.value, template, patches).patches) {
            assertEquals(emptyList(), MeshInvariants.check(patch, PartBuilder.openRings(template, patch.patch)), label)
        }
    }

    @Test
    fun aNewPawHasNoToes() {
        assertEquals(listOf("body", "pad:palm"), PartBuilder.build(paw).map { it.name })
    }

    @Test
    fun everyToeSettingKeepsTheBodysGridAndHolds() {
        val drawings = listOf(emptyMap(), mapOf(View.SIDE to DRAWN_SIDE), mapOf(View.TOP to DRAWN_TOP), mapOf(View.SIDE to DRAWN_SIDE, View.TOP to DRAWN_TOP))
        for (toes in TOE_SETTINGS) for (outlines in drawings) {
            val patches = PartBuilder.build(paw, mapOf("toes" to toes), outlines)
            assertWatertight(paw, patches, "$toes toes, drawn ${outlines.keys}")
            assertEquals(paw.topology.rings, body(patches).nu)
            assertEquals(paw.topology.segments, body(patches).nv)
            assertEquals(toes.toInt(), patches.count { it.name.startsWith("toes:") })
            assertEquals(toes.toInt(), patches.count { it.name.startsWith("pad:toe:") })
        }
    }

    @Test
    fun withoutToesTheUndrawnBodyRoundsOffAtTheAxisEnd() {
        val slices = body(PartBuilder.build(paw)).slices!!
        assertEquals(1f, slices.last().u)
        assertEquals(0f, slices.last().height)
        assertEquals(paw.axis.length, slices.last().frame.center dot axes.along, 1e-4f)
        val heights = slices.map { it.height }
        val fullest = heights.indexOf(heights.max())
        assertEquals(heights.take(fullest + 1).sorted(), heights.take(fullest + 1), "it rounds up from its back to its radius")
        assertEquals(heights.max(), heights[slices.size / 2], 1e-4f, "keeps that radius, with no waist, up to the cap")
        assertEquals(heights.drop(fullest).sortedDescending(), heights.drop(fullest), "and narrows only toward its end")
    }

    @Test
    fun withToesTheBodyStopsWhereTheyTakeOver() {
        val slices = body(PartBuilder.build(paw, mapOf("toes" to 3f))).slices!!
        assertEquals(0.66f, slices.last().u, 1e-5f)
    }

    @Test
    fun withoutToesADrawnBodyFollowsTheOutlineToItsTip() {
        val patches = PartBuilder.build(paw, outlines = mapOf(View.SIDE to DRAWN_SIDE, View.TOP to DRAWN_TOP))
        val slices = body(patches).slices!!
        assertEquals(1f, slices.last().frame.center dot axes.along, 1e-3f)
        val heights = slices.dropLast(1).map { it.height }
        val fullest = heights.indexOf(heights.max())
        assertEquals(heights.take(fullest + 1).sorted(), heights.take(fullest + 1), "it rises to its fullest with no waist")
        assertEquals(heights.drop(fullest).sortedDescending(), heights.drop(fullest), "and falls from there to the tip")
    }

    @Test
    fun thePalmPadSitsAtItsUOnThePlainBody() {
        val pad = PartBuilder.build(paw).first { it.name == "pad:palm" }
        val middle = (pad.vertices().maxOf { it dot axes.along } + pad.vertices().minOf { it dot axes.along }) / 2f
        assertEquals(0.47f * paw.axis.length, middle, 2e-3f)
    }

    @Test
    fun aFrontOutlineShapesThePawsBackAndOnlyAToelessPawsFront() {
        for (toes in listOf(3f, 4f, 5f)) {
            val params = mapOf("toes" to toes)
            val bare = body(PartBuilder.build(paw, params))
            val shaped = body(PartBuilder.build(paw, params, mapOf(View.FRONT to DRAWN_FRONT)))
            for (ring in 0 until bare.nu) {
                val u = bare.slices!![ring].u
                if (u > 0f) assertEquals(u < 1f / 6f, bare.ring(ring) != shaped.ring(ring), "$toes toes, ring $ring at u = $u")
            }
        }
        val plain = body(PartBuilder.build(paw))
        val capped = body(PartBuilder.build(paw, outlines = mapOf(View.FRONT to DRAWN_FRONT)))
        assertNotEquals(plain, capped)
        for (ring in 0 until plain.nu) {
            val u = plain.slices!![ring].u
            if (u > 0f && u < 1f) assertEquals(u < 1f / 6f || u > 5f / 6f, plain.ring(ring) != capped.ring(ring), "ring $ring at u = $u")
        }
        assertWatertight(paw, PartBuilder.build(paw, outlines = mapOf(View.FRONT to DRAWN_FRONT)), "front only")
    }
}
