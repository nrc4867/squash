package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.templateOutline
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * On a hand, foot or paw whose field fits the drawing, the drawn Side and Top are the main control: dragging them out reshapes the whole
 * solid, toes and all, and the template's own numbers shape what lies inside.
 */
class OutlineFitTest {
    private fun newest(id: String): PartTemplate = shipped.parts.filter { it.id.value == id }.maxBy { it.version }

    private fun version(id: String, version: Int): PartTemplate = shipped.parts.single { it.id.value == id && it.version == version }

    /** Every vertex the build shows, in the part's own axes as built. */
    private fun shown(patches: List<Patch>) = patches.flatMap { patch -> (0 until patch.nu * patch.nv).filter { patch.buried?.get(it) != true }.map { patch.point(it) } }

    private class Extent(val length: Float, val width: Float)

    private fun extent(patches: List<Patch>): Extent {
        val points = shown(patches)
        return Extent(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.z } - points.minOf { it.z })
    }

    /** [outline] scaled about its own start by [along] and about the axis by [across]: the outline dragged longer and wider. */
    private fun dragged(outline: OutlineCurve, along: Float, across: Float): OutlineCurve {
        val start = outline.points.minOf { it.x }
        return OutlineCurve(outline.points.map { CurvePoint(start + (it.x - start) * along, it.y * across) })
    }

    private fun outlines(template: PartTemplate, params: Map<String, Float> = emptyMap()): Map<View, OutlineCurve> {
        val side = assertNotNull(templateOutline(template, PartData(params = params), View.SIDE))
        val top = assertNotNull(templateOutline(template, PartData(params = params, outlines = mapOf(View.SIDE to side)), View.TOP))
        return mapOf(View.SIDE to side, View.TOP to top)
    }

    private fun assertClosed(patches: List<Patch>, label: String) = patches.forEach { patch ->
        assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(patch) { 0 }, emptySet()), "$label ${patch.name}")
    }

    @Test
    fun draggingTheOutlineLongerAndWiderReshapesEveryFittedFootAndPaw() {
        for (id in listOf("paw", "foot-bird", "foot")) {
            val template = newest(id)
            val drawn = outlines(template)
            val undrawn = extent(PartBuilder.build(template))
            val asStarted = extent(PartBuilder.build(template, outlines = drawn))
            assertEquals(undrawn.length, asStarted.length, undrawn.length * 0.05f, "$id: its own outline keeps its length")
            assertEquals(undrawn.width, asStarted.width, undrawn.width * 0.08f, "$id: its own outline keeps its width")
            val longer = PartBuilder.build(template, outlines = drawn.mapValues { (_, it) -> dragged(it, 1.4f, 1f) })
            assertClosed(longer, "$id longer")
            assertEquals(asStarted.length * 1.4f, extent(longer).length, asStarted.length * 0.06f, "$id: dragged 1.4 times longer")
            val wider = extent(PartBuilder.build(template, outlines = mapOf(View.SIDE to drawn.getValue(View.SIDE), View.TOP to dragged(drawn.getValue(View.TOP), 1f, 1.5f))))
            assertEquals(asStarted.width * 1.5f, wider.width, asStarted.width * 0.1f, "$id: dragged 1.5 times wider")
            val both = PartBuilder.build(template, outlines = mapOf(View.SIDE to dragged(drawn.getValue(View.SIDE), 1.3f, 1f), View.TOP to dragged(drawn.getValue(View.TOP), 1.3f, 1.4f)))
            assertClosed(both, "$id longer and wider")
            val sides = shown(both)
            assertEquals(0f, sides.maxOf { it.z } + sides.minOf { it.z }, asStarted.width * 0.05f, "$id: widened evenly to both sides")
        }
    }

    @Test
    fun aDrawingOnAnOlderVersionStillSizesOnlyItsField() {
        val paw = version("paw", 5)
        val drawn = outlines(newest("paw")).mapValues { (_, it) -> dragged(it, 1.4f, 1.5f) }
        assertEquals(extent(PartBuilder.build(paw)).length, extent(PartBuilder.build(paw, outlines = drawn)).length, 1e-4f, "paw 5's toes keep their place")
    }

    @Test
    fun everyOlderHandFootAndPawStillLoadsLintsAndBuilds() {
        for ((id, versions) in listOf("hand" to 1..5, "foot" to 1..4, "paw" to 1..5, "foot-bird" to 1..3)) for (v in versions) {
            val template = version(id, v)
            assertEquals(emptyList(), TemplateLint.lint(template), "$id $v")
            assertTrue(PartBuilder.build(template).isNotEmpty(), "$id $v builds")
        }
    }
}
