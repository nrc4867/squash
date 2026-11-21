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

private const val MIN_LINE_POINTS = 2
private const val TINY = 1e-7f

/** A closed mark of a mouth role by its anchors, and whether it draws over the lips. */
class AnchoredMark(val id: MarkId, val anchors: List<Anchor>, val over: Boolean)

/**
 * A mouth's marks by their anchors, as the shaper reads them: its lip line's places, the inner role's drawn shape if any, its teeth and
 * tongue, whether its tongue role is filled, so a tongue no mark draws shows when lifted, and whether its lip line's role is, so the lips
 * show filled bands.
 */
class MouthMarks(
    val lip: List<List<Anchor>>,
    val inner: List<Anchor>? = null,
    val teeth: List<AnchoredMark> = emptyList(),
    val tongue: List<AnchoredMark> = emptyList(),
    val tongueFilled: Boolean = false,
    val lipsFilled: Boolean = false,
)

/** A mouth shaped on the patch [patch] of its part: the [flat] shapes, and their places on the patch through [uv]. */
class ShapedMouth(val patch: String, val flat: FlatMouth, private val frame: MouthFrame) {
    fun uv(places: List<Vec2>): List<SurfaceUv> = places.map(frame::onPatch)
}

/**
 * [marks] on [holder]'s [surface], shaped by [controls] as [style] scales them, in the flat frame laid on the lip line's patch at its
 * middle; null when the lip line has fewer than two places there or the style's line is a beak's seam, which nothing parts on the surface.
 */
fun shapeMouth(holder: PartId, marks: MouthMarks, controls: MarkControls, style: MouthStyle, surface: PartSurface): ShapedMouth? {
    if (style.line == MouthLine.SEAM) return null
    val held = marks.lip.map { mark -> mark.filter { it.part == holder } }
    val patch = held.flatten().firstOrNull()?.patch ?: return null
    val own = held.map { mark -> mark.filter { it.patch == patch } }.filter { it.isNotEmpty() }
    val frame = MouthFrame.of(holder, patch, own.flatten(), surface) ?: return null
    val lip = own.flatMap { mark -> curveThrough(mark.map(frame::flat)) }.sortedBy { it.x }
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
    )
    return ShapedMouth(patch, MouthShaper.shape(drawing, style.scale.of(controls), style.depth, style.span), frame)
}

private const val MIN_LOOP = 3
