package dev.chieppa.squash.screens.look.spike

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.character.shape.cellMiddle
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.character.dress.bodyChart
import dev.chieppa.squash.character.dress.placeOnBody
import dev.chieppa.squash.screens.dress.roundStroke
import dev.chieppa.squash.screens.fennec.BACK_LEG_L
import dev.chieppa.squash.screens.fennec.BACK_PAW_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.woman.ARM_L
import dev.chieppa.squash.screens.woman.FOOT_L
import dev.chieppa.squash.screens.woman.LEG_L
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import dev.chieppa.squash.screens.fennec.TORSO as FENNEC_TORSO
import dev.chieppa.squash.screens.woman.TORSO as WOMAN_TORSO

/** One cell of a part's chart where it lies at rest, with its place in the part's box: [a] along x, [h] up y, [s] across z, each 0 to 1. */
class CellAt(val cell: Int, val at: Vec3, val a: Float, val h: Float, val s: Float) {
    val v: Float get() = (cell % CHART_COLUMNS + 0.5f) / CHART_COLUMNS
    val u: Float get() = (cell / CHART_COLUMNS + 0.5f) / CHART_ROWS
}

/** A part's cells at rest, on its leading side. */
class PartCells(val part: PartId, val cells: List<CellAt>, val low: Vec3, val high: Vec3) {
    val size: Vec3 get() = high - low
}

fun partCells(character: Character, body: AssembledCharacter, part: PartId): PartCells {
    val chart = bodyChart(character, body)
    val held = checkNotNull(chart.part(part))
    val places = (0 until CHART_CELLS).mapNotNull { cell -> placeOnBody(body, cellMiddle(held.id, held.patch, cell))?.let { cell to it } }
    val low = Vec3(places.minOf { it.second.x }, places.minOf { it.second.y }, places.minOf { it.second.z })
    val high = Vec3(places.maxOf { it.second.x }, places.maxOf { it.second.y }, places.maxOf { it.second.z })
    val size = high - low
    return PartCells(held.id, places.map { (cell, at) ->
        CellAt(cell, at, (at.x - low.x) / size.x, (at.y - low.y) / size.y, (at.z - low.z) / size.z)
    }, low, high)
}

/** The mask of [cells]' part holding those where [test] holds. */
fun maskOf(character: Character, body: AssembledCharacter, cells: PartCells, test: (CellAt) -> Boolean): SurfaceMask {
    val chart = bodyChart(character, body)
    val held = cells.cells.filter(test).mapTo(HashSet()) { it.cell }
    return SurfaceMask.build(chart) { p, c -> p == cells.part && c in held }
}

/** A painted shape: its name and the areas it paints, in turn, each a mask. */
class SpikeShape(val name: String, val areas: (Character, AssembledCharacter) -> List<SurfaceMask>)

/** How far along a part, in u, a ring cut by a plane tilted [lean] in u from square across the part lies at [v] round it. */
private fun tilt(v: Float, lean: Float) = lean * kotlin.math.cos(2.0 * PI * v).toFloat()

private fun wave(v: Float, waves: Int, height: Float) = height * sin(2.0 * PI * waves * v).toFloat()

/** A brush dab of [radius], a share of the part's height, at the cell [pick] chooses. */
private fun dab(character: Character, body: AssembledCharacter, part: PartId, radius: Float, pick: (CellAt) -> Float): SurfaceMask {
    val cells = partCells(character, body, part)
    val center = cells.cells.maxBy(pick).at
    val r = radius * cells.size.y
    return maskOf(character, body, cells) { (it.at - center).length <= r }
}

val WOMAN_SHAPES: List<SpikeShape> = listOf(
    SpikeShape("slanted-hem") { c, b ->
        val torso = partCells(c, b, WOMAN_TORSO)
        listOf(maskOf(c, b, torso) { it.h > 0.3f + 0.25f * (it.s - 0.5f) })
    },
    SpikeShape("thigh-stripe") { c, b ->
        val leg = partCells(c, b, LEG_L)
        listOf(maskOf(c, b, leg) { abs(it.h - 0.75f - 0.25f * (it.s - 0.5f)) < 0.045f })
    },
    SpikeShape("chest-patch") { c, b ->
        listOf(dab(c, b, WOMAN_TORSO, 0.12f) { if (abs(it.h - 0.7f) < 0.03f && abs(it.s - 0.5f) < 0.2f) it.at.x else -1e9f })
    },
    SpikeShape("forearm-sleeve") { c, b -> listOf(roundStroke(c, b, ARM_L, 0f, 0.72f)) },
    SpikeShape("wavy-boot") { c, b ->
        val leg = partCells(c, b, LEG_L)
        val foot = partCells(c, b, FOOT_L)
        listOf(maskOf(c, b, leg) { it.h < 0.3f + wave(it.v, 3, 0.04f) } + maskOf(c, b, foot) { true })
    },
    SpikeShape("mid-arm-sleeve") { c, b -> listOf(roundStroke(c, b, ARM_L, 0f, 0.4f)) },
    SpikeShape("diagonal-stripe") { c, b ->
        val arm = partCells(c, b, ARM_L)
        listOf(maskOf(c, b, arm) { abs(it.u - 0.55f - tilt(it.v, 0.12f)) < 0.05f })
    },
    SpikeShape("thin-line") { c, b ->
        val arm = partCells(c, b, ARM_L)
        listOf(maskOf(c, b, arm) { abs(it.a - 0.55f - 0.35f * (it.h - 0.5f)) < 0.06f })
    },
    SpikeShape("two-areas") { c, b ->
        val torso = partCells(c, b, WOMAN_TORSO)
        val line = { it: CellAt -> 0.55f + 0.2f * (it.s - 0.5f) }
        listOf(maskOf(c, b, torso) { it.h > line(it) }, maskOf(c, b, torso) { it.h <= line(it) && it.h > 0.2f })
    },
)

val FENNEC_SHAPES: List<SpikeShape> = listOf(
    SpikeShape("slanted-hem") { c, b ->
        val torso = partCells(c, b, FENNEC_TORSO)
        listOf(maskOf(c, b, torso) { it.a > 0.45f + 0.3f * (it.h - 0.5f) })
    },
    SpikeShape("thigh-stripe") { c, b ->
        val leg = partCells(c, b, BACK_LEG_L)
        listOf(maskOf(c, b, leg) { abs(it.h - 0.65f - 0.3f * (it.a - 0.5f)) < 0.07f })
    },
    SpikeShape("chest-patch") { c, b ->
        listOf(dab(c, b, FENNEC_TORSO, 0.18f) { if (abs(it.h - 0.35f) < 0.05f && abs(it.s - 0.5f) < 0.2f) it.at.x else -1e9f })
    },
    SpikeShape("forearm-sleeve") { c, b -> listOf(roundStroke(c, b, FRONT_LEG_L, 0f, 0.55f)) },
    SpikeShape("wavy-boot") { c, b ->
        val leg = partCells(c, b, BACK_LEG_L)
        val paw = partCells(c, b, BACK_PAW_L)
        listOf(maskOf(c, b, leg) { it.h < 0.4f + wave(it.v, 3, 0.06f) } + maskOf(c, b, paw) { true })
    },
    SpikeShape("diagonal-stripe") { c, b ->
        val leg = partCells(c, b, FRONT_LEG_L)
        listOf(maskOf(c, b, leg) { abs(it.u - 0.5f - tilt(it.v, 0.15f)) < 0.06f })
    },
    SpikeShape("two-areas") { c, b ->
        val torso = partCells(c, b, FENNEC_TORSO)
        val line = { it: CellAt -> 0.5f + 0.25f * (it.h - 0.5f) }
        listOf(maskOf(c, b, torso) { it.a > line(it) }, maskOf(c, b, torso) { it.a <= line(it) })
    },
)
