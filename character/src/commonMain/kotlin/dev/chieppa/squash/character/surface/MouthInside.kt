package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.TriangleSplits
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh

/** The slot a mouth built open paints its inside in where its mouth names no inner fill. */
val MOUTH_INSIDE_FALLBACK = SlotName("ink")

/**
 * The inside of each mouth built open on [surface] ([CharacterField.openJaws]), in its head's slot from [slots], else
 * [MOUTH_INSIDE_FALLBACK]: everywhere [OpenJaw.inside] reads the surface as the cut's faces or the lining, cut along that value's zero as a
 * smooth curve, each triangle the curve may cross split [PAINT_SPLITS] by [PAINT_SPLITS] with the value read again at each new vertex.
 * The inside is decided by the shape alone, never by which run holds a vertex.
 */
fun mouthInsideLayers(surface: CharacterSurface, slots: (OpenJaw) -> SlotName?): List<PaintLayer> = surface.field.openJaws.mapNotNull { jaw ->
    val chart = surface.chart
    val members = surface.field.runs.indices.filter { surface.field.runs[it].partId == jaw.part && surface.field.runs[it].jawSide != null }
    val triangles = chart.trianglesOf(members)
    val onJaw = heldBy(surface.mesh, members.toSet())
    val far = -chart.reach
    val render = surface.render
    val values = FloatArray(chart.count) { far }
    val indices = render.indices
    for (tri in triangles) for (c in 0 until 3) {
        val v = indices[tri * 3 + c]
        if (values[v] != far) continue
        val held = if (v < chart.corners) onJaw[v] else true
        if (held) values[v] = jaw.inside(positionOf(render, v)).coerceIn(-chart.reach, chart.reach)
    }
    val split = insideSplits(chart, render, triangles, values, jaw)
    PaintLayer.of(slots(jaw) ?: MOUTH_INSIDE_FALLBACK, surface, values, triangles, split = split)
}

/**
 * The curves where each mouth built open on [field] turns from skin to its inside, over the triangles of [mesh] its head's split runs hold,
 * found on [lines], the surface where it lies now; inked as joins are.
 */
internal fun mouthEdgeCurves(field: CharacterField, mesh: SurfaceMesh, lines: TriangleLines): List<List<CurvePoint>> = field.openJaws.flatMap { jaw ->
    val members = field.runs.indices.filter { field.runs[it].partId == jaw.part && field.runs[it].jawSide != null }.toSet()
    val onJaw = heldBy(mesh, members)
    val values = FloatArray(mesh.vertexCount) { v -> if (onJaw[v]) jaw.inside(mesh.position(v)) else 0f }
    val t = mesh.triangles
    lines.zeroCurves(values) { tri -> onJaw[t[tri * 3]] && onJaw[t[tri * 3 + 1]] && onJaw[t[tri * 3 + 2]] }.map(::eased)
}

/** Whether each vertex of [mesh] is held by one of [members]. */
private fun heldBy(mesh: SurfaceMesh, members: Set<Int>): BooleanArray = BooleanArray(mesh.vertexCount) { v ->
    (0 until HELD).any { k -> mesh.held[v * HELD + k] in members }
}

private fun positionOf(render: RenderMesh, v: Int): Vec3 {
    val at = v * RenderMesh.FLOATS_PER_VERTEX
    return Vec3(render.vertices[at], render.vertices[at + 1], render.vertices[at + 2])
}

/** The triangles among [triangles] the inside's edge may cross, split, with [jaw]'s inside read at each extra vertex; null for none. */
private fun insideSplits(chart: SurfaceChart, render: RenderMesh, triangles: IntArray, values: FloatArray, jaw: OpenJaw): PaintSplits? {
    val indices = render.indices
    val split = ArrayList<Int>()
    for (tri in triangles) {
        val a = indices[tri * 3]
        val b = indices[tri * 3 + 1]
        val c = indices[tri * 3 + 2]
        val pa = positionOf(render, a)
        val pb = positionOf(render, b)
        val pc = positionOf(render, c)
        val longest = maxOf((pa - pb).length, (pb - pc).length, (pc - pa).length)
        if (minOf(kotlin.math.abs(values[a]), kotlin.math.abs(values[b]), kotlin.math.abs(values[c])) < longest) split += tri
    }
    if (split.isEmpty()) return null
    val splits = TriangleSplits.of(indices, chart.count, split.toIntArray(), PAINT_SPLITS)
    val out = splits.extended(values)
    for (k in 0 until splits.extraCount) {
        val wb = splits.wb[k]
        val wc = splits.wc[k]
        val p = positionOf(render, splits.a[k]) * (1f - wb - wc) + positionOf(render, splits.b[k]) * wb + positionOf(render, splits.c[k]) * wc
        out[splits.base + k] = jaw.inside(p).coerceIn(-chart.reach, chart.reach)
    }
    return PaintSplits(splits, out)
}
