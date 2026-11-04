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
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState

/** One placed part as the panes draw it: what the graph derived for it, in body space. Never changes once made. */
class SheetPart(private val placed: PlacedPart) {
    val id: PartId? get() = placed.id
    val mesh: RenderMesh get() = placed.surface.mesh
    val origin: Vec3 get() = placed.origin
    val mirrored: Boolean get() = placed.mirrored
    val planned: Boolean get() = placed.planned
    val axes: PartAxes = PartAxes.of(placed.template.axis.rest.toVec3(), placed.mirrored)
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
}

/**
 * What the panes draw from one assembled character: the [part] selected with the outlines the document holds for it, the
 * [others] around it, the [box] everything fills, the height of the [floor] and the [drawingScale] its size parameter sets.
 */
class SheetSelection(
    val part: SheetPart,
    val outlines: Map<View, OutlineCurve>,
    val others: List<SheetPart> = emptyList(),
    val box: BodyBox = part.box,
    val floor: Float = part.box.low.y,
    val drawingScale: Float = 1f,
)

/** [character] as the panes draw it with [selected] picked out, or null while that part has built nothing. */
fun sheetSelection(character: AssembledCharacter, selected: PartId, outlines: Map<View, OutlineCurve>, drawingScale: Float = 1f): SheetSelection? {
    val shown = character.parts.filter { it.box != null }.map(::SheetPart)
    val part = shown.firstOrNull { it.id == selected } ?: return null
    val box = character.box ?: return null
    return SheetSelection(part, outlines, shown - part, box, character.floor, drawingScale)
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
    val data = document.character.resolved(instance)
    val outlines = data.outlines
    val scale = data.params[SIZE_PARAM] ?: 1f
    val assembled = character ?: return null
    return remember(assembled, selected, outlines, scale) { sheetSelection(assembled, selected, outlines, scale) }
}
