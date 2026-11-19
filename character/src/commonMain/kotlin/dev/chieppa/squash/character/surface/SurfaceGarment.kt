package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.field.JoinedMember

/**
 * A garment on the surface: its whole grown [surface], the body's covered field grown by each branch's ease and meshed as the body is;
 * its [coverage] at each of that mesh's vertices; and what of it is worn, [cut] from the surface where coverage is at least zero and
 * laid as [drawn], with its [hems], the curves where coverage is zero, eased, and its contour [lines], kept to where coverage is at least zero;
 * [drawnCoverage] is its coverage over the drawn mesh, middles too. Where an outer garment hides it, its cut keeps only what shows. The parts
 * coming out through its holes are [holes], and those coming out through the ends it reaches [ends], their members placed at rest.
 * Its own shapes are painted over its color as [paint], laid on its surface's drawn mesh and kept to what is drawn, null with none.
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
    val ends: List<JoinedMember> = emptyList(),
    val paint: SurfacePaint? = null,
    val seamValues: List<FloatArray> = emptyList(),
) {
    /**
     * The curves where each join its garment inks shares its surface equally, a covered part's run and the run it grows from, over what
     * the garment covers, as its surface lies.
     */
    val inkedJoins: List<List<CurvePoint>> by lazy {
        val inked = cover.garment.joins
        val joins = if (inked.isEmpty()) emptyList() else joinCurves(surface.field, surface.mesh, surface.lines, { cover.branchOf(it.partId)?.part in inked }) { coverage[it] >= 0f }
        joins + if (cover.garment.inkHem) seamsOf(surface, coverage, seamValues) else emptyList()
    }

    /** Where it is drawn, one value a vertex of its surface's drawn mesh: its [cut] is where this is at least zero. */
    val shownValues: FloatArray get() = cutValues

    /** This garment drawn only where [shown], cut where [values] are at least zero, keeps it, a cut of its surface's drawn mesh. */
    fun within(shown: LevelCut, values: FloatArray): SurfaceGarment =
        SurfaceGarment(cover, surface, coverage, shown, shown.mesh(surface.render), hems, lines, drawnCoverage, holes, values, ends, paint?.within(values, surface), seamValues)

    /** This garment with [paint] painted over its color. */
    fun painted(paint: SurfacePaint): SurfaceGarment = SurfaceGarment(cover, surface, coverage, cut, drawn, hems, lines, drawnCoverage, holes, cutValues, ends, paint, seamValues)

    /**
     * This garment on [moved], its surface moved, its cut laid again and its hems and lines found there again; where the move leaves
     * quads out of the drawn mesh, as a pose parting two touching parts does, its cut is made again over what is left.
     */
    fun movedTo(moved: CharacterSurface): SurfaceGarment {
        val kept = touchedTriangles(moved.lines.triangles, coverage)
        val laid = if (moved.render.indices.size == surface.render.indices.size) cut else LevelCut.of(cutValues, moved.render.indices)
        return SurfaceGarment(
            cover, moved, coverage, laid, laid.mesh(moved.render), hemsOf(moved.lines, coverage), TriangleLines(moved.positions, moved.lineNormals, kept, coverage), drawnCoverage, holes, cutValues, ends,
            paint?.on(moved.render), seamValues,
        )
    }

    companion object {
        /** [cover]'s garment worn on its grown [surface]. */
        fun of(cover: WornCover, surface: CharacterSurface): SurfaceGarment = GarmentCut.run(cover, surface)
    }
}
