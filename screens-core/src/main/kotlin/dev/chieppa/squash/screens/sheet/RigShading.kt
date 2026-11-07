package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.Tones
import dev.chieppa.stretch.frame.ViewportOverlay

/** One flat color over a part's whole mesh, each vertex at its opacity in [alphas], in the mesh's vertex order. */
@Immutable
class RigLayer(val color: Color, val alphas: FloatArray)

/**
 * How the Rig stage shows the character in every pane in place of its color and lines: each part in its flat [colors], with its
 * [layers] over it in order, on the mesh the pose shows, and [overlay] drawn over each sight. A view that shows it shows the
 * whole character.
 */
@Immutable
class RigShading(
    val colors: Map<PartId, Color>,
    val layers: Map<PartId, List<RigLayer>>,
    val overlay: (SurfaceView) -> ViewportOverlay = { ViewportOverlay.Empty },
)

/** [part] as the Rig stage draws it: its flat color with its layers, on its mesh in [posed] when the pose moves it, outlined [outline] wide. */
fun SheetLook.rigDraw(part: SheetPart, rig: RigShading, outline: Float, posed: Map<PartId, dev.chieppa.squash.geom.RenderMesh>? = null): MeshDraw {
    val id = part.id
    val mesh = id?.let { posed?.get(it) } ?: part.mesh
    val base = id?.let(rig.colors::get) ?: this.part.base
    val material = PartMaterial(base, base, this.part.ink, Shading.FLAT)
    val layers = id?.let(rig.layers::get).orEmpty().map { ColorLayerDraw(mesh, Tones(it.color, it.color), it.alphas) }
    return MeshDraw(mesh, material, part.model(), outline, layers = layers)
}
