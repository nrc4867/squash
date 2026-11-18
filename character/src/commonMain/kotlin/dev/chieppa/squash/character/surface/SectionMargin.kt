package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.surface.HELD
import kotlin.math.abs
import kotlin.math.sqrt

/** How far, in cells, a run's section may read from the curve where its shares truly tie: the waver of the field reads it is taken from. */
private const val SECTION_WAVER_CELLS = 0.09f

/** How far past zero, in cells, a run's section is held where its zero is lost in the waver. */
private const val SECTION_MARGIN_CELLS = 0.06f

/** How wide, in cells, the waver spreads the zero across the surface where the section is held its whole margin past it, and at most. */
private const val WHOLE_SPREAD_CELLS = 1f

/** How wide, in cells, the waver spreads the zero across the surface where the section is held at zero, and at least. */
private const val NO_SPREAD_CELLS = 0.5f

/** How far either side of zero, in cells, a section is read for where its zero wavers; past it the section is left as it is. */
private const val SECTION_BAND_CELLS = 1f

/** How much of a cell squared steadies a section's slope fitted over a vertex's ring. */
private const val SLOPE_STEADY = 0.01f

/**
 * A run's section on [chart] held a little past zero where its zero is lost in the waver of the reads it is taken from, and nowhere else.
 *
 * Where a run and a neighbour meet at a slant, the section falls steeply through zero and its zero is the curve where their surfaces
 * cross, the curve the surface's ink follows. Where they meet nearly level and share the surface almost equally over a stretch, the
 * section lies within its waver of zero across more than a cell, and its zero breaks into steps and islands. How wide the waver spreads
 * the zero is the waver over the section's slope along the surface; where that spread passes [NO_SPREAD_CELLS] the section is held past
 * zero by a share of [SECTION_MARGIN_CELLS] growing to the whole of it at [WHOLE_SPREAD_CELLS], so it ends where the shares part.
 * Where the section is within its waver of zero and the neighbour it gives the surface to changes within a vertex's ring, three runs
 * meet: the run's share narrows there to a wedge between two neighbours finer than a cell, whose tip breaks off, and the section is held
 * its whole margin past zero.
 */
class SectionMargin(private val chart: SurfaceChart) {
    private val mesh = chart.mesh
    private val cell = mesh.cell

    /** [section], the section of the runs [members] laid by [SurfaceChart.layOn] over [triangles], held past zero where its zero wavers. */
    fun widened(section: FloatArray, members: List<Int>, triangles: IntArray): FloatArray {
        val held = BooleanArray(chart.corners)
        for (member in members) for (v in chart.vertices[member]) held[v] = true
        val margin = cell * SECTION_MARGIN_CELLS
        val out = section.copyOf()
        val ceded = IntArray(chart.corners) { v -> if (held[v]) cededTo(v, held, members) else -1 }
        for (v in 0 until chart.corners) {
            if (!held[v] || abs(section[v]) >= cell * SECTION_BAND_CELLS) continue
            if (abs(section[v]) < cell * SECTION_WAVER_CELLS && meets(v, ceded, section, held)) {
                out[v] += margin
                continue
            }
            val slope = slopeAt(v, section, held)
            if (slope <= 0f) {
                out[v] += margin
                continue
            }
            val spread = SECTION_WAVER_CELLS / slope
            val share = ((spread - NO_SPREAD_CELLS) / (WHOLE_SPREAD_CELLS - NO_SPREAD_CELLS)).coerceIn(0f, 1f)
            out[v] += margin * share
        }
        val indices = chart.indices
        for (tri in triangles) {
            val middle = indices[tri * 3 + 2]
            if (middle < chart.corners) continue
            val quad = tri / 4
            var sum = 0f
            for (k in 0 until 4) sum += out[indices[(quad * 4 + k) * 3]]
            out[middle] = sum / 4f
        }
        return out
    }

    /** The run a vertex [held] flags gives the surface to, the other run it holds with the lowest own field there; -1 where it holds none. */
    private fun cededTo(v: Int, held: BooleanArray, members: List<Int>): Int {
        var least = Float.MAX_VALUE
        var run = -1
        for (k in 0 until HELD) {
            val other = mesh.held[v * HELD + k]
            if (other < 0 || other in members) continue
            val own = mesh.heldOwn[v * HELD + k]
            if (own < least) {
                least = own
                run = other
            }
        }
        return run
    }

    /** Whether a vertex in [v]'s ring that [held] flags, its section not held at the chart's reach, gives the surface to another run than [v]. */
    private fun meets(v: Int, ceded: IntArray, section: FloatArray, held: BooleanArray): Boolean {
        if (ceded[v] < 0) return false
        for (j in chart.linkStart[v] until chart.linkStart[v + 1]) {
            val u = chart.links[j]
            if (held[u] && abs(section[u]) < chart.reach && ceded[u] >= 0 && ceded[u] != ceded[v]) return true
        }
        return false
    }

    /**
     * How steeply [section] falls along the surface at corner [v], fitted over [v]'s ring among the corners [held] flags whose section is
     * not held at the chart's reach: how much it falls in body units for a body unit along the surface. Infinite where the ring holds too few to fit.
     */
    private fun slopeAt(v: Int, section: FloatArray, held: BooleanArray): Float {
        val p = mesh.positions
        val nx = mesh.normals[v * 3]
        val ny = mesh.normals[v * 3 + 1]
        val nz = mesh.normals[v * 3 + 2]
        // One axis across the surface at v, from whichever world axis lies least along its normal, and the other at right angles to both.
        val (ax, ay, az) = when {
            abs(nx) <= abs(ny) && abs(nx) <= abs(nz) -> Triple(0f, -nz, ny)
            abs(ny) <= abs(nz) -> Triple(nz, 0f, -nx)
            else -> Triple(-ny, nx, 0f)
        }
        val length = sqrt(ax * ax + ay * ay + az * az)
        if (length == 0f) return Float.POSITIVE_INFINITY
        val t1x = ax / length
        val t1y = ay / length
        val t1z = az / length
        val t2x = ny * t1z - nz * t1y
        val t2y = nz * t1x - nx * t1z
        val t2z = nx * t1y - ny * t1x
        var aa = 0f
        var ab = 0f
        var bb = 0f
        var ra = 0f
        var rb = 0f
        var count = 0
        for (j in chart.linkStart[v] until chart.linkStart[v + 1]) {
            val u = chart.links[j]
            if (!held[u] || abs(section[u]) >= chart.reach) continue
            val dx = p[u * 3] - p[v * 3]
            val dy = p[u * 3 + 1] - p[v * 3 + 1]
            val dz = p[u * 3 + 2] - p[v * 3 + 2]
            val a = dx * t1x + dy * t1y + dz * t1z
            val b = dx * t2x + dy * t2y + dz * t2z
            val ds = section[u] - section[v]
            aa += a * a
            ab += a * b
            bb += b * b
            ra += a * ds
            rb += b * ds
            count++
        }
        if (count < 3) return Float.POSITIVE_INFINITY
        val steady = cell * cell * SLOPE_STEADY
        aa += steady
        bb += steady
        val det = aa * bb - ab * ab
        if (det <= 0f) return Float.POSITIVE_INFINITY
        val ga = (ra * bb - rb * ab) / det
        val gb = (rb * aa - ra * ab) / det
        return sqrt(ga * ga + gb * gb)
    }
}
