package dev.chieppa.squash.geom

import kotlin.math.abs

/** A range along one axis. */
data class Span(val min: Float, val max: Float) {
    val length: Float get() = max - min
    val center: Float get() = (min + max) / 2f

    /** The range seen along the axis pointing the other way. */
    operator fun unaryMinus() = Span(-max, -min)
}

/** How far a part reaches along each of its axes, from its socket plane. */
data class PartExtents(val along: Span, val up: Span, val side: Span)

/** A box lined up with the body's axes. */
data class BodyBox(val low: Vec3, val high: Vec3) {
    val center: Vec3 get() = (low + high) * 0.5f
    val size: Vec3 get() = high - low

    /** The radius of a sphere around [center] that holds the box. */
    val radius: Float get() = size.length / 2f

    val corners: List<Vec3>
        get() = listOf(low.x, high.x).flatMap { x -> listOf(low.y, high.y).flatMap { y -> listOf(low.z, high.z).map { z -> Vec3(x, y, z) } } }

    /** How long the box's shadow is on a line along [axis], a unit direction. */
    fun spanAlong(axis: Vec3): Float = abs(size.x * axis.x) + abs(size.y * axis.y) + abs(size.z * axis.z)

    fun moved(by: Vec3) = BodyBox(low + by, high + by)

    fun union(other: BodyBox) = BodyBox(
        Vec3(minOf(low.x, other.low.x), minOf(low.y, other.low.y), minOf(low.z, other.low.z)),
        Vec3(maxOf(high.x, other.high.x), maxOf(high.y, other.high.y), maxOf(high.z, other.high.z)),
    )
}

/** A part's outlines as paths of points: looking straight [along], [up] and [side] its own axes, and from the body's side. */
class PartSilhouettes(
    val along: List<List<Vec3>>,
    val up: List<List<Vec3>>,
    val side: List<List<Vec3>>,
    val bodySide: List<List<Vec3>>,
)

/**
 * What a built part shows, relative to its socket plane: the GPU's [mesh] and the [silhouettes] of its swept patches,
 * mirrored across the body's plane when [mirrored], and how far it reaches. [patches] stay in the part's own axes, so a socket on
 * it reads the same (u, v) on either side. Never changes once made.
 */
class PartSurface(
    val patches: List<Patch>,
    val mirrored: Boolean,
    val mesh: RenderMesh,
    val silhouettes: PartSilhouettes,
    val box: BodyBox?,
    val extents: PartExtents?,
) {
    /** A local point or direction of [patches] as this surface shows it. */
    fun shown(local: Vec3): Vec3 = if (mirrored) local.mirrored else local

    /** The grid of the patch named [name] as this surface's mesh holds it, or null when it has none so named. */
    fun grid(name: String): PatchGrid? {
        var first = 0
        for (patch in patches) {
            if (patch.name == name) return PatchGrid(patch, mesh, first, mirrored)
            first += patch.nu * patch.nv
        }
        return null
    }

    /** How long the part's axis is: its first swept patch's length for a whole run of u from 0 to 1, or 0 with none. */
    val axisLength: Float get() = patches.firstOrNull { it.slices != null }?.lengthPerU() ?: 0f

    companion object {
        /** The surface of [built], whose axes are [axes]. [checkCancelled] runs between its steps and throws to stop a stale run. */
        fun of(built: PartMesh, axes: PartAxes, mirrored: Boolean, checkCancelled: () -> Unit = {}): PartSurface {
            val own = RenderMesh.of(built)
            checkCancelled()
            val mesh = if (mirrored) own.mirrored() else own
            val swept = PartMesh(built.part, built.patches.filter { it.patch.slices != null })
            fun outline(ownToViewer: Vec3): List<List<Vec3>> {
                checkCancelled()
                val paths = silhouette(swept, ownToViewer)
                return if (mirrored) paths.map { path -> path.map { it.mirrored } } else paths
            }
            val bodySide = BodyView.SIDE.toViewer.let { if (mirrored) it.mirrored else it }
            val silhouettes = PartSilhouettes(outline(axes.along), outline(axes.up), outline(axes.side), outline(bodySide))
            return PartSurface(built.patches.map { it.patch }, mirrored, mesh, silhouettes, boxOf(mesh), extentsOf(own, axes))
        }

        private fun boxOf(mesh: RenderMesh): BodyBox? {
            if (mesh.vertexCount == 0) return null
            val x = spanOf(mesh, Vec3.X)
            val y = spanOf(mesh, Vec3.Y)
            val z = spanOf(mesh, Vec3.Z)
            return BodyBox(Vec3(x.min, y.min, z.min), Vec3(x.max, y.max, z.max))
        }

        private fun extentsOf(mesh: RenderMesh, axes: PartAxes): PartExtents? {
            if (mesh.vertexCount == 0) return null
            return PartExtents(spanOf(mesh, axes.along), spanOf(mesh, axes.up), spanOf(mesh, axes.side))
        }

        private fun spanOf(mesh: RenderMesh, axis: Vec3): Span {
            var min = Float.POSITIVE_INFINITY
            var max = Float.NEGATIVE_INFINITY
            for (v in 0 until mesh.vertexCount) {
                val value = mesh.position(v) dot axis
                if (value < min) min = value
                if (value > max) max = value
            }
            return Span(min, max)
        }
    }
}
