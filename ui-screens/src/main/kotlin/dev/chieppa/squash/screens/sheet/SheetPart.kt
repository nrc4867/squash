package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.RenderMesh
import dev.chieppa.squash.render.silhouette
import dev.chieppa.squash.screens.AppState

/** One build of a part as the panes draw it: its real mesh, axes and locked silhouettes. Never changes once made. */
class SheetPart(
    val id: PartId,
    val mesh: RenderMesh,
    val axes: PartAxes,
    val extents: PartExtents,
    val silhouettes: Map<View, List<List<Vec3>>>,
) {
    /** The middle of the part. */
    val center: Vec3 = axes.middle(extents)

    /** The radius of a sphere around [center] that holds the part. */
    val radius: Float = Vec3(extents.along.length, extents.up.length, extents.side.length).length / 2f

    /** The point under [center] at the height of the part's lowest vertex. */
    val floor: Vec3 = Vec3(center.x, (0 until mesh.vertexCount).minOf { mesh.position(it).y }, center.z)
}

/** Everything the panes derive from one build of a part, or null for a build with nothing in it. */
fun sheetPart(id: PartId, template: PartTemplate, built: PartMesh): SheetPart? {
    val mesh = RenderMesh.of(built)
    val axes = PartAxes.of(template.axis.rest.toVec3())
    val extents = axes.extentsOf(mesh) ?: return null
    return SheetPart(id, mesh, axes, extents, View.entries.associateWith { silhouette(built, axes.basis(it).back) })
}

/** The selected part's build with the outlines the document holds for it now. */
class SheetSelection(val part: SheetPart, val outlines: Map<View, OutlineCurve>)

/** The selected part through the real build, or null while nothing is selected or it has not built yet. */
@Composable
fun rememberSheetSelection(state: AppState): SheetSelection? {
    val session by state.session.collectAsState()
    val id by state.selectedPart.collectAsState()
    val open = session ?: return null
    val selected = id ?: return null
    val document by remember(open) { open.document }.collectAsState()
    val instance = document.character.part(selected) ?: return null
    val built by remember(open, selected) { open.mesh(selected).output }.collectAsState()
    val template = remember(open, instance.template) { open.templates[instance.template] }
    val part = remember(selected, template, built) { built?.let { sheetPart(selected, template, it) } } ?: return null
    return SheetSelection(part, instance.outlines)
}
