package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.viewkeys.KeyedView
import dev.chieppa.squash.character.viewkeys.ViewKeyCache
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
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.INK_SLOT
import dev.chieppa.squash.screens.sheet.PART_BASE
import dev.chieppa.squash.screens.sheet.PART_INK
import dev.chieppa.squash.screens.sheet.SurfaceRibbons
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.sheet.rememberPaneSurface
import dev.chieppa.squash.character.build.lineWeightStrokes
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
 * The parts [surface] holds are lined as that surface: its contour, its joins and hems, the marks its paint inks, and a selected
 * part's own section; the parts it does not hold are lined by their own meshes.
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
    private val surface: PosedSurfaceLines,
    private val keys: ViewKeyCache? = null,
    private val body: AssembledCharacter? = null,
) : SurfaceRibbons {
    private val inSurface: Set<PartId> = surface.parts

    private val kept = object : LinkedHashMap<Any, List<RibbonDraw>>(KEPT_VIEWS, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, List<RibbonDraw>>) = size > KEPT_VIEWS
    }

    override fun figure(view: SurfaceView): List<RibbonDraw> = kept.getOrPut(if (view is FullView) Triple(view.current, view.width, view.height) else view) {
        val keyed = keyedFor(view) ?: return@getOrPut figureDraws(view)
        val shown = keyed.surface ?: return@getOrPut figureDraws(view)
        SurfaceLines(keyed.lines, work, look, taper, ink, figureRadius, palette, picked, faces, pickedWeight, surface.on(shown)).figureDraws(view)
    }

    /** The head cheats as a free view sees them, over the lines' own body and surface; none for a locked pane, which shows what is drawn. */
    override fun keyedFor(view: SurfaceView): KeyedView? {
        if (keys == null || body == null || view !is FullView) return null
        val keyed = keys.keyed(body, lines, work, surface.shown, view::toViewer)
        return keyed.takeIf { it.body !== body || it.surface !== surface.shown }
    }

    private fun figureDraws(view: SurfaceView): List<RibbonDraw> = buildList {
        val parts = lines.parts.values.map { if (it.part.id in inSurface) it.withoutRuns() else it }
        addAll(surface.figureContour(parts, view, look.lines, ink, figureRadius))
        parts.forEach { addAll(creaseDraws(it, look.creases, ink, DepthLayer.SCENE)) }
        addAll(surface.inks(view, look, ink, palette))
        addAll(surface.garmentMarkDraws(look, palette, ink, taper))
        work.parts.forEach { (id, held) ->
            addAll(interiorInk(id, held, DepthLayer.SCENE, view))
            if (id !in inSurface) faces?.let { addAll(it.draws(id, view).ribbons) }
        }
    }

    override fun selected(part: PartId, view: SurfaceView): List<RibbonDraw> {
        val key = if (view is FullView) part to view.current else part to view
        return kept.getOrPut(key) {
            if (part in inSurface) {
                return@getOrPut surface.selected(part, view, look, ink, figureRadius) + work.parts[part]?.let { interiorInk(part, it, DepthLayer.FRONT, view) }.orEmpty()
            }
            val held = lines.parts[part] ?: return@getOrPut emptyList()
            selectionDraws(part, held, view)
        }
    }

    private fun selectionDraws(part: PartId, held: PartLines, view: SurfaceView): List<RibbonDraw> = buildList {
        contourRibbon(listOf(held), view)?.let { addAll(contourDraws(it, look.lines, ink, LineLayers.SELECTION, figureRadius)) }
        addAll(creaseDraws(held, look.creases, ink, DepthLayer.FRONT))
        work.parts[part]?.let { addAll(interiorInk(part, it, DepthLayer.FRONT, view)) }
        faces?.let { held -> held.draws(part, view).ribbons.mapTo(this) { it.copy(layer = DepthLayer.FRONT) } }
    }

    override fun faceLayers(part: PartId, view: SurfaceView): List<ColorLayerDraw> = if (part in inSurface) emptyList() else faces?.draws(part, view)?.layers.orEmpty()

    /**
     * What is inked on [id]'s surface at the interior weight: its strokes, tapered, and its region borders, closed; on a part of the one
     * surface, the strokes its paint inks.
     */
    private fun interiorInk(id: PartId, held: ResolvedSurfaceWork, layer: DepthLayer, view: SurfaceView): List<RibbonDraw> = buildList {
        val strokes = if (id in inSurface) surface.marks(id, view) else held.looseMarks()
        strokes.filterNot { it.kind == MarkKind.FUR }.mapNotNullTo(this) { mark ->
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

/**
 * The open character's lines, on its posed surface when [posed] and at rest otherwise, or null until its graph has built them and its surface. The
 * face's parts are the ones the lines were found on, so the face and the lines always lie on the same surface.
 */
@Composable
fun rememberSurfaceLines(state: AppState, posed: Boolean = false): SurfaceRibbons? {
    val session by state.session.collectAsState()
    val open = session ?: return null
    val lines by remember(open, posed) { (if (posed) open.posedLines else open.bodyLines).output }.collectAsState()
    val work by remember(open, posed) { (if (posed) open.posedSurfaceWork else open.bodySurfaceWork).output }.collectAsState()
    val character by remember(open, posed) { (if (posed) open.posedBody else open.assembly).output }.collectAsState()
    val document by remember(open) { open.document }.collectAsState()
    val rules = document.character.lineRules
    val ink = document.character.palette.colorOf(INK_SLOT) ?: PART_INK
    val density = LocalDensity.current.density
    val palette = document.character.palette
    val picked = state.lines.pickedMark?.let { PickedStroke(it.id, Stretch.colors.accent) }
    val pickedWeight = state.lines.picked?.let { PickedLineWeight(it.id, Stretch.colors.accent) }
    val stage by state.stage.collectAsState()
    val facing = stage == Stage.FACE
    val locked = state.face.lockedVersion.takeIf { facing }
    val bins = remember(open) { FaceBinsHeld() }
    val expression = document.character.shownExpression
    val surface = rememberPaneSurface(open)?.shown(posed)
    val keys by remember(open) { open.viewKeys.output }.collectAsState()
    return remember(lines, work, character, rules, ink, density, palette, picked, pickedWeight, locked, expression, surface, keys) {
        val shown = lines
        val held = work
        val whole = character
        val radius = whole?.box?.radius
        if (shown == null || held == null || whole == null || radius == null || surface == null) {
            null
        } else {
            val look = characterLook(rules, density)
            val parts = shown.parts.mapValues { it.value.part }
            val faces = FaceWork(FaceInput(parts, held, shown, palette, expression), look.marks, ink, rules.taper) { part, view -> locked ?: bins.binOf(part, view) }
            SurfaceLines(shown, held, look, rules.taper, ink, radius, palette, picked, faces, pickedWeight, PosedSurfaceLines(surface, lineWeightStrokes(whole, held)), keys, whole)
        }
    }
}
