package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.PartColor
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.CreaseMarks
import dev.chieppa.squash.character.surface.PosedCreases
import dev.chieppa.squash.character.surface.PaintLayer
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.character.surface.SurfaceSection
import dev.chieppa.squash.character.surface.garmentPiece
import dev.chieppa.squash.character.surface.isFieldRun
import dev.chieppa.squash.character.surface.separatePieces
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.laidOn
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.castShadeTones
import dev.chieppa.squash.render.creaseTones
import dev.chieppa.squash.render.foldTones
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.LayerSides
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.Tones
import androidx.compose.ui.graphics.Color

/** How much of a shaped layer, as an average share, must lie on one side of the body's plane to be darkened as that side. */
private const val HALF_SHARE = 0.5

/**
 * A character's one surface as a fill: its root part's base slot, shaded by the carried shading normals, with each side of a pair's far
 * darkening laid over the surface as a layer that shows by that side's share of each vertex, so a far leg darkens and its join into the
 * body ramps; then each layer of its [paint] in order, each darkened the same way where it lies on a far side, a flat one unshaded. With
 * a [section], only that part's own section of the surface, its paint cut to it; else what of it shows under its garments, [skin], with
 * the shade under a head's chin and its [creases]' shade over all. Its eyes show in the look seen along [toViewer] from each eye's middle, the way to the camera there.
 */
fun surfaceDraw(
    surface: CharacterSurface,
    paint: SurfacePaint?,
    skin: LevelCut?,
    color: BodyColor,
    palette: Palette,
    fallback: PartMaterial,
    shading: ObjectShading,
    toCamera: Vec3,
    section: SurfaceSection? = null,
    toViewer: (Vec3) -> Vec3 = { toCamera },
    creases: CreaseMarks? = null,
): MeshDraw {
    val root = surface.field.runs.firstOrNull { it.part.socket == null } ?: surface.field.runs.first()
    val slot = color.parts[root.partId]?.baseSlot
    val lit = palette.colorOf(slot) ?: fallback.base
    val base = palette.tonesOf(slot, fallback.base, shading, null, toCamera)
    val cut = section?.cut
    val shownCut = cut ?: skin
    val mesh = shownCut?.mesh(surface.render) ?: surface.render
    val sides = if (shownCut == null) {
        sideLayers(mesh, palette, slot, fallback, shading, toCamera, surface.positiveZ, surface.negativeZ)
    } else {
        sideLayers(mesh, palette, slot, fallback, shading, toCamera, shownCut.values(surface.positiveZ), shownCut.values(surface.negativeZ))
    }
    val seen = paint?.seenFrom(toViewer)?.layers.orEmpty()
    val shownLayers = if (section == null) seen else seen.mapNotNull { section.layer(it, surface.render) }
    val creased = creases?.takeIf { section == null }?.let { creaseLayer(mesh, shownCut?.values(it.shade) ?: it.shade, creaseTones(lit)) }
    val chin = surface.chin?.let { ColorLayerDraw(mesh, castShadeTones(base), shownCut?.values(it) ?: it, crease = true) }
    val layers = listOfNotNull(sides) + shownLayers.flatMap { layer -> paintLayerDraws(layer, palette, fallback, shading, toCamera, base.shadow) } +
        listOfNotNull(chin, creased)
    return MeshDraw(mesh, shading.material(base, fallback.ink, toCamera), layers = layers)
}

/** A crease's shade band over [mesh], [shade] one value a vertex, in [tones]. */
private fun creaseLayer(mesh: RenderMesh, shade: FloatArray, tones: Tones) = ColorLayerDraw(mesh, tones, shade, crease = true)

/**
 * A garment's crease layers over its drawn [mesh] from [marks], each value read through [values], in its color [lit]: its joint creases'
 * band, its folds' lighter band, both its own tone a little darker, and on dark cloth in [base] tones the highlight its creases' folds catch.
 */
private fun clothCreaseLayers(mesh: RenderMesh, marks: CreaseMarks, lit: Color, base: Tones, values: (FloatArray) -> FloatArray): List<ColorLayerDraw> = listOfNotNull(
    creaseLayer(mesh, values(marks.shade), creaseTones(lit)),
    marks.folds?.let { creaseLayer(mesh, values(it), foldTones(lit)) },
)

/**
 * Each garment of [dress] as a fill in its color slot, then its own paint's layers, shaded and darkened on a far side as the surface is,
 * then its creases' and folds' bands from [creases]. A garment whose cloth shows what lies under it fills as see-through cloth
 * ([clothDraw]), its far sides darkened in the cloth's own tones.
 */
fun garmentDraws(dress: SurfaceDress, palette: Palette, fallback: PartMaterial, shading: ObjectShading, toCamera: Vec3, creases: PosedCreases? = null): List<MeshDraw> = dress.garments.map { garment ->
    val slot = garment.cover.garment.color
    val lit = palette.colorOf(slot) ?: fallback.base
    val base = palette.tonesOf(slot, fallback.base, shading, null, toCamera)
    val painted = garment.paint?.layers.orEmpty().flatMap { layer -> paintLayerDraws(layer, palette, fallback, shading, toCamera, base.shadow) }
    val creased = creases?.garments?.get(garment.cover.garmentPiece)?.let { clothCreaseLayers(garment.drawn, it, lit, base, garment.cut::values) }.orEmpty()
    val material = shading.material(base, fallback.ink, toCamera)
    val seeThrough = garment.seeThrough
    if (seeThrough != null) return@map MeshDraw(garment.drawn, material, layers = painted + creased, cloth = clothDraw(garment, seeThrough, palette, fallback, shading, toCamera))
    val sides = sideLayers(garment.drawn, palette, slot, fallback, shading, toCamera, garment.cut.values(garment.surface.positiveZ), garment.cut.values(garment.surface.negativeZ))
    MeshDraw(garment.drawn, material, layers = listOfNotNull(sides) + painted + creased)
}

/**
 * One [layer] of paint as fills: its slot shaded, darkened on a far side, or flat and unshaded when it is flat, taken by its tint either
 * way, which reads [skinShadow] as the skin's shadow tone. A shaded layer drawn to a shape lies on one side of the body's plane, so it is darkened
 * whole as the side most of it holds.
 */
private fun paintLayerDraws(layer: PaintLayer, palette: Palette, fallback: PartMaterial, shading: ObjectShading, toCamera: Vec3, skinShadow: Color): List<ColorLayerDraw> {
    val layerLit = palette.colorOf(layer.slot) ?: fallback.base
    if (layer.flat) {
        val shown = tinted(layerLit, layer.tint, palette, fallback.ink, skinShadow)
        return listOf(ColorLayerDraw(layer.mesh, Tones(shown, shown), shape = layer.shape))
    }
    if (layer.shape != null) {
        val side = when {
            (layer.positive?.average() ?: 0.0) > HALF_SHARE -> BodySide.POSITIVE_Z
            (layer.negative?.average() ?: 0.0) > HALF_SHARE -> BodySide.NEGATIVE_Z
            else -> null
        }
        return listOf(ColorLayerDraw(layer.mesh, palette.tonesOf(layer.slot, fallback.base, shading, side, toCamera), shape = layer.shape))
    }
    val tones = palette.tonesOf(layer.slot, fallback.base, shading, null, toCamera).let { tones ->
        if (layer.tint == null) tones else tones.copy(lit = tinted(tones.lit, layer.tint, palette, fallback.ink, skinShadow), shadow = tinted(tones.shadow, layer.tint, palette, fallback.ink, skinShadow))
    }
    return listOf(ColorLayerDraw(layer.mesh, tones, sides = layerSides(palette, layer.slot, fallback, shading, toCamera, layer.positive, layer.negative)))
}

/** [mesh] darkened as each side of the body's plane shows from [toCamera], by each vertex's share of that side, [positive] and [negative]. */
private fun sideLayers(
    mesh: RenderMesh, palette: Palette, slot: SlotName?, fallback: PartMaterial, shading: ObjectShading, toCamera: Vec3, positive: FloatArray?, negative: FloatArray?,
): ColorLayerDraw? = layerSides(palette, slot, fallback, shading, toCamera, positive, negative)?.let {
    ColorLayerDraw(mesh, palette.tonesOf(slot, fallback.base, shading, null, toCamera), sides = it)
}

/** The far sides' tones of [slot] by [positive] and [negative] shares, or null where neither side holds any. */
private fun layerSides(palette: Palette, slot: SlotName?, fallback: PartMaterial, shading: ObjectShading, toCamera: Vec3, positive: FloatArray?, negative: FloatArray?): LayerSides? {
    if (positive == null && negative == null) return null
    return LayerSides(
        palette.tonesOf(slot, fallback.base, shading, BodySide.POSITIVE_Z, toCamera), positive,
        palette.tonesOf(slot, fallback.base, shading, BodySide.NEGATIVE_Z, toCamera), negative,
    )
}

/** What of [part], a part of the one surface, still draws as its own mesh: its pieces that are not runs, a pad, with their own color. */
fun piecesDraw(part: PlacedPart, color: PartColor?, palette: Palette, fallback: PartMaterial, shading: ObjectShading, toCamera: Vec3): MeshDraw? {
    val pieces = separatePieces(part) ?: return null
    val shown = part.surface.patches.filterNot { isFieldRun(part, it) }.map { it.name }.toSet()
    val own = color?.let { PartColor(it.baseSlot, it.layers.filter { layer -> layer.cut.patch in shown }) }
    return coloredPartDraw(
        pieces, own, palette, fallback, shading, part.bodySide(), toCamera, translation(part.origin), 0f,
        cutMesh = { cut -> part.surface.grid(cut.patch)?.let(cut::laidOn) ?: cut.mesh },
        spans = spansOf(part.surface.patches),
    )
}
