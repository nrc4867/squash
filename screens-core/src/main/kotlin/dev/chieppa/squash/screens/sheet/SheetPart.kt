package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.character.build.AssembledCharacter
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
import dev.chieppa.squash.geom.showing
import java.util.Collections
import java.util.IdentityHashMap
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.screens.AppState

/** One placed part as the panes draw it: what the graph derived for it, in body space. Never changes once made. */
class SheetPart(private val placed: PlacedPart) {
    val id: PartId? get() = placed.id
    val mesh: RenderMesh get() = placed.surface.mesh
    val origin: Vec3 get() = placed.origin
    val mirrored: Boolean get() = placed.mirrored
    val planned: Boolean get() = placed.planned
    val side: BodySide? get() = placed.bodySide()
    val lengthFactor: Float get() = placed.lengthFactor
    val axes: PartAxes = PartAxes.of(placed.template.axis.rest.toVec3(), placed.mirrored, placed.template.axis.up?.toVec3())
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

    private val posedCuts = Collections.synchronizedMap(IdentityHashMap<PatchCut, Pair<RenderMesh, RenderMesh>>())

    /** [cut], a color layer on this part, laid on [posed], this part's mesh in a pose; kept while the pose holds. */
    fun posedCut(cut: PatchCut, posed: RenderMesh): RenderMesh {
        posedCuts[cut]?.takeIf { it.first === posed }?.let { return it.second }
        val grid = placed.surface.showing(posed).grid(cut.patch) ?: return cut.mesh
        return cut.laidOn(grid).also { posedCuts[cut] = posed to it }
    }
}

/**
 * What the panes draw from one assembled character: the [part] selected with the outlines the document holds for it, the
 * [others] around it, the [box] everything fills, the height of the [floor] and the [drawingScale] its size parameter sets.
 * [overridden] is the views this side holds for itself, and [hidden] what the pair holds under those while an edit lands on
 * the pair. [posed] is each part's mesh in the character's pose, or null while the pose leaves every bone at rest.
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
    val posed: Map<PartId, RenderMesh>? = null,
)

/** [character] as the panes draw it with [selected] picked out, or null while that part has built nothing. */
fun sheetSelection(
    character: AssembledCharacter,
    selected: PartId,
    outlines: Map<View, OutlineCurve>,
    drawingScale: Float = 1f,
    overridden: Set<View> = emptySet(),
    hidden: Map<View, OutlineCurve> = emptyMap(),
    posed: Map<PartId, RenderMesh>? = null,
): SheetSelection? {
    val shown = character.parts.filter { it.box != null }.map(::SheetPart)
    val part = shown.firstOrNull { it.id == selected } ?: return null
    val box = character.box ?: return null
    return SheetSelection(part, outlines, shown - part, box, character.floor, drawingScale, overridden, hidden, posed)
}

/**
 * Each part's mesh in the open character's pose, as its posed lines were found on it so the two always agree, or null while [atRest]
 * or before the posed lines have been found.
 */
@Composable
private fun rememberPosedMeshes(state: AppState, atRest: Boolean): Map<PartId, RenderMesh>? {
    val session by state.session.collectAsState()
    val open = session ?: return null
    val lines by remember(open) { open.posedLines.output }.collectAsState()
    val meshes = remember(lines) { lines?.parts?.mapValues { it.value.part.surface.mesh } }
    return if (atRest) null else meshes
}

/** The selected part and the character around it through the real build, or null while nothing is selected or it has not built yet. */
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
    val data = document.character.resolved(instance)
    val outlines = data.outlines
    val scale = data.params[SIZE_PARAM] ?: 1f
    val overridden = document.character.overridesOf(instance).outlines.keys
    val hidden = if (mirror) document.character.editBase(instance, thisSide = false).outlines.filterKeys { it in overridden } else emptyMap()
    val held by state.rig.held.collectAsState()
    val posed = rememberPosedMeshes(state, (held ?: document.character.pose).isRest)
    val assembled = character ?: return null
    return remember(assembled, selected, outlines, scale, overridden, hidden, posed) { sheetSelection(assembled, selected, outlines, scale, overridden, hidden, posed) }
}
