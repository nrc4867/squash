package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartLines
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.surface.SurfaceSection
import dev.chieppa.squash.character.surface.isFieldRun
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.WeightStroke
import dev.chieppa.squash.geom.lineWeightAt
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.StrokeRibbon
import dev.chieppa.squash.screens.sheet.SurfaceView

/** How many parts' sections a [PosedSurfaceLines] keeps the lines of. */
private const val KEPT_SECTIONS = 4

/**
 * A character's one surface as its lines read it: [shown], the surface at rest or posed with its paint and dress, and the line weights
 * [strokes] that thicken its contour by distance in space.
 */
class PosedSurfaceLines(val shown: PosedSurface, private val strokes: List<WeightStroke> = emptyList()) {
    /** The parts the surface holds. */
    val parts: Set<PartId> get() = shown.surface.parts

    private val weightAt: ((Vec3) -> Float)? = if (strokes.isEmpty()) null else { point -> lineWeightAt(point, strokes) }

    /**
     * The contour of [lined], each part's lines with the surface's runs left out, and of the surface and its garments, through [view],
     * with the hems of the garments hanging free (see [hangingHems]), each garment's at its ink weight.
     */
    fun contour(lined: Collection<PartLines>, view: SurfaceView): StrokeRibbon? {
        val garments = shown.dress?.garments.orEmpty()
        return contourRibbon(
            lined, view, listOf(shown.surface.lines) + garments.map { it.lines }, weightAt, shown.dress?.let(::hangingHems).orEmpty(),
            scales = listOf(1f) + garments.map { it.cover.garment.inkWeight },
        )
    }

    /** The ink along the surface's inked joins and its garments' inked hems and joins, through [view]. */
    fun inks(view: SurfaceView, look: CharacterLook, ink: Color): List<RibbonDraw> {
        val surface = shown.surface
        val dress = shown.dress
        return joinDraws(surface, view, look.creases, ink) + dress?.let { hemDraws(it, view, look.creases, ink, surface.mesh.cell) }.orEmpty() +
            dress?.garments.orEmpty().flatMap { facedCurveDraws(it.inkedJoins, view, look.creases, ink, surface.mesh.cell, scale = it.cover.garment.inkWeight) }
    }

    /** The lines its garments' cloth prints, as they are worn: each garment's inked marks and region borders, cut to its cloth. */
    val garmentMarks: List<ResolvedMark> get() = shown.dress?.garments.orEmpty().flatMap { garment -> garment.paint?.marks?.values?.flatten().orEmpty() }

    /** The lines its garments' cloth prints, each in its slot's color from [palette] or [ink], at the marks' weight. */
    fun garmentMarkDraws(look: CharacterLook, palette: Palette, ink: Color, taper: Float): List<RibbonDraw> =
        garmentMarks.mapNotNull { it.lineDraw(null, look.marks, it.slot?.let(palette::colorOf) ?: ink, taper, DepthLayer.SCENE) }

    /** What the surface's paint inks on [part] as [view] sees it, its face's lines among them. */
    fun marks(part: PartId, view: SurfaceView) = shown.paint?.seenFrom(view::toViewer)?.marks?.get(part).orEmpty()

    private val sections = object : LinkedHashMap<PartId, Pair<TriangleLines, List<List<CurvePoint>>>>(KEPT_SECTIONS, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PartId, Pair<TriangleLines, List<List<CurvePoint>>>>) = size > KEPT_SECTIONS
    }

    private fun sectionOf(part: PartId): Pair<TriangleLines, List<List<CurvePoint>>> = synchronized(sections) {
        sections.getOrPut(part) {
            val section = SurfaceSection(shown.surface, part)
            TriangleLines(shown.surface.positions, shown.surface.lineNormals, section.triangles) to section.border()
        }
    }

    /** [part]'s own section's contour through [view] in front of what is ghosted, and the curve where it meets its neighbours. */
    fun selected(part: PartId, view: SurfaceView, look: CharacterLook, ink: Color, figureRadius: Float): List<RibbonDraw> {
        val (lines, border) = sectionOf(part)
        val contour = contourRibbon(emptyList(), view, listOf(lines), weightAt)?.let { contourDraws(it, look.lines, ink, LineLayers.SELECTION, figureRadius) }.orEmpty()
        return contour + facedCurveDraws(border, view, look.creases, ink, shown.surface.mesh.cell, DepthLayer.FRONT)
    }

    /** [part]'s own section's contour through [view] as the figure's own is drawn, with nothing where it meets its neighbours. */
    fun sectionContour(part: PartId, view: SurfaceView, look: CharacterLook, ink: Color, figureRadius: Float): List<RibbonDraw> =
        contourRibbon(emptyList(), view, listOf(sectionOf(part).first), weightAt)?.let { contourDraws(it, look.lines, ink, LineLayers.FIGURE, figureRadius) }.orEmpty()
}

/** These lines of a part of the one surface: only its patches that are not runs, and no crease, since the surface's own joins ink it. */
internal fun PartLines.withoutRuns(): PartLines = PartLines(part, grids.filterNot { isFieldRun(part, it.patch) }, emptyList(), markWeights)
