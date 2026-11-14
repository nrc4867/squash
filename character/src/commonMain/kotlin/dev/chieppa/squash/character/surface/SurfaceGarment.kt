package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.field.JoinedMember

/**
 * A garment on a one surface: its whole grown [surface], the body's covered field grown by each branch's ease and meshed as the body is;
 * its [coverage] at each of that mesh's vertices; and what of it is worn, [cut] from the surface where coverage is at least zero and
 * laid as [drawn], with its [hems], the curves where coverage is zero, eased, and its contour [lines] over the triangles it keeps whole;
 * [drawnCoverage] is its coverage over the drawn mesh, middles too. Where an outer garment hides it, its cut keeps only what shows. The parts
 * coming out through its holes are [holes], their members placed at rest.
 * Its hem, neck opening and cuffs are those curves: smooth level curves of its coordinates, not rows of any grid.
 */
class SurfaceGarment(
    val cover: WornCover,
    val surface: CharacterSurface,
    val coverage: FloatArray,
    val cut: LevelCut,
    val drawn: RenderMesh,
    val hems: List<List<CurvePoint>>,
    val lines: TriangleLines,
    val drawnCoverage: FloatArray,
    val holes: List<JoinedMember> = emptyList(),
    private val cutValues: FloatArray = drawnCoverage,
) {
    /** This garment drawn only where [shown], cut where [values] are at least zero, keeps it, a cut of its surface's drawn mesh. */
    fun within(shown: LevelCut, values: FloatArray): SurfaceGarment = SurfaceGarment(cover, surface, coverage, shown, shown.mesh(surface.render), hems, lines, drawnCoverage, holes, values)

    /**
     * This garment on [moved], its surface moved, its cut laid again and its hems and lines found there again; where the move leaves
     * quads out of the drawn mesh, as a pose parting two touching parts does, its cut is made again over what is left.
     */
    fun movedTo(moved: CharacterSurface): SurfaceGarment {
        val kept = coveredTriangles(moved.lines.triangles, coverage)
        val laid = if (moved.render.indices.size == surface.render.indices.size) cut else LevelCut.of(cutValues, moved.render.indices)
        return SurfaceGarment(
            cover, moved, coverage, laid, laid.mesh(moved.render), hemsOf(moved.lines, coverage), TriangleLines(moved.positions, moved.lineNormals, kept), drawnCoverage, holes, cutValues,
        )
    }

    companion object {
        /** [cover]'s garment worn on its grown [surface]. */
        fun of(cover: WornCover, surface: CharacterSurface): SurfaceGarment = GarmentCut.run(cover, surface)
    }
}
