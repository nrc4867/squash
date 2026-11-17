package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.Tones
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.character.surface.separatePieces
import dev.chieppa.stretch.frame.ViewportOverlay

/** One flat color over a part's whole mesh, each vertex at its opacity in [alphas], in the mesh's vertex order. */
@Immutable
class RigLayer(val color: Color, val alphas: FloatArray)

/**
 * A one surface as the Rig stage colors it: in the [base] color, with its [layers] over it in order, one value a vertex of its drawn mesh,
 * drawn on [rest] or, while the pose moves it, [posed]; the parts it holds, [parts], draw only their pieces that are not runs.
 */
@Immutable
class RigSurface(val parts: Set<PartId>, val base: Color, val layers: List<RigLayer>, val rest: RenderMesh, val posed: RenderMesh?)

/**
 * How the Rig stage shows the character in every pane in place of its color and lines: each part in its flat [colors], with its
 * [layers] over it in order, on the mesh the pose shows, its one [surface] colored the same way, and [overlay] drawn over each sight.
 * A view that shows it shows the whole character.
 */
@Immutable
class RigShading(
    val colors: Map<PartId, Color>,
    val layers: Map<PartId, List<RigLayer>>,
    val overlay: (SurfaceView) -> ViewportOverlay = { ViewportOverlay.Empty },
    val surface: RigSurface,
)

/**
 * [part] as the Rig stage draws it: its flat color with its layers, on its mesh in [posed] when the pose moves it, outlined [outline] wide;
 * a part the rig's one surface holds draws only its pieces that are not runs, null with none.
 */
fun SheetLook.rigDraw(part: SheetPart, rig: RigShading, outline: Float, posed: Map<PartId, PlacedPart>? = null): MeshDraw? {
    val id = part.id
    val placed = id?.let { posed?.get(it) } ?: part.placedPart
    val shown = placed.surface.mesh
    val base = id?.let(rig.colors::get) ?: this.part.base
    val material = PartMaterial(base, base, this.part.ink, Shading.FLAT)
    if (id != null && rig.surface.parts.contains(id)) {
        return separatePieces(placed)?.let { MeshDraw(it, material, part.model(), outline) }
    }
    val layers = id?.let(rig.layers::get).orEmpty().map { ColorLayerDraw(shown, Tones(it.color, it.color), it.alphas) }
    return MeshDraw(shown, material, part.model(), outline, layers = layers)
}

/** [rig]'s one surface as the Rig stage draws it, posed when [posedPane] and the pose moves it, outlined [outline] wide. */
fun SheetLook.rigSurfaceDraws(rig: RigShading, outline: Float, posedPane: Boolean): List<MeshDraw> {
    val surface = rig.surface
    val mesh = if (posedPane) surface.posed ?: surface.rest else surface.rest
    val material = PartMaterial(surface.base, surface.base, part.ink, Shading.FLAT)
    return listOf(MeshDraw(mesh, material, outlinePixels = outline, layers = surface.layers.map { ColorLayerDraw(mesh, Tones(it.color, it.color), it.alphas) }))
}
