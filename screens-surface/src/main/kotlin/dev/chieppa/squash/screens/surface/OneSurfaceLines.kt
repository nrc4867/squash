package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartLines
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.surface.SurfaceSection
import dev.chieppa.squash.character.surface.isRun
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.TriangleLines
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.WeightStroke
import dev.chieppa.squash.geom.lineWeightAt
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.screens.sheet.SurfaceView

/** How many parts' sections a [OneSurfaceLines] keeps the lines of. */
private const val KEPT_SECTIONS = 4

/**
 * A character's one surface as its lines read it: [shown], the surface at rest or posed with its paint and dress, and the line weights
 * [strokes] that thicken its contour by distance in space.
 */
class OneSurfaceLines(val shown: PosedSurface, private val strokes: List<WeightStroke> = emptyList()) {
    /** The parts the surface holds. */
    val parts: Set<PartId> get() = shown.surface.parts

    private val weightAt: ((Vec3) -> Float)? = if (strokes.isEmpty()) null else { point -> lineWeightAt(point, strokes) }

    /** The contour of [lined], each part's lines with the surface's runs left out, and of the surface and its garments, through [view]. */
    fun contour(lined: Collection<PartLines>, view: SurfaceView) =
        contourRibbon(lined, view, listOf(shown.surface.lines) + shown.dress?.garments.orEmpty().map { it.lines }, weightAt)

    /** The ink along the surface's inked joins and its garments' inked hems, through [view]. */
    fun inks(view: SurfaceView, look: CharacterLook, ink: Color): List<RibbonDraw> {
        val surface = shown.surface
        return joinDraws(surface, view, look.creases, ink) + shown.dress?.let { hemDraws(it, view, look.creases, ink, surface.mesh.cell) }.orEmpty()
    }

    /** What the surface's paint inks on [part] as [view] sees it, its face's lines among them. */
    fun marks(part: PartId, view: SurfaceView) = shown.paint?.seenFrom(view::toViewer)?.marks?.get(part).orEmpty()

    private val sections = object : LinkedHashMap<PartId, Pair<TriangleLines, List<List<CurvePoint>>>>(KEPT_SECTIONS, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PartId, Pair<TriangleLines, List<List<CurvePoint>>>>) = size > KEPT_SECTIONS
    }

    /** [part]'s own section's contour through [view] in front of what is ghosted, and the curve where it meets its neighbours. */
    fun selected(part: PartId, view: SurfaceView, look: CharacterLook, ink: Color, figureRadius: Float): List<RibbonDraw> {
        val (lines, border) = synchronized(sections) {
            sections.getOrPut(part) {
                val section = SurfaceSection(shown.surface, part)
                TriangleLines(shown.surface.positions, shown.surface.lineNormals, section.triangles) to section.border()
            }
        }
        val contour = contourRibbon(emptyList(), view, listOf(lines), weightAt)?.let { contourDraws(it, look.lines, ink, LineLayers.SELECTION, figureRadius) }.orEmpty()
        return contour + facedCurveDraws(border, view, look.creases, ink, shown.surface.mesh.cell, DepthLayer.FRONT)
    }
}

/** These lines of a part of the one surface: only its patches that are not runs, and no crease, since the surface's own joins ink it. */
internal fun PartLines.withoutRuns(): PartLines = PartLines(part, grids.filterNot { isRun(it.patch) }, emptyList(), markWeights)
