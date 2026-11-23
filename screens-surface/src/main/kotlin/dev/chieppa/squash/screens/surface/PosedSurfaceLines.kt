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
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.StrokeRibbon
import dev.chieppa.squash.render.foldLineColor
import dev.chieppa.squash.render.overSeeThroughCloth
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

    /** These lines read on [other], the same surface moved, with the same line weights. */
    fun on(other: PosedSurface): PosedSurfaceLines = PosedSurfaceLines(other, strokes)

    private val weightAt: ((Vec3) -> Float)? = if (strokes.isEmpty()) null else { point -> lineWeightAt(point, strokes) }

    private val garments = shown.dress?.garments.orEmpty()
    private val seeThrough = garments.filter { it.seeThrough != null }
    private val whole = garments.filter { it.seeThrough == null }

    /**
     * The contour of [lined], each part's lines with the surface's runs left out, and of the surface and its garments, through [view],
     * with the hems of the garments hanging free (see [hangingHems]), each garment's at its ink weight; see-through garments' are left
     * out, for [clothContour].
     */
    fun contour(lined: Collection<PartLines>, view: SurfaceView): StrokeRibbon? = ribbons(lined, view, folds = false).contour

    /** [contour], with, where [folds], the folds of the surface and its garments that their smooth contours miss. */
    private fun ribbons(lined: Collection<PartLines>, view: SurfaceView, folds: Boolean = true): FigureRibbons = figureRibbons(
        lined, view, listOf(shown.surface.lines) + whole.map { it.lines }, weightAt, hangingHems(whole),
        scales = listOf(1f) + whole.map { it.cover.garment.inkWeight }, folds = folds, shown = listOf(null) + whole.map { it.shownValues },
    )

    /** The contour of the see-through garments through [view], with their hems hanging free, each at its ink weight, and their folds. */
    fun clothContour(view: SurfaceView): FigureRibbons? =
        if (seeThrough.isEmpty()) null else figureRibbons(
            emptyList(), view, seeThrough.map { it.lines }, weightAt, hangingHems(seeThrough), seeThrough.map { it.cover.garment.inkWeight }, shown = seeThrough.map { it.shownValues },
        )

    /**
     * The contour of [lined] and the surface ([contour]) at [rule]'s two weights, with the folds its smooth contours miss, each lock
     * group's apart, tucked behind by its own depth, then the see-through garments' over their cloth.
     */
    fun figureContour(lined: Collection<PartLines>, view: SurfaceView, rule: LineRule, ink: Color, figureRadius: Float): List<RibbonDraw> {
        val (locks, others) = lined.partition { it.part.locks != null }
        val figure = ribbons(others, view)
        val cloth = clothContour(view)
        return figure.contour?.let { contourDraws(it, rule, ink, LineLayers.FIGURE, figureRadius, folds = figure.folds) }.orEmpty() +
            locks.flatMap { lock ->
                contourRibbon(listOf(lock), view)?.let { contourDraws(it, rule, ink, LineLayers.FIGURE, figureRadius, tuck = lock.part.locks?.tuck) }.orEmpty()
            } +
            cloth?.contour?.let { contourDraws(it, rule, ink, LineLayers.FIGURE, figureRadius, folds = cloth.folds).map(RibbonDraw::overSeeThroughCloth) }.orEmpty()
    }

    /**
     * The ink along the surface's inked joins, its garments' inked hems and joins, and its creases and wrinkles, through [view]; each
     * garment's creases in its cloth's fold line color from [palette], a see-through garment's over its cloth.
     */
    fun inks(view: SurfaceView, look: CharacterLook, ink: Color, palette: Palette): List<RibbonDraw> {
        val surface = shown.surface
        val inkWeights = garments.associate { it.cover.garment.id to it.cover.garment.inkWeight }
        val foldColors = garments.associate { garment -> garment.cover.garment.id to (palette.colorOf(garment.cover.garment.color)?.foldLineColor ?: ink) }
        val clothIds = seeThrough.mapTo(HashSet()) { it.cover.garment.id }
        return joinDraws(surface, view, look.creases, ink) + hemDraws(garments, view, look.creases, ink, surface.mesh.cell) +
            garments.flatMap { facedCurveDraws(it.inkedJoins, view, look.creases, ink, surface.mesh.cell, scale = it.cover.garment.inkWeight).onCloth(it) } +
            shown.creases?.let { creaseInkDraws(it, inkWeights, foldColors, view, look.creases, ink, surface.mesh.cell, clothIds) }.orEmpty()
    }

    /** The lines its garments' cloth prints, as they are worn: each garment's inked marks and region borders, cut to its cloth. */
    val garmentMarks: List<ResolvedMark> get() = garments.flatMap { garment -> garment.paint?.marks?.values?.flatten().orEmpty() }

    /** The lines its garments' cloth prints, each in its slot's color from [palette] or [ink], at the marks' weight, a see-through garment's over its cloth. */
    fun garmentMarkDraws(look: CharacterLook, palette: Palette, ink: Color, taper: Float): List<RibbonDraw> = garments.flatMap { garment ->
        garment.paint?.marks?.values?.flatten().orEmpty().mapNotNull { it.lineDraw(null, look.marks, it.slot?.let(palette::colorOf) ?: ink, taper, DepthLayer.SCENE) }.onCloth(garment)
    }

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

    /**
     * [part]'s own section's contour through [view] as the figure's own is drawn, with nothing where it meets its neighbours, and the
     * contour of its [pieces], its patches that are not runs, a claw or a pad, with it.
     */
    fun sectionContour(part: PartId, view: SurfaceView, look: CharacterLook, ink: Color, figureRadius: Float, pieces: Collection<PartLines> = emptyList()): List<RibbonDraw> =
        contourRibbon(pieces, view, listOf(sectionOf(part).first), weightAt)?.let { contourDraws(it, look.lines, ink, LineLayers.FIGURE, figureRadius) }.orEmpty()
}

/** These lines of a part of the one surface: only its patches that are not runs, and no crease, since the surface's own joins ink it; its strands stay. */
internal fun PartLines.withoutRuns(): PartLines = PartLines(part, grids.filterNot { isFieldRun(part, it.patch) }, emptyList(), markWeights, strands)
