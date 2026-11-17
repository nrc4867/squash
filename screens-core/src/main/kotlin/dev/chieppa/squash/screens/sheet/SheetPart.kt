package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.screens.edit.dressTarget
import dev.chieppa.squash.screens.edit.outlinesOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.PartExtents
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.laidOn
import dev.chieppa.squash.geom.PartSurface
import java.util.Collections
import java.util.IdentityHashMap
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.character.build.GarmentLook
import dev.chieppa.squash.document.isBranchPart

/**
 * One placed part as the panes draw it: what the graph derived for it, in body space, [seeThrough] for a garment shown ghosted. Never
 * changes once made.
 */
class SheetPart(private val placed: PlacedPart, val seeThrough: Boolean = false) {
    val id: PartId? get() = placed.id
    val placedPart: PlacedPart get() = placed
    val mesh: RenderMesh get() = placed.surface.mesh
    val origin: Vec3 get() = placed.origin
    val mirrored: Boolean get() = placed.mirrored
    val planned: Boolean get() = placed.planned
    val side: BodySide? get() = placed.bodySide()
    val lengthFactor: Float get() = placed.lengthFactor
    val axes: PartAxes = PartAxes.of(placed.template.axis.rest.toVec3(), placed.mirrored, placed.template.axis.up?.toVec3(), placed.template.viewPlanes)
    val box: BodyBox = requireNotNull(placed.box) { "A part with nothing built is not drawn" }
    val extents: PartExtents = requireNotNull(placed.surface.extents)

    /** The middle of the part. */
    val center: Vec3 get() = box.center

    /** The radius of a sphere around [center] that holds the part. */
    val radius: Float get() = box.radius

    /** The outline of the part as [view]'s pane sees it, as paths of points in body space. */
    fun silhouette(view: View): List<List<Vec3>> {
        val outlines = placed.surface.silhouettes
        return inBody(
            when (view) {
                View.SIDE -> outlines.side
                View.TOP -> outlines.up
                View.FRONT -> outlines.along
            },
        )
    }

    /** The outline of the part seen from the body's side, as paths of points in body space. */
    fun silhouetteFromBodySide(): List<List<Vec3>> = inBody(placed.surface.silhouettes.bodySide)

    private fun inBody(paths: List<List<Vec3>>) = paths.map { path -> path.map { origin + it } }

    private val posedCuts = Collections.synchronizedMap(IdentityHashMap<PatchCut, Pair<PartSurface, RenderMesh>>())

    /** [cut], a color layer on this part, laid on [posed], this part's surface in a pose; kept while the pose holds. */
    fun posedCut(cut: PatchCut, posed: PartSurface): RenderMesh {
        posedCuts[cut]?.takeIf { it.first === posed }?.let { return it.second }
        val grid = posed.grid(cut.patch) ?: return cut.mesh
        return cut.laidOn(grid).also { posedCuts[cut] = posed to it }
    }
}

/**
 * What the panes draw from one assembled character: the [part] selected with the outlines the document holds for it, the
 * [others] around it, the [box] everything fills, the height of the [floor] and the [drawingScale] its size parameter sets.
 * [overridden] is the views this side holds for itself, and [hidden] what the pair holds under those while an edit lands on
 * the pair. [posed] is each part as the character's pose shows it, its patches and mesh together, or null while the pose leaves every
 * bone at rest; a part the pose has not caught up with yet shows as the build the pose was found on has it.
 */
class SheetSelection(
    val part: SheetPart,
    val outlines: Map<View, OutlineCurve>,
    val others: List<SheetPart> = emptyList(),
    val box: BodyBox = part.box,
    val floor: Float = part.box.low.y,
    val drawingScale: Float = 1f,
    val overridden: Set<View> = emptySet(),
    val hidden: Map<View, OutlineCurve> = emptyMap(),
    val posed: Map<PartId, PlacedPart>? = null,
) {
    /** Every part, the selection among them, with the see-through ones last so what lies behind them is drawn first. */
    val whole: List<SheetPart> get() = (others + part).sortedBy { it.seeThrough }
}

/** [character] as the panes draw it with [selected] picked out, its garments see-through when [ghosted], or null while that part has built nothing. */
fun sheetSelection(
    character: AssembledCharacter,
    selected: PartId,
    outlines: Map<View, OutlineCurve>,
    drawingScale: Float = 1f,
    overridden: Set<View> = emptySet(),
    hidden: Map<View, OutlineCurve> = emptyMap(),
    posed: Map<PartId, PlacedPart>? = null,
    ghosted: Boolean = false,
): SheetSelection? {
    val shown = character.parts.filter { it.box != null }.map { SheetPart(it, ghosted && it.id?.let(::isBranchPart) == true) }
    val part = shown.firstOrNull { it.id == selected } ?: return null
    val box = character.box ?: return null
    return SheetSelection(part, outlines, shown - part, box, character.floor, drawingScale, overridden, hidden, posed)
}

/**
 * Each part as the open character's pose shows it, the part its posed lines were found on so the two always agree, or null while
 * [atRest] or before the posed lines have been found.
 */
@Composable
private fun rememberPosedParts(state: AppState, atRest: Boolean): Map<PartId, PlacedPart>? {
    val session by state.session.collectAsState()
    val open = session ?: return null
    val lines by remember(open) { open.posedLines.output }.collectAsState()
    val parts = remember(lines) { lines?.let(::posedParts) }
    return if (atRest) null else parts
}

/** Each part as [lines] were found on it: in a pose, its patches and mesh as that pose shows them, from one build. */
fun posedParts(lines: BodyLines): Map<PartId, PlacedPart> = lines.parts.mapValues { it.value.part }

/**
 * The selected part and the character around it through the real build, or null while nothing is selected or it has not built yet.
 * In Dress its outlines are those of what the outline tools draw on, the picked garment's branch over it or its Redraw.
 */
@Composable
fun rememberSheetSelection(state: AppState): SheetSelection? {
    val session by state.session.collectAsState()
    val id by state.selectedPart.collectAsState()
    val open = session ?: return null
    val selected = id ?: return null
    val document by remember(open) { open.document }.collectAsState()
    val instance = document.character.part(selected) ?: return null
    val character by remember(open) { open.assembly.output }.collectAsState()
    val mirror by state.mirror.collectAsState()
    val stage by state.stage.collectAsState()
    val garment by state.dress.garment.collectAsState()
    val redrawing by state.dress.redrawing.collectAsState()
    val dressing = stage == Stage.DRESS
    val data = document.character.resolved(instance)
    val target = if (dressing) garment?.let { document.character.dressTarget(it, selected, redrawing) } else null
    val outlines = if (dressing) target?.let(document.character::outlinesOf).orEmpty() else data.outlines
    val scale = data.params[SIZE_PARAM] ?: 1f
    val overridden = if (dressing) emptySet() else document.character.overridesOf(instance).outlines.keys
    val hidden = if (mirror && !dressing) document.character.editBase(instance, thisSide = false).outlines.filterKeys { it in overridden } else emptyMap()
    val held by state.rig.held.collectAsState()
    val poseView by state.poseView.collectAsState()
    val posed = rememberPosedParts(state, document.character.shownWith(held).isRest || !posesPane(stage, poseView, locked = false))
    val look by state.dress.look(stage == Stage.DRESS).collectAsState()
    val ghosted = look == GarmentLook.GHOSTED
    val assembled = character ?: return null
    return remember(assembled, selected, outlines, scale, overridden, hidden, posed, ghosted) {
        sheetSelection(assembled, selected, outlines, scale, overridden, hidden, posed, ghosted)
    }
}
