package dev.chieppa.squash.geom

import kotlin.math.floor

/** One cross-section of a swept patch. Its section is scaled by half of [width] and [height]. */
data class Slice(
    val u: Float,
    val frame: Frame,
    val height: Float,
    val width: Float,
    val section: ClosedSpline,
    /** The ring's shading frame: its section's axes turned to the axis's direction here. Proxy normals are built in it. */
    val proxyNormalFrame: Mat3,
) {
    /** The point of this ring at [v] around it, as a patch lofted from it holds it. */
    fun pointAt(v: Float): Vec3 {
        val around = section.pointAt(v - floor(v))
        return frame.center + frame.side * (around.x * width / 2f) + frame.up * (around.y * height / 2f)
    }
}

/**
 * A named surface: a fixed grid of [nu] rings by [nv] points, closed in v for a tube and open for a sheet. A ring whose
 * points all coincide is a pole. [slices] is present for swept patches. [boneU] is the u along the part that every
 * vertex takes its bone from, for a patch whose own u does not run along the part. [spanned] says, for a patch spanned between two
 * others, where on each of them every vertex takes its bones from. [proxyNormals] holds a swept patch's shading normal per point,
 * three values each, as the slice build derives them; a patch without them shades by its mesh normal. [us] gives a patch with no slices
 * a u range of its own, one u a ring. A patch with [openEnds] meets other surfaces at its first and last rings, which close nothing.
 * [buried] marks the points that lie inside the part as built, where another of its patches shows the surface: no face with one of
 * them as a corner is built. [rides] says, for a patch whose bones follow each vertex's place, where it takes them from.
 */
class Patch(
    val name: String,
    val nu: Int,
    val nv: Int,
    val closedV: Boolean,
    val slices: List<Slice>?,
    val points: FloatArray,
    val boneU: Float? = null,
    val proxyNormals: FloatArray? = null,
    val spanned: Spanned? = null,
    val us: FloatArray? = null,
    val openEnds: Boolean = false,
    val buried: BooleanArray? = null,
    val rides: Rides? = null,
) {
    init {
        require(points.size == nu * nv * COMPONENTS) { "Patch $name holds ${points.size} values for a $nu by $nv grid" }
        require(us == null || us.size == nu) { "Patch $name holds ${us?.size} us for $nu rings" }
        require(spanned == null || spanned.vertexCount == nu * nv) { "Patch $name spans ${spanned?.vertexCount} vertices for a $nu by $nv grid" }
        require(buried == null || buried.size == nu * nv) { "Patch $name buries ${buried?.size} of a $nu by $nv grid" }
        require(rides == null || rides.vertexCount == nu * nv) { "Patch $name rides ${rides?.vertexCount} vertices for a $nu by $nv grid" }
    }

    /** The u along the part that the vertex at [local] takes its bone from. */
    fun boneUOf(local: Int): Float = boneU ?: spanned?.u(local) ?: ringUs[local / nv]

    /** The u of each ring: its slice's, its own [us], or evenly spaced from 0 to 1. */
    val ringUs: FloatArray by lazy { FloatArray(nu) { slices?.get(it)?.u ?: us?.get(it) ?: (it.toFloat() / (nu - 1)) } }

    /** How many spans of grid run around v: the wrap to the first point counts on a closed patch. */
    val segments: Int get() = if (closedV) nv else nv - 1

    /**
     * Whether the surface goes all the way round: closed in v, or open with its first and last columns on one edge, as a sheet's
     * two faces meet there. Such an edge turns edge on like any other place round a closed patch.
     */
    val meetsAround: Boolean by lazy {
        closedV || (0 until nu).all { ring -> (point(index(ring, 0)) - point(index(ring, nv - 1))).length < POLE_TOLERANCE }
    }

    /** Whether the points of [ring] all coincide. */
    fun isPole(ring: Int): Boolean {
        val first = point(index(ring, 0))
        return (1 until nv).all { (point(index(ring, it)) - first).length < POLE_TOLERANCE }
    }

    fun index(ring: Int, segment: Int): Int = ring * nv + segment

    fun point(index: Int): Vec3 =
        Vec3(points[index * COMPONENTS], points[index * COMPONENTS + 1], points[index * COMPONENTS + 2])

    /**
     * This closed patch cut open at v = 0: a column more, the last on the first, so v = 0 and v = 1 lie on one edge and the patch is a
     * sheet whose two faces meet there. Every point and shading normal stays where it was. An open patch is returned as it is.
     */
    fun openedInV(): Patch {
        if (!closedV) return this
        val columns = nv + 1
        fun reopened(values: FloatArray) = FloatArray(nu * columns * COMPONENTS) { i ->
            val point = i / COMPONENTS
            values[((point / columns) * nv + (point % columns) % nv) * COMPONENTS + i % COMPONENTS]
        }
        val buriedAgain = buried?.let { flags -> BooleanArray(nu * columns) { flags[(it / columns) * nv + (it % columns) % nv] } }
        return Patch(
            name, nu, columns, closedV = false, slices, reopened(points), boneU, proxyNormals?.let(::reopened), spanned?.reopened(nu, nv), us, openEnds,
            buriedAgain, rides?.reopened(nu, nv),
        )
    }

    /** This patch shading by [normals], three values per point. */
    fun withProxyNormals(normals: FloatArray) = Patch(name, nu, nv, closedV, slices, points, boneU, normals, spanned, us, openEnds, buried, rides)

    /** This patch meeting other surfaces at its end rings, which close nothing. */
    fun withOpenEnds() = Patch(name, nu, nv, closedV, slices, points, boneU, proxyNormals, spanned, us, true, buried, rides)

    /** This patch with [buried] points and the bones of each vertex taken from [rides]. */
    fun withBuried(buried: BooleanArray?, rides: Rides?) = Patch(name, nu, nv, closedV, slices, points, boneU, proxyNormals, spanned, us, openEnds, buried, rides)

    /** This patch with its points moved to [moved] and its shading normals to [normals], everything addressed by u and v kept. */
    fun withPoints(moved: FloatArray, normals: FloatArray?) = Patch(name, nu, nv, closedV, slices, moved, boneU, normals, spanned, us, openEnds, buried, rides)

    override fun equals(other: Any?): Boolean =
        other is Patch && name == other.name && nu == other.nu && nv == other.nv && closedV == other.closedV &&
            slices == other.slices && points.contentEquals(other.points) && boneU == other.boneU && spanned == other.spanned &&
            us.contentEquals(other.us) && openEnds == other.openEnds && buried.contentEquals(other.buried) && rides == other.rides

    override fun hashCode(): Int = 31 * (31 * name.hashCode() + nu) + points.contentHashCode()

    companion object {
        const val COMPONENTS = 3
        private const val POLE_TOLERANCE = 1e-6f
    }
}
