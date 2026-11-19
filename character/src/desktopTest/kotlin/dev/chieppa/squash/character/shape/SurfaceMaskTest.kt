package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceMaskTest {
    private val body = PartId("body")
    private val limb = PartId("limb")
    private val other = PartId("limb-r")
    private val chart = BodyChart(
        listOf(ChartPart(body, PATCH, null, 0f, 0f, 1f), ChartPart(limb, PATCH, body, 0.5f, 0.5f, 1f)),
        leaders = mapOf(other to limb),
    )

    private fun rows(part: PartId, from: Int, to: Int) = SurfaceMask.build(chart) { p, cell -> p == part && cell / CHART_COLUMNS in from until to }

    @Test
    fun runsRoundTripAndAWholePartIsOneRun() {
        val mask = SurfaceMask.whole(chart, setOf(body)) + rows(limb, 10, 20)
        val runs = mask.runs()
        assertEquals(listOf(0, CHART_CELLS), runs[body])
        assertEquals(listOf(10 * CHART_COLUMNS, 10 * CHART_COLUMNS), runs[limb])
        assertEquals(mask, SurfaceMask.of(chart, runs))
    }

    @Test
    fun theOtherSideOfAPairReadsAsItsLeader() {
        val mask = rows(limb, 0, 5)
        assertTrue(Anchor(other, PATCH, 0.01f, 0.3f) in mask)
        assertEquals(setOf(limb), SurfaceMask.whole(chart, setOf(other)).parts)
    }

    @Test
    fun growAndShrinkMoveTheEdgeByACellAndShrinkUndoesGrow() {
        val band = rows(limb, 20, 30)
        val grown = band.grown()
        assertEquals(12 * CHART_COLUMNS, grown.count(limb))
        assertEquals(band, grown.shrunk())
        assertEquals(8 * CHART_COLUMNS, band.shrunk().count(limb))
    }

    @Test
    fun growingFromAPartsRootReachesTheCellItSitsOn() {
        val root = rows(limb, 0, 1)
        val socket = chart.socketCell(chart.part(limb)!!)
        assertTrue(root.grown().held(body, socket))
        assertFalse(root.held(body, socket))
    }

    @Test
    fun cleaningTakesASpeckAwayButKeepsABand() {
        val band = rows(limb, 20, 30)
        val speck = SurfaceMask.build(chart) { p, cell -> p == limb && cell == 45 * CHART_COLUMNS + 3 }
        val cleaned = (band + speck).cleaned(1)
        assertEquals(band, cleaned)
    }

    @Test
    fun mirroringHoldsEachCellAtMinusVToo() {
        val one = SurfaceMask.build(chart) { p, cell -> p == body && cell == 5 * CHART_COLUMNS + 2 }
        val both = one.mirrored(setOf(body))
        assertTrue(both.held(body, 5 * CHART_COLUMNS + CHART_COLUMNS - 3))
        assertEquals(2, both.count(body))
    }

    @Test
    fun aBandRoundALimbHasTwoLevelEdgesAndARootBandOneRingAtTheRoot() {
        val band = rows(limb, 20, 30)
        val edges = edgesOf(band)
        assertEquals(2, edges.size)
        val us = edges.map { edge -> edge.curve.points.map { it.u }.average().toFloat() }.sorted()
        assertEquals(20f / CHART_ROWS, us[0], 0.002f)
        assertEquals(30f / CHART_ROWS, us[1], 0.002f)
        edges.forEach { edge -> assertTrue(edge.curve.closed && edge.curve.points.all { it.part == limb }) }
        val atRoot = edgesOf(SurfaceMask.whole(chart, setOf(body)))
        assertEquals(1, atRoot.size, "cloth on the body ends at the limb's root")
        assertTrue(atRoot.single().curve.points.all { it.part == limb && it.u == 0f })
    }

    @Test
    fun anEdgeAlongAnotherMasksCellsIsASeam() {
        val upper = rows(limb, 0, 20)
        val lower = rows(limb, 20, 40)
        val edges = edgesOf(lower, besides = upper)
        assertEquals(listOf(true, false), edges.sortedBy { e -> e.curve.points.map { it.u }.average() }.map { it.seam })
    }

    @Test
    fun redrawingAMasksOwnEdgesKeepsItAndMovingOneMovesTheEdge() {
        val band = rows(limb, 20, 30)
        val curves = edgesOf(band).map { it.curve }
        assertEquals(band, redrawn(band, curves))
        val low = curves.minBy { c -> c.points.map { it.u }.average() }
        val lower = low.copy(points = low.points.map { it.copy(u = 10f / CHART_ROWS) })
        val moved = redrawn(band, curves - low + lower)
        assertEquals(20 * CHART_COLUMNS, moved.count(limb), "the edge moved ten rows down takes them in")
    }

    @Test
    fun takingAnEdgeAwayFillsItsFarSide() {
        val band = rows(limb, 20, 30)
        val curves = edgesOf(band).map { it.curve }
        val high = curves.maxBy { c -> c.points.map { it.u }.average() }
        val opened = withoutEdge(band, high, curves)
        assertEquals((CHART_ROWS - 20) * CHART_COLUMNS, opened.count(limb), "the band runs on to the limb's end")
    }

    private companion object {
        const val PATCH = "body"
    }
}
