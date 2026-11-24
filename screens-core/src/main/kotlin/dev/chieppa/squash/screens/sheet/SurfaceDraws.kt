package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartColor
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.SlottedCut
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.WidthProfile
import dev.chieppa.squash.geom.WidthSample
import dev.chieppa.squash.geom.dragProfile
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.MeshInstance
import dev.chieppa.squash.character.copies.CopyPlace
import dev.chieppa.squash.character.copies.PartCopies
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.RibbonPoint
import dev.chieppa.squash.render.StrokeRibbon
import dev.chieppa.stretch.gpu.Mat4

/** How far along a mark with no width of its own its ends taper, as a share of its length. */
private const val END_TAPER = 0.25f

/** Which side of the body this part lies on, when it has a side. */
fun PlacedPart.bodySide(): BodySide? = when {
    side == null -> null
    origin.z >= 0f -> BodySide.POSITIVE_Z
    else -> BodySide.NEGATIVE_Z
}

/** The color of [slot] in this palette, or null when it has no such slot. */
fun Palette.colorOf(slot: SlotName?): Color? = slot?.let(::color)?.let { Color(it.red, it.green, it.blue) }

/** The shadow tone [slot] carries of its own in this palette, or null when it has none. */
fun Palette.shadowOf(slot: SlotName?): Color? = slot?.let(::shadow)?.let { Color(it.red, it.green, it.blue) }

/**
 * A part's draw with its color from [palette]. Its base shows the slot its work names; with none named, or one the palette does
 * not hold, [fallback]'s base, the look's own part color. A layer whose slot the palette does not hold is not drawn, so what is
 * under it shows. [side] and [toCamera] choose its tones, each slot's material its finish ([tonesOf]). It draws at its base slot's opacity. With [copies] it draws
 * once for each copy, [mesh] being the part's own at rest, each in the slot that copy shows, its own base where it names none. A slot
 * whose material fades in another ([dev.chieppa.squash.document.MaterialFade]) draws that slot over itself by how far along its part each
 * place lies: a layer by its patch's run of u in [spans], the base by [baseAlong]. A shaped tooth's slot with no material draws as
 * [TEETH_MATERIAL].
 */
fun coloredPartDraw(
    mesh: RenderMesh,
    color: PartColor?,
    palette: Palette,
    fallback: PartMaterial,
    shading: ObjectShading,
    side: BodySide?,
    toCamera: Vec3,
    model: Mat4,
    outlinePixels: Float,
    cutMesh: (PatchCut) -> RenderMesh = { it.mesh },
    copies: PartCopies? = null,
    spans: Map<String, ClosedFloatingPointRange<Float>> = emptyMap(),
    baseAlong: FloatArray? = null,
): MeshDraw {
    val base = palette.tonesOf(color?.baseSlot, fallback.base, shading, side, toCamera)
    fun drawn(cuts: List<SlottedCut<SlotName>>) = cuts.flatMap { layer ->
        val shown = palette.forLayer(layer.cut.patch, layer.slot)
        val lit = shown.colorOf(layer.slot) ?: return@flatMap emptyList()
        val laid = cutMesh(layer.cut)
        val own = ColorLayerDraw(laid, shown.tonesOf(layer.slot, lit, shading, side, toCamera), layer.cut.alphas)
        val fade = shown.look(layer.slot).fade
        val faded = fade?.let { f ->
            shown.colorOf(f.slot)?.let { to ->
                val along = alongOf(layer.cut, spans)
                ColorLayerDraw(laid, shown.tonesOf(f.slot, to, shading, side, toCamera), FloatArray(along.size) { i -> f.share(along[i]) * (layer.cut.alphas?.get(i) ?: 1f) })
            }
        }
        listOfNotNull(own, faded)
    }
    val baseFade = color?.baseSlot?.let { slot -> palette.look(slot).fade }?.takeIf { baseAlong != null && baseAlong.size == mesh.vertexCount }?.let { f ->
        palette.colorOf(f.slot)?.let { to -> ColorLayerDraw(mesh, palette.tonesOf(f.slot, to, shading, side, toCamera), FloatArray(mesh.vertexCount) { i -> f.share(requireNotNull(baseAlong)[i]) }) }
    }
    val layers = listOfNotNull(baseFade) + drawn(color?.layers.orEmpty())
    val copyLayers = color?.copies?.map(::drawn)
    val material = shading.material(base, fallback.ink, toCamera)
    val instances = copies?.places?.mapIndexed { i, place ->
        val shade = place.slot?.let { slot ->
            palette.colorOf(slot)?.let { palette.tonesOf(slot, it, shading, side, toCamera) }?.let { shading.material(it, fallback.ink, toCamera) }
        }
        MeshInstance(copyModel(place), shade, copyLayers?.getOrNull(i))
    }.orEmpty()
    return MeshDraw(mesh, material, model, outlinePixels, opacity = color?.baseSlot?.let(palette::opacity) ?: 1f, layers = layers, instances = instances)
}

/** The matrix taking a part's own mesh to where [place], one of its copies, shows it. */
fun copyModel(place: CopyPlace): Mat4 = Mat4.of(
    place.turn.x.x, place.turn.x.y, place.turn.x.z, 0f,
    place.turn.y.x, place.turn.y.y, place.turn.y.z, 0f,
    place.turn.z.x, place.turn.z.y, place.turn.z.z, 0f,
    place.origin.x, place.origin.y, place.origin.z, 1f,
)

/**
 * A mark's weight along it: its stops, or with none the line rule's own weight, its ends narrowing over [endTaper] of its length each.
 */
fun markProfile(width: MarkWidth, endTaper: Float = END_TAPER): WidthProfile {
    val stops = width.stops.map { WidthSample(it.at, it.scale) }.ifEmpty { listOf(WidthSample(endTaper, 1f), WidthSample(1f - endTaper, 1f)) }
    return requireNotNull(dragProfile(stops, fromPlane = false))
}

/** The ribbon along this mark's resolved points, or null when they do not span two places. */
fun ResolvedMark.ribbon(): StrokeRibbon? = StrokeRibbon.along(points.map { RibbonPoint(it.position, it.normal) }, markProfile(width))

/** This mark as a ribbon in [color] under [rule], or null when it has no ribbon or is not inked. */
fun ResolvedMark.ribbonDraw(rule: LineRule, color: Color, model: Mat4 = Mat4.Identity): RibbonDraw? =
    if (ink) ribbon()?.let { RibbonDraw(it, rule, color, model) } else null
