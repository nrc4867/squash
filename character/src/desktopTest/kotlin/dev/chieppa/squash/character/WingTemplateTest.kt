package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WingTemplateTest {
    private val wing = template("wing-feathered")
    private val axes = PartBuilder.axes(wing)

    @Test
    fun itLintsCleanAndIsAWing() {
        assertEquals(emptyList(), TemplateLint.lint(wing))
        assertEquals(Role.WING, wing.role)
    }

    @Test
    fun itBuildsWithNothingDrawnOnThreeBones() {
        val body = PartBuilder.build(wing).single { it.name == BODY_PATCH }
        assertEquals(wing.topology.rings, body.nu)
        assertEquals(listOf("shoulder", "elbow", "wrist"), wing.blocks.flatMap { it.joints() }.map { it.name })
        assertTrue(wing.blocks.flatMap { it.joints() }.none { it.stretch })
    }

    @Test
    fun aTopPlanformSetsTheChordAndASideOutlineTheThickness() {
        val narrow = planform(0.08f)
        val broad = planform(0.2f)
        val thin = planform(0.02f)
        val thick = planform(0.05f)
        val chordNarrow = extent(mapOf(View.TOP to narrow, View.SIDE to thin), axes.up)
        val chordBroad = extent(mapOf(View.TOP to broad, View.SIDE to thin), axes.up)
        assertEquals(0.16f, chordNarrow, 0.02f)
        assertEquals(0.4f, chordBroad, 0.03f)
        val thicknessThin = extent(mapOf(View.TOP to broad, View.SIDE to thin), axes.side)
        val thicknessThick = extent(mapOf(View.TOP to broad, View.SIDE to thick), axes.side)
        assertEquals(0.04f, thicknessThin, 0.01f)
        assertEquals(0.1f, thicknessThick, 0.015f)
        assertEquals(chordBroad, extent(mapOf(View.TOP to broad, View.SIDE to thick), axes.up), 1e-3f)
    }

    /** A box open at the socket plane, [half] either side of the axis. */
    private fun planform(half: Float): OutlineCurve = outline(0f to half, 0.5f to half, 0.5f to -half, 0f to -half)

    /** How far the body's middle ring reaches along [direction] with [outlines] drawn. */
    private fun extent(outlines: Map<View, OutlineCurve>, direction: Vec3): Float {
        val body = PartBuilder.build(wing, PartData(outlines = outlines)).single { it.name == BODY_PATCH }
        val along = body.ring(body.nu / 2).map { it dot direction }
        return along.max() - along.min()
    }
}
