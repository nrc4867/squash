package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.PartLines
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.INK_SLOT
import dev.chieppa.squash.screens.sheet.PART_BASE
import dev.chieppa.squash.screens.sheet.PART_INK
import dev.chieppa.squash.screens.sheet.SurfaceRibbons
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.sheet.rememberObjectShading
import dev.chieppa.squash.screens.sheet.inkedBorders
import dev.chieppa.stretch.theme.Stretch

/** A mark's weight where the surface turns away, as a multiple of the interior weight. */
private const val MARK_EDGE_WEIGHT = 1.4f

/** How many cameras' lines a [SurfaceLines] keeps, a pane for each and room for those a zoom passes through. */
private const val KEPT_VIEWS = 8

/** How much wider a picked stroke draws than its own weight. */
private const val PICKED_WIDENS = 1.75f

/** How a character's lines are drawn under [rules], in pixels at [density]. */
fun characterLook(rules: LineRules, density: Float): CharacterLook {
    val interior = rules.interior * density
    return CharacterLook(
        lines = LineRule(interior, rules.silhouette * density),
        marks = LineRule(interior, interior * MARK_EDGE_WEIGHT),
        creases = LineRule(interior),
        inkSlot = INK_SLOT,
        fallback = PART_BASE,
    )
}

/**
 * The character's lines as the panes ask for them, for one state of the graph: contours for each camera, then creases, inked region
 * borders and marks at the interior weight, on the surface. A camera that holds still costs nothing, since its lines are kept until the state changes.
 */
class SurfaceLines(
    private val lines: BodyLines,
    private val work: BodySurfaceWork,
    private val look: CharacterLook,
    private val taper: Float,
    private val ink: Color,
    private val figureRadius: Float,
    private val palette: Palette = Palette(),
    private val picked: PickedStroke? = null,
    private val faces: FaceWork? = null,
    private val pickedWeight: PickedLineWeight? = null,
) : SurfaceRibbons {
    private val kept = object : LinkedHashMap<Any, List<RibbonDraw>>(KEPT_VIEWS, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, List<RibbonDraw>>) = size > KEPT_VIEWS
    }

    override fun figure(view: SurfaceView): List<RibbonDraw> = kept.getOrPut(if (view is FullView) Triple(view.current, view.width, view.height) else view) { figureDraws(view) }

    private fun figureDraws(view: SurfaceView): List<RibbonDraw> = buildList {
        val parts = lines.parts.values
        contourRibbon(parts, view)?.let { addAll(contourDraws(it, look.lines, ink, LineLayers.FIGURE, figureRadius)) }
        parts.forEach { addAll(creaseDraws(it, look.creases, ink, DepthLayer.SCENE)) }
        work.parts.forEach { (id, held) ->
            addAll(interiorInk(id, held, DepthLayer.SCENE))
            faces?.let { addAll(it.draws(id, view).ribbons) }
        }
    }

    override fun selected(part: PartId, view: SurfaceView): List<RibbonDraw> {
        val key = if (view is FullView) part to view.current else part to view
        return kept.getOrPut(key) {
            val held = lines.parts[part] ?: return@getOrPut emptyList()
            selectionDraws(part, held, view)
        }
    }

    private fun selectionDraws(part: PartId, held: PartLines, view: SurfaceView): List<RibbonDraw> = buildList {
        contourRibbon(listOf(held), view)?.let { addAll(contourDraws(it, look.lines, ink, LineLayers.SELECTION, figureRadius)) }
        addAll(creaseDraws(held, look.creases, ink, DepthLayer.FRONT))
        work.parts[part]?.let { addAll(interiorInk(part, it, DepthLayer.FRONT)) }
        faces?.let { held -> held.draws(part, view).ribbons.mapTo(this) { it.copy(layer = DepthLayer.FRONT) } }
    }

    override fun faceLayers(part: PartId, view: SurfaceView): List<ColorLayerDraw> = faces?.draws(part, view)?.layers.orEmpty()

    /** What is inked on [id]'s surface at the interior weight: its strokes, tapered, and its region borders, closed. */
    private fun interiorInk(id: PartId, held: ResolvedSurfaceWork, layer: DepthLayer): List<RibbonDraw> = buildList {
        held.looseMarks().filterNot { it.kind == MarkKind.FUR }.mapNotNullTo(this) { mark ->
            if (mark.source == picked?.id) {
                mark.lineDraw(lines.parts[id], look.marks.copy(interiorPixels = look.marks.interiorPixels * PICKED_WIDENS, silhouettePixels = look.marks.silhouettePixels * PICKED_WIDENS), picked.color, taper, DepthLayer.FRONT)
            } else {
                mark.lineDraw(lines.parts[id], look.marks, mark.slot?.let(palette::colorOf) ?: ink, taper, layer)
            }
        }
        held.inkedBorders().mapTo(this) { RibbonDraw(it, look.creases, ink, layer = layer) }
        pickedWeight?.let { lit -> held.lineWeights.filter { it.id == lit.id }.mapNotNullTo(this) { it.litDraw(look.marks.copy(interiorPixels = look.marks.interiorPixels * PICKED_WIDENS, silhouettePixels = look.marks.silhouettePixels * PICKED_WIDENS), lit.color) } }
    }
}

/** The stroke [id] shown on the model in [color], as the Lines inspector picked it. */
data class PickedStroke(val id: MarkId, val color: Color)

/** The line weight [id] lit on the model in [color], as the Lines inspector picked it. */
data class PickedLineWeight(val id: LineWeightId, val color: Color)

/** The marks that stand alone, outside any mark group. */
fun ResolvedSurfaceWork.looseMarks() = groups.flatMap { group -> group.roles.flatMap { it.marks } }.toSet().let { grouped -> marks.filter { it.id !in grouped } }

/** The open character's lines, or null until its graph has built them. */
@Composable
fun rememberSurfaceLines(state: AppState): SurfaceRibbons? {
    val session by state.session.collectAsState()
    val open = session ?: return null
    val lines by remember(open) { open.bodyLines.output }.collectAsState()
    val work by remember(open) { open.bodySurfaceWork.output }.collectAsState()
    val character by remember(open) { open.assembly.output }.collectAsState()
    val document by remember(open) { open.document }.collectAsState()
    val rules = document.character.lineRules
    val ink = document.character.palette.colorOf(INK_SLOT) ?: PART_INK
    val density = LocalDensity.current.density
    val palette = document.character.palette
    val picked = state.lines.pickedMark?.let { PickedStroke(it.id, Stretch.colors.accent) }
    val pickedWeight = state.lines.picked?.let { PickedLineWeight(it.id, Stretch.colors.accent) }
    val toLight = rememberObjectShading(state).toLight
    val target = state.face.gazeTarget.takeIf { state.face.gazeOn }
    val bins = remember(open) { FaceBinsHeld() }
    val expression = document.character.shownExpression
    val cheats = document.character.gazeCheats
    return remember(lines, work, character, rules, ink, density, palette, picked, pickedWeight, toLight, target, expression, cheats) {
        val shown = lines
        val held = work
        val whole = character
        val radius = whole?.box?.radius
        if (shown == null || held == null || whole == null || radius == null) {
            null
        } else {
            val look = characterLook(rules, density)
            val parts = whole.parts.mapNotNull { part -> part.id?.let { it to part } }.toMap()
            val faces = FaceWork(FaceInput(parts, held, shown, palette, toLight, expression, target, cheats), look.marks, ink, rules.taper, bins::binOf)
            SurfaceLines(shown, held, look, rules.taper, ink, radius, palette, picked, faces, pickedWeight)
        }
    }
}
