package dev.chieppa.squash.screens.look.spike

import dev.chieppa.squash.character.dress.paintedOf
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.surface.HELD
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * How far the cloth of [garment] strays from its painted cells on [session]'s dress: at each vertex of its grown surface lying well
 * within one part's run, whether it is cut in (coverage at least zero) against whether the cell under it is painted; where they differ,
 * how far, in cells, the vertex lies from the nearest cell that agrees. Printed per shape.
 */
fun fidelity(session: CharacterSession, garment: Garment, label: String) {
    val dress = session.sessionSurface.dress.output.value?.dress ?: return println("spike-fit: $label no dress")
    val worn = dress.garments.firstOrNull { it.cover.garment.id == garment.id } ?: return println("spike-fit: $label not worn")
    val character = session.document.value.character
    val cells = areaCells(garment).fold(HashMap<PartId, BooleanArray>()) { all, area ->
        for ((part, held) in area) {
            val into = all.getOrPut(part) { BooleanArray(held.size) }
            for (i in held.indices) if (held[i]) into[i] = true
        }
        all
    }
    val byPart = HashMap<PartId, BooleanArray>()
    for ((part, held) in cells) {
        byPart[part] = held
        character.part(part)?.let { character.twinOf(it) }?.let { byPart[it.id] = held }
    }
    if (printGrids) printGrid(worn, cells, label)
    val mesh = worn.surface.mesh
    val runs = worn.surface.field.runs
    var agree = 0
    var over = 0
    var under = 0
    val strays = ArrayList<Float>()
    var fieldOver = 0
    var fieldUnder = 0
    var cutIn = 0
    var cutOut = 0
    val fieldStrays = ArrayList<Float>()
    val cutRows = java.util.TreeMap<Int, Int>()
    for (v in 0 until mesh.vertexCount) {
        var best = -1
        var share = 0f
        for (k in 0 until HELD) {
            val m = mesh.held[v * HELD + k]
            if (m < 0) continue
            if (mesh.heldShares[v * HELD + k] > share) {
                share = mesh.heldShares[v * HELD + k]
                best = k
            }
        }
        if (best < 0 || share < 0.95f) continue
        val run = runs[mesh.held[v * HELD + best]]
        if (!run.first) continue
        val held = byPart[run.partId] ?: continue
        val u = mesh.heldU[v * HELD + best]
        val vv = mesh.heldV[v * HELD + best]
        if (u < 0f || u > 1f) continue
        val x = u * CHART_ROWS
        val y = (vv - floor(vv)) * CHART_COLUMNS
        val cell = minOf(x.toInt(), CHART_ROWS - 1) * CHART_COLUMNS + minOf(y.toInt(), CHART_COLUMNS - 1)
        val painted = held[cell]
        val covered = worn.coverage[v] >= 0f
        val cloth = worn.cover.clothOf(run.partId)
        val fieldSays = cloth?.let { it.at(u, vv) >= 0.5f }
        if (fieldSays != null) {
            if (fieldSays != painted) {
                if (fieldSays) fieldOver++ else fieldUnder++
                fieldStrays += nearestAgreeing(held, x, y, fieldSays).let { if (it == Float.MAX_VALUE) CHART_ROWS.toFloat() else it }
            } else if (fieldSays != covered) {
                if (covered) cutOut++ else cutIn++
                cutRows.merge(x.toInt(), 1, Int::plus)
            }
        }
        if (painted == covered) {
            agree++
            continue
        }
        if (covered) over++ else under++
        strays += nearestAgreeing(held, x, y, covered).let { if (it == Float.MAX_VALUE) CHART_ROWS.toFloat() else it }
    }
    strays.sort()
    fieldStrays.sort()
    fun fq(q: Double) = if (fieldStrays.isEmpty()) 0f else fieldStrays[((fieldStrays.size - 1) * q).toInt()]
    println(
        "spike-field: $label field vs paint over=$fieldOver under=$fieldUnder stray p50=%.2f p95=%.2f max=%.2f cells; coverage vs field: cut where field covers=$cutIn, covered where field does not=$cutOut, by row $cutRows"
            .format(fq(0.5), fq(0.95), fieldStrays.lastOrNull() ?: 0f),
    )
    val tolerance = worn.surface.mesh.cell * 0.5f
    val hemPoints = worn.hems.sumOf { it.size }
    val handles = worn.hems.sumOf { curve -> simplified(curve.map { it.position }, tolerance) }
    val seamCurves = worn.inkedJoins
    val body = checkNotNull(session.assembly.output.value)
    val painted = paintedOf(character, body, garment)
    println("spike-edges: $label paint's edges=${painted.edges.size} edge points=${painted.edges.sumOf { it.edge.curve.points.size }}")
    println("spike-hem: $label hems=${worn.hems.size} hem points=$hemPoints handles at half a cell=$handles seams=${seamCurves.size} seam handles=${seamCurves.sumOf { c -> simplified(c.map { it.position }, tolerance) }}")
    fun at(q: Double) = if (strays.isEmpty()) 0f else strays[((strays.size - 1) * q).toInt()]
    println(
        "spike-fit: $label vertices=${agree + over + under} agree=$agree over=$over under=$under stray p50=%.2f p95=%.2f max=%.2f cells; strays over 1 cell=%d"
            .format(at(0.5), at(0.95), strays.lastOrNull() ?: 0f, strays.count { it > 1f }),
    )
}

/** How far, in cells, (x, y) lies from the nearest cell whose painted state is [painted]. */
private fun nearestAgreeing(held: BooleanArray, x: Float, y: Float, painted: Boolean): Float {
    var best = Float.MAX_VALUE
    for (r in 0 until CHART_ROWS) for (c in 0 until CHART_COLUMNS) {
        if (held[r * CHART_COLUMNS + c] != painted) continue
        val dx = maxOf(r - x, 0f, x - (r + 1))
        var dyRaw = y - (c + 0.5f)
        dyRaw -= CHART_COLUMNS * Math.round(dyRaw / CHART_COLUMNS).toFloat()
        val dy = maxOf(kotlin.math.abs(dyRaw) - 0.5f, 0f)
        val d = sqrt(dx * dx + dy * dy)
        if (d < best) best = d
    }
    return best
}

/** How many points a curve keeps simplified to within [tolerance] (Douglas and Peucker). */
private fun simplified(points: List<dev.chieppa.squash.geom.Vec3>, tolerance: Float): Int {
    if (points.size < 3) return points.size
    fun keep(from: Int, to: Int): Int {
        if (to - from < 2) return 0
        val a = points[from]
        val d = points[to] - a
        val len2 = d dot d
        var worst = -1f
        var at = -1
        for (i in from + 1 until to) {
            val p = points[i] - a
            val t = if (len2 > 0f) ((p dot d) / len2).coerceIn(0f, 1f) else 0f
            val off = (p - d * t).length
            if (off > worst) { worst = off; at = i }
        }
        return if (worst <= tolerance) 0 else keep(from, at) + 1 + keep(at, to)
    }
    return 2 + keep(0, points.size - 1)
}

/** Whether [fidelity] also prints each painted part's chart, a row of cells a line. */
var printGrids = false

/** Each painted part's cells of [garment] beside its field: '#' painted and covered by the field, 'x' painted only, 'o' field only, '.' neither. */
fun printGrid(worn: dev.chieppa.squash.character.surface.SurfaceGarment, cells: Map<PartId, BooleanArray>, label: String) {
    for ((part, held) in cells) {
        val cloth = worn.cover.clothOf(part)
        println("spike-grid: $label ${part.value} field=${cloth != null}")
        for (r in 0 until CHART_ROWS) {
            val line = StringBuilder()
            for (c in 0 until CHART_COLUMNS) {
                val p = held[r * CHART_COLUMNS + c]
                val f = cloth?.let { it.at((r + 0.5f) / CHART_ROWS, (c + 0.5f) / CHART_COLUMNS) >= 0.5f } ?: false
                line.append(if (p && f) '#' else if (p) 'x' else if (f) 'o' else '.')
            }
            println("spike-grid: %2d %s".format(r, line))
        }
    }
}
