package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.cellsOfRuns
import dev.chieppa.squash.document.runsOfCells
import dev.chieppa.squash.document.ChartBox
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId

/**
 * Cells of a [chart] that are held, by part: an area painted on a surface, read the same on both sides of a pair since the chart reads
 * the other side as its leader. Saved as runs (see [runs]). A general mask that knows nothing of what is painted with it.
 */
class SurfaceMask private constructor(val chart: BodyChart, private val cells: Map<PartId, BooleanArray>) {
    /** Whether [cell] of [part], or of its pair's leader, is held. */
    fun held(part: PartId, cell: Int): Boolean = cells[chart.leaderOf(part)]?.get(cell) == true

    /** Whether the cell [anchor] lies in is held. */
    operator fun contains(anchor: Anchor): Boolean = chart.cellOf(anchor)?.let { (part, cell) -> held(part, cell) } == true

    /** The parts it holds any cell of, leaders of pairs only. */
    val parts: Set<PartId> by lazy { cells.filterValues { held -> held.any { it } }.keys }

    val isEmpty: Boolean get() = parts.isEmpty()

    /** How many cells of [part] it holds. */
    fun count(part: PartId): Int = cells[chart.leaderOf(part)]?.count { it } ?: 0

    /** How many cells it holds. */
    val count: Int get() = cells.values.sumOf { held -> held.count { it } }

    operator fun plus(other: SurfaceMask): SurfaceMask = build(chart) { part, cell -> held(part, cell) || other.held(part, cell) }

    operator fun minus(other: SurfaceMask): SurfaceMask = build(chart) { part, cell -> held(part, cell) && !other.held(part, cell) }

    /** The cells held here and in [other]. */
    fun within(other: SurfaceMask): SurfaceMask = build(chart) { part, cell -> held(part, cell) && other.held(part, cell) }

    /** This mask grown by one cell everywhere along its edge, across where parts meet too. */
    fun grown(): SurfaceMask = build(chart) { part, cell -> held(part, cell) || chart.neighbours(part, cell).any { (p, c) -> held(p, c) } }

    /** This mask shrunk by one cell everywhere along its edge, across where parts meet too. */
    fun shrunk(): SurfaceMask = build(chart) { part, cell -> held(part, cell) && chart.neighbours(part, cell).all { (p, c) -> held(p, c) } }

    /** This mask grown by [steps] cells. */
    fun grown(steps: Int): SurfaceMask = (0 until steps).fold(this) { mask, _ -> mask.grown() }

    /**
     * This mask with its edge smoothed [steps] times where it lies in [near], or everywhere without it: each cell there takes what most of
     * itself and the cells beside it on its part, or at its root the cell of the parent it sits on, are.
     */
    fun cleaned(steps: Int, near: SurfaceMask? = null): SurfaceMask = (0 until steps).fold(this) { mask, _ ->
        build(chart) { part, cell ->
            val now = mask.held(part, cell)
            if (near != null && !near.held(part, cell)) return@build now
            val beside = mask.localNeighbours(part, cell)
            val votes = beside.count { (p, c) -> mask.held(p, c) } + if (now) 1 else 0
            votes * 2 > beside.size + 1
        }
    }

    /** This mask with each held cell of the parts [middle] holds held on the other side of the body's middle too, v read as -v. */
    fun mirrored(middle: Set<PartId>): SurfaceMask = build(chart) { part, cell ->
        held(part, cell) || (part in middle && held(part, mirrorCell(cell)))
    }

    /** What of each part it holds, as a box (see [ChartBox]); a part it holds none of is left out. */
    fun boxes(): Map<PartId, ChartBox> = cells.mapNotNull { (part, held) -> ChartBox.over { held[it] }?.let { part to it } }.toMap()

    /**
     * Each part's cells, row by row, as the lengths of their runs, the first one not held: a part held whole is `[0, cells]`, and a run of
     * not held cells reaching the end is left off. Parts it holds none of are left out.
     */
    fun runs(): Map<PartId, List<Int>> = cells.filterKeys { it in parts }.mapValues { (_, held) -> runsOfCells(held) }

    /** The cells beside [cell] on its own part, with the cell of its parent where it lies on its part's root row. */
    private fun localNeighbours(part: PartId, cell: Int): List<Pair<PartId, Int>> {
        val row = cell / CHART_COLUMNS
        val column = cell % CHART_COLUMNS
        val out = ArrayList<Pair<PartId, Int>>(4)
        if (row > 0) out += part to cell - CHART_COLUMNS
        if (row < CHART_ROWS - 1) out += part to cell + CHART_COLUMNS
        out += part to row * CHART_COLUMNS + (column + 1) % CHART_COLUMNS
        out += part to row * CHART_COLUMNS + (column + CHART_COLUMNS - 1) % CHART_COLUMNS
        val chartPart = chart.part(part)
        if (row == 0 && chartPart?.parent != null) out += chartPart.parent to chart.socketCell(chartPart)
        return out
    }

    override fun equals(other: Any?): Boolean =
        other is SurfaceMask && other.chart === chart && runs() == other.runs()

    override fun hashCode(): Int = runs().hashCode()

    companion object {
        /** A mask holding nothing. */
        fun empty(chart: BodyChart): SurfaceMask = SurfaceMask(chart, emptyMap())

        /** The mask [runs] (see [SurfaceMask.runs]) hold on [chart]; runs of parts the chart lacks are left out. */
        fun of(chart: BodyChart, runs: Map<PartId, List<Int>>): SurfaceMask = SurfaceMask(chart, runs.mapNotNull { (part, lengths) ->
            val leader = chart.part(part)?.id ?: return@mapNotNull null
            leader to cellsOfRuns(lengths)
        }.toMap())

        /** The mask holding each cell of [chart] that [held] says it does. */
        fun build(chart: BodyChart, held: (PartId, Int) -> Boolean): SurfaceMask =
            SurfaceMask(chart, chart.parts.associate { part -> part.id to BooleanArray(CHART_CELLS) { held(part.id, it) } })

        /** The mask holding every cell of [parts]. */
        fun whole(chart: BodyChart, parts: Set<PartId>): SurfaceMask {
            val leaders = parts.mapTo(HashSet(), chart::leaderOf)
            return build(chart) { part, _ -> part in leaders }
        }
    }
}

/** The cell across the body's middle from [cell], at -v. */
fun mirrorCell(cell: Int): Int = (cell / CHART_COLUMNS) * CHART_COLUMNS + (CHART_COLUMNS - 1 - cell % CHART_COLUMNS)
