package dev.chieppa.squash.geom

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

    fun moved(by: Vec3) = BodyBox(low + by, high + by)

    fun union(other: BodyBox) = BodyBox(
        Vec3(minOf(low.x, other.low.x), minOf(low.y, other.low.y), minOf(low.z, other.low.z)),
        Vec3(maxOf(high.x, other.high.x), maxOf(high.y, other.high.y), maxOf(high.z, other.high.z)),
    )
}

/**
 * What a built part shows, relative to its socket plane: the GPU's [mesh] and each [BodyView]'s [silhouettes] of its swept
 * patches, mirrored across the body's plane when [mirrored], and how far it reaches. [patches] stay in the part's own axes, so a socket on
 * it reads the same (u, v) on either side. Never changes once made.
 */
class PartSurface(
    val patches: List<Patch>,
    val mirrored: Boolean,
    val mesh: RenderMesh,
    val silhouettes: Map<BodyView, List<List<Vec3>>>,
    val box: BodyBox?,
    val extents: PartExtents?,
) {
    /** A local point or direction of [patches] as this surface shows it. */
    fun shown(local: Vec3): Vec3 = if (mirrored) local.mirrored else local

    companion object {
        /** The surface of [built], whose axes are [axes]. [checkCancelled] runs between its steps and throws to stop a stale run. */
        fun of(built: PartMesh, axes: PartAxes, mirrored: Boolean, checkCancelled: () -> Unit = {}): PartSurface {
            val own = RenderMesh.of(built)
            checkCancelled()
            val mesh = if (mirrored) own.mirrored() else own
            val swept = PartMesh(built.part, built.patches.filter { it.patch.slices != null })
            val silhouettes = BodyView.entries.associateWith { view ->
                checkCancelled()
                if (mirrored) silhouette(swept, view.toViewer.mirrored).map { path -> path.map { it.mirrored } }
                else silhouette(swept, view.toViewer)
            }
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
