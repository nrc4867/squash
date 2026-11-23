package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.character.build.NeckJoin
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.surface.SurfaceMesh

/** A join's ink setting at or below this leaves the join unlined. */
private const val INK_OFF = 0.5f

/** How many times a join's curve is eased. */
private const val EASING_STEPS = 3

/**
 * A character's body as one surface: the [mesh] its [field] meshes to, [render] as the GPU draws it, over the mesh's [QuadFan], with
 * each part's shading carried onto it, its [lines] for contours, read at [positions] with [lineNormals], the surface's normals smoothed
 * over a few rings of neighbours, three values a vertex of [mesh], and the curves where each inked join's two parts share the surface
 * equally, [joins]. [positiveZ] and [negativeZ] hold each vertex's share of paired parts on that side of the body's plane, so a far side
 * darkens by how much of it is that side's. [chin] holds each vertex's shade under a head's chin, 0 to 1, or is null where no head's
 * neck style casts one (see [ChinShade]). A posed surface keeps the [mesh] it was meshed as, with everything it draws moved.
 */
class CharacterSurface(
    val field: CharacterField,
    val mesh: SurfaceMesh,
    val render: RenderMesh,
    val lines: TriangleLines,
    val joins: List<List<CurvePoint>>,
    val positiveZ: FloatArray,
    val negativeZ: FloatArray,
    val positions: FloatArray,
    val lineNormals: FloatArray,
    val chin: FloatArray? = null,
) {
    /**
     * This surface moved: drawn as [render], its vertices at [positions] with [lineNormals], its lines and joins found there again over
     * [triangles], the mesh's or some of them.
     */
    fun movedTo(render: RenderMesh, positions: FloatArray, lineNormals: FloatArray, triangles: IntArray = mesh.triangles): CharacterSurface {
        val moved = TriangleLines(positions, lineNormals, triangles)
        return CharacterSurface(field, mesh, render, moved, joinCurves(field, mesh, moved), positiveZ, negativeZ, positions, lineNormals, chin)
    }

    /** The surface read by its parts' coordinates, made once when first asked for. */
    val chart: SurfaceChart by lazy { SurfaceChart(this) }

    /** The parts the surface holds; each is drawn as the surface and not as its own mesh. */
    val parts: Set<PartId> get() = this.field.parts

    companion object {
        /** The surface of [mesh], meshed from [field]. */
        fun of(field: CharacterField, mesh: SurfaceMesh): CharacterSurface = SurfaceFinish.run(field, mesh)
    }
}

/**
 * Where each smooth union between two parts shares the surface equally, its child's own field equal to its parent's, over the
 * triangles whose corners all lie on that union and [keep] keeps; a join whose part [drawn] leaves out, by default one that sets its ink
 * off, draws none.
 */
internal fun joinCurves(
    field: CharacterField,
    mesh: SurfaceMesh,
    lines: TriangleLines,
    drawnRun: (FieldRun) -> Boolean = ::inked,
    keep: (Int) -> Boolean = { true },
): List<List<CurvePoint>> {
    val values = FloatArray(mesh.vertexCount)
    val pair = IntArray(mesh.vertexCount) { -1 }
    val drawn = BooleanArray(field.runs.size) { drawnRun(field.runs[it]) }
    for (v in 0 until mesh.vertexCount) {
        val winner = mesh.winners[v]
        val parent = mesh.parents[v]
        if (winner < 0 || parent < 0 || !drawn[winner]) continue
        if (field.runs[winner].part === field.runs[parent].part || !keep(v)) continue
        pair[v] = winner
        values[v] = mesh.parentOwn[v] - mesh.winnerOwn[v]
    }
    val t = mesh.triangles
    return lines.zeroCurves(values) { tri ->
        val p = pair[t[tri * 3]]
        p >= 0 && pair[t[tri * 3 + 1]] == p && pair[t[tri * 3 + 2]] == p
    }.map(::eased)
}

/**
 * [curve] with each inner point moved half way to the mean of its neighbours [EASING_STEPS] times, its ends kept, so a curve
 * cut across triangles does not zigzag with their edges.
 */
internal fun eased(curve: List<CurvePoint>): List<CurvePoint> {
    val closed = curve.size > 3 && curve.first().position == curve.last().position
    var points = if (closed) curve.dropLast(1) else curve
    val n = points.size
    if (n < 3) return curve
    repeat(EASING_STEPS) {
        val last = points
        points = List(n) { i ->
            if (!closed && (i == 0 || i == n - 1)) {
                last[i]
            } else {
                val before = last[(i - 1 + n) % n]
                val after = last[(i + 1) % n]
                CurvePoint(last[i].position * 0.5f + (before.position + after.position) * 0.25f, last[i].normal)
            }
        }
    }
    return if (closed) points + points.first() else points
}

private fun inked(run: FieldRun): Boolean =
    run.part.placement[JoinParams.INK]?.let { it > INK_OFF } ?: NeckJoin.of(run.part.placement).inked ?: run.part.template.join.ink
