package dev.chieppa.squash.geom

/** Ends of two runs this close, as a share of the grid's own reach, are one place. */
private const val JOIN_SHARE = 1e-4f

/** A triangle whose edges' cross product is shorter than this has closed to nothing. */
private const val FACE_AREA = 1e-10f

/** A place on a contour: where it lies in body space and the weight of the line there. */
data class ContourPoint(val position: Vec3, val weight: Float)

/**
 * A contour in order along it. A [closed] one repeats its first point last. Runs of one grid with the same [piece] are connected, where
 * the mesh's edges branch.
 */
class ContourRun(val points: List<ContourPoint>, val closed: Boolean, val piece: Int = 0)

/**
 * A patch's grid as a surface shows it in body space: where each point lies, the outward normal of each triangle of the mesh over it,
 * two to a cell, and the weight of a line through each point. What a line follows and a tool picks on. Never changes once made.
 */
class LineGrid private constructor(
    val patch: Patch,
    private val positions: Array<Vec3>,
    private val faceNormals: Array<Vec3>,
    private val weights: FloatArray,
    private val center: Vec3,
    private val reach: Float,
    private val hidden: BooleanArray? = null,
) {
    /** This grid with its line weight at each point times [weightAt] there. */
    fun weighted(weightAt: (Vec3) -> Float): LineGrid =
        LineGrid(patch, positions, faceNormals, FloatArray(positions.size) { weights[it] * weightAt(positions[it]) }, center, reach, hidden)

    /** This grid with its line weight at each point times [scale] of that point, one value a grid point. */
    fun scaled(scale: FloatArray): LineGrid {
        require(scale.size == positions.size) { "${scale.size} scales for ${positions.size} points" }
        return LineGrid(patch, positions, faceNormals, FloatArray(positions.size) { weights[it] * scale[it] }, center, reach, hidden)
    }

    /** This grid with no contour along an edge both of whose points [hidden] marks, as where a garment hides the skin. */
    fun hiding(hidden: BooleanArray): LineGrid {
        require(hidden.size == positions.size) { "${hidden.size} flags for ${positions.size} points" }
        return LineGrid(patch, positions, faceNormals, weights, center, reach, hidden)
    }

    /**
     * Where this surface turns edge on to a viewer that [toViewer] gives the direction of from any point: the mesh's edges between a
     * triangle facing the viewer and one facing away, so a line along them lies on the edge the fill shows, sawteeth and all. A closed patch's end rings count against the faces that close them. An edge of an open
     * patch has one triangle and is no contour, unless its faces meet round it ([Patch.meetsAround]), as a sheet's do at its edge; the end
     * rings of a patch with [Patch.openEnds] close nothing. Ends of runs meeting at a pole are joined. An edge between two hidden points
     * is no contour, nor is one between a cell with a hidden corner and a cell with none, the edge where the shown surface stops, nor any
     * edge of a cell with a [Patch.buried] corner, which has no faces.
     */
    fun contours(toViewer: (Vec3) -> Vec3): List<ContourRun> {
        val facing = BooleanArray(faceNormals.size) { face -> (faceNormals[face] dot toViewer(positions[faceCorner(face)])) > 0f }
        val hidden = hidden
        val edges = silhouetteEdges(facing).let { all -> if (hidden == null) all else all.filter { (a, b) -> !hidden[a] || !hidden[b] } }
        val pieces = connectedPieces(edges)
        val runs = walkedEdges(edges).map { run -> IndexedRun(run.map { ContourPoint(positions[it], weights[it]) }, pieces.getValue(run.first())) }
        return joinedAtEnds(runs, reach * JOIN_SHARE)
    }

    private fun faceCorner(face: Int): Int {
        val cells = (patch.nu - 1) * patch.segments * 2
        if (face >= cells) return patch.index(if (face == cells) 0 else patch.nu - 1, 0)
        val cell = face / 2
        return patch.index(cell / patch.segments, cell % patch.segments)
    }

    /** Each mesh edge with one triangle either way, as its two grid points, where [facing] differs across it. */
    private fun silhouetteEdges(facing: BooleanArray): List<Pair<Int, Int>> = buildList {
        val segments = patch.segments
        val nv = patch.nv
        fun first(ring: Int, segment: Int) = (ring * segments + segment) * 2
        fun second(ring: Int, segment: Int) = first(ring, segment) + 1
        val startCap = (patch.nu - 1) * segments * 2
        val endCap = startCap + 1
        val closesEnds = patch.meetsAround && !patch.openEnds
        fun shown(ring: Int, segment: Int): Boolean {
            val held = hidden ?: return true
            val next = (segment + 1) % nv
            return !held[patch.index(ring, segment)] && !held[patch.index(ring, next)] && !held[patch.index(ring + 1, segment)] && !held[patch.index(ring + 1, next)]
        }
        val buried = patch.buried
        fun built(ring: Int, segment: Int): Boolean {
            val held = buried ?: return true
            val next = (segment + 1) % nv
            return !held[patch.index(ring, segment)] && !held[patch.index(ring, next)] && !held[patch.index(ring + 1, segment)] && !held[patch.index(ring + 1, next)]
        }
        for (ring in 0 until patch.nu - 1) {
            for (segment in 0 until segments) {
                if (!built(ring, segment)) continue
                val a = patch.index(ring, segment)
                val next = (segment + 1) % nv
                val c = patch.index(ring + 1, segment)
                val d = patch.index(ring + 1, next)
                val here = shown(ring, segment)
                if (facing[first(ring, segment)] != facing[second(ring, segment)]) add(a to d)
                if (ring > 0 && built(ring - 1, segment) && here == shown(ring - 1, segment) && facing[first(ring, segment)] != facing[second(ring - 1, segment)]) add(a to patch.index(ring, next))
                val before = if (segment > 0) segment - 1 else if (patch.meetsAround) segments - 1 else -1
                if (before >= 0 && built(ring, before) && here == shown(ring, before) && facing[second(ring, segment)] != facing[first(ring, before)]) add(a to c)
                if (closesEnds && ring == 0 && facing[first(ring, segment)] != facing[startCap]) add(a to patch.index(ring, next))
                if (closesEnds && ring == patch.nu - 2 && facing[second(ring, segment)] != facing[endCap]) add(c to d)
            }
        }
    }

    companion object {
        /** [patch]'s grid as a surface shows it through [shown] from its own axes, moved by [origin]. */
        fun of(patch: Patch, origin: Vec3, shown: (Vec3) -> Vec3): LineGrid {
            val count = patch.nu * patch.nv
            val positions = Array(count) { origin + shown(patch.point(it)) }
            val low = Vec3(positions.minOf { it.x }, positions.minOf { it.y }, positions.minOf { it.z })
            val high = Vec3(positions.maxOf { it.x }, positions.maxOf { it.y }, positions.maxOf { it.z })
            return LineGrid(patch, positions, faceNormals(patch, shown), FloatArray(count) { 1f }, (low + high) * 0.5f, (high - low).length * 0.5f)
        }
    }
}

/**
 * The outward normal of each triangle of [patch] as [shown] shows it, two to a cell, the same split as [LineGrid.hit] takes, then of the
 * faces closing its first and last rings. A triangle that has closed to nothing at a pole takes its cell's other.
 */
private fun faceNormals(patch: Patch, shown: (Vec3) -> Vec3): Array<Vec3> {
    val cells = (patch.nu - 1) * patch.segments * 2
    val normals = Array(cells + 2) { Vec3.ZERO }
    normals[cells] = shown(ringFace(patch, 0, 1))
    normals[cells + 1] = shown(ringFace(patch, patch.nu - 1, patch.nu - 2))
    for (ring in 0 until patch.nu - 1) {
        for (segment in 0 until patch.segments) {
            val next = (segment + 1) % patch.nv
            val a = patch.point(patch.index(ring, segment))
            val b = patch.point(patch.index(ring, next))
            val c = patch.point(patch.index(ring + 1, segment))
            val d = patch.point(patch.index(ring + 1, next))
            val first = (d - a) cross (b - a)
            val second = (c - a) cross (d - a)
            val cell = (ring * patch.segments + segment) * 2
            normals[cell] = shown(if (first.length > FACE_AREA) first.normalized() else second.normalized())
            normals[cell + 1] = shown(if (second.length > FACE_AREA) second.normalized() else first.normalized())
        }
    }
    return normals
}

/** The outward normal of the flat face closing [ring], which faces away from the ring [inner] beside it; zero where the ring is a pole. */
private fun ringFace(patch: Patch, ring: Int, inner: Int): Vec3 {
    var area = Vec3.ZERO
    var center = Vec3.ZERO
    var middle = Vec3.ZERO
    val first = patch.point(patch.index(ring, 0))
    for (segment in 0 until patch.nv) {
        val here = patch.point(patch.index(ring, segment))
        area += (here - first) cross (patch.point(patch.index(ring, (segment + 1) % patch.nv)) - first)
        center += here
        middle += patch.point(patch.index(inner, segment))
    }
    if (area.length <= FACE_AREA) return Vec3.ZERO
    val normal = area.normalized()
    return if ((normal dot (center - middle)) >= 0f) normal else normal * -1f
}
