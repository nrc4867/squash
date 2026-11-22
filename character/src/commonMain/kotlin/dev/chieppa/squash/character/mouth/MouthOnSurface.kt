package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.character.model.MouthLine
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2
import kotlin.math.floor
import kotlin.math.hypot

private const val MIN_LINE_POINTS = 2
private const val TINY = 1e-7f

/** A closed mark of a mouth role by its anchors, and whether it draws over the lips. */
class AnchoredMark(val id: MarkId, val anchors: List<Anchor>, val over: Boolean)

/**
 * A mouth's marks by their anchors, as the shaper reads them: its lip line's places, the inner role's drawn shape if any, its teeth and
 * tongue, whether its tongue role is filled, so a tongue no mark draws shows when lifted, and whether its lip line's role is, so the lips
 * show filled bands; and whether a tongue shows at all. [lipRegions] are the borders of the regions painted in the lip line role's fill, the
 * lips' color as drawn, which the lips carry as they move in place of bands.
 */
class MouthMarks(
    val lip: List<List<Anchor>>,
    val inner: List<Anchor>? = null,
    val teeth: List<AnchoredMark> = emptyList(),
    val tongue: List<AnchoredMark> = emptyList(),
    val tongueFilled: Boolean = false,
    val lipsFilled: Boolean = false,
    val tongueShown: Boolean = true,
    val lipRegions: List<List<Anchor>> = emptyList(),
)

/**
 * A mouth shaped on the patch [patch] of its part: the [flat] shapes, and their places on the patch through [uv]; [lipUv] holds each lip
 * mark's curve taken through its own u and v, place for place with its flat curve.
 */
class ShapedMouth(
    val holder: PartId,
    val patch: String,
    val flat: FlatMouth,
    private val frame: MouthFrame,
    private val lipUv: List<List<Vec2>> = emptyList(),
) {
    fun uv(places: List<Vec2>): List<SurfaceUv> = places.map(frame::onPatch)

    /** The upper lip's pieces on the patch: along the lip marks themselves while the lip holds its drawn line, else through [uv]. */
    fun upperUv(): List<List<SurfaceUv>> = flat.upperRuns?.mapNotNull { run ->
        lipUv.getOrNull(run.mark)?.subList(run.range.first, run.range.last + 1)?.map { SurfaceUv(it.x, it.y - floor(it.y)) }
    } ?: flat.upper.map(::uv)

    /** [place]'s u and v on the patch; null where the place found lies further than [within] from it in the flat frame. */
    fun uvWithin(place: Vec2, within: Float): SurfaceUv? {
        val uv = frame.onPatch(place)
        val back = frame.flat(Anchor(holder, patch, uv.u, uv.v))
        return uv.takeIf { hypot(back.x - place.x, back.y - place.y) <= within }
    }
}

/**
 * [marks] on [holder]'s [surface], shaped by [controls] as [style] scales them, in the flat frame laid on the lip line's patch at its
 * middle, with [dentition]'s teeth along its lips; null when the lip line has fewer than two places there or the style's line is a beak's
 * seam, which nothing parts on the surface.
 */
fun shapeMouth(
    holder: PartId,
    marks: MouthMarks,
    controls: MarkControls,
    style: MouthStyle,
    surface: PartSurface,
    dentition: MouthDentition = MouthDentition.NONE,
): ShapedMouth? {
    if (style.line == MouthLine.SEAM) return null
    val held = marks.lip.map { mark -> mark.filter { it.part == holder } }
    val patch = held.flatten().firstOrNull()?.patch ?: return null
    val own = held.map { mark -> mark.filter { it.patch == patch } }.filter { it.isNotEmpty() }
    val frame = MouthFrame.of(holder, patch, own, surface) ?: return null
    val curves = own.map { mark -> curveThrough(mark.map(frame::flat)) }
    val lip = curves.flatten().sortedBy { it.x }
    if (lip.size < MIN_LINE_POINTS || lip.last().x - lip.first().x < TINY) return null
    fun flat(anchors: List<Anchor>) = anchors.filter { it.part == holder && it.patch == patch }.map(frame::flat)
    fun flat(mark: AnchoredMark) = FlatMark(mark.id, flat(mark.anchors), mark.over)
    val drawing = MouthDrawing(
        lip = lip,
        inner = marks.inner?.let(::flat),
        teeth = marks.teeth.map(::flat).filter { it.places.size >= MIN_LOOP },
        tongue = marks.tongue.map(::flat).filter { it.places.size >= MIN_LOOP },
        tongueFilled = marks.tongueFilled,
        lipsFilled = marks.lipsFilled,
        lipUpper = style.upperLip,
        lipLower = style.lowerLip,
        dentition = dentition,
        tongueShown = marks.tongueShown,
        lipMarks = curves,
        lipShapes = marks.lipRegions.map { border -> if (border.all { it.part == holder && it.patch == patch }) border.map(frame::flat) else emptyList() },
    )
    val lipUv = own.map { mark -> curveThrough(mark.map { Vec2(it.u, mark.first().v + unwrapped(it.v - mark.first().v)) }) }
    return ShapedMouth(holder, patch, MouthShaper.shape(drawing, style.controlsOf(controls), style.depth, style.span), frame, lipUv)
}

private fun unwrapped(dv: Float) = dv - floor(dv + 0.5f)

private const val MIN_LOOP = 3
