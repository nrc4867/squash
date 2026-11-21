package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.hair.fit.HeadView
import dev.chieppa.squash.character.hair.fit.TraceImage
import dev.chieppa.squash.character.hair.fit.traceHairOutline
import dev.chieppa.squash.character.hair.lockColors
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.HairOutline
import dev.chieppa.squash.document.HairOutlineView
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.references.ReferenceImageCache
import dev.chieppa.squash.screens.references.traceCorners
import dev.chieppa.squash.screens.sheet.colorOf
import kotlin.math.roundToInt

/** How far in Lab a reference image's pixel may lie from the hair's colours and still be traced as hair. */
private const val TRACE_TOLERANCE = 26f

/** How far below the top of the head's hair, as a share of its box's height, the image is read for the hair's colour as drawn. */
private const val SAMPLE_BELOW_TOP = 0.08f

private const val BYTE = 0xFF

/**
 * [head]'s hair outline with its front traced from the reference image drawn in the front view: the hair keyed by its palette colour and
 * by the colour the image shows just under the top of the hair as built, outlined, and carried into the head's face plane; its other views
 * kept. Null when the front view has no shown reference image or no hair is found in it.
 */
internal fun tracedHairOutline(document: SquashDocument, images: ReferenceImageCache, assembled: AssembledCharacter, head: PartId): HairOutline? {
    val character = document.character
    val trace = character.traces[View.FRONT]?.takeIf { it.shown } ?: return null
    val reference = document.references.image(trace.image) ?: return null
    val pixels = images.of(reference)?.trace ?: return null
    val headPart = assembled.part(head) ?: return null
    val view = HeadView.of(headPart, HeadView.sizeOf(character, headPart), OutlineView.FRONT)
    val (topLeft, topRight, _, bottomLeft) = traceCorners(trace, View.FRONT, reference.width, reference.height)
    val across = topRight - topLeft
    val down = bottomLeft - topLeft
    fun world(x: Float, y: Float): Vec3 = topLeft + across * (x / pixels.width) + down * (y / pixels.height)
    fun rgbAt(x: Int, y: Int): Int {
        val c = pixels.rgba[y.coerceIn(0, pixels.height - 1) * pixels.width + x.coerceIn(0, pixels.width - 1)]
        return ((c and BYTE) shl 16) or (((c shr 8) and BYTE) shl 8) or ((c shr 16) and BYTE)
    }
    val keys = buildList {
        val group = character.lockGroups.firstOrNull { it.on == head }
        val slot = group?.let { lockColors(character)[lockGroupPartId(it.id)]?.baseSlot }
        character.palette.colorOf(slot)?.let { add(rgbOf(it)) }
        val hairTop = assembled.parts.filter { it.locks?.head == head || it.id == group?.mass }.mapNotNull { it.box }.maxOfOrNull { it.high.y }
        val box = headPart.box
        if (hairTop != null && box != null) {
            val p = box.center.let { Vec3(it.x, hairTop - box.size.y * SAMPLE_BELOW_TOP, it.z) }
            val u = ((p - topLeft) dot across) / (across dot across)
            val v = ((p - topLeft) dot down) / (down dot down)
            if (u in 0f..1f && v in 0f..1f) add(rgbAt((u * pixels.width).roundToInt(), (v * pixels.height).roundToInt()))
        }
    }
    val image = TraceImage(pixels.width, pixels.height, ::rgbAt)
    val points = traceHairOutline(image, keys, TRACE_TOLERANCE) { x, y -> view.flat(world(x, y)) } ?: return null
    val front = HairOutlineView(OutlineView.FRONT, points.map { CurvePoint(it.x, it.y) })
    val held = character.hairOutlineOn(head)
    return HairOutline(head, listOf(front) + held?.views.orEmpty().filter { it.view != OutlineView.FRONT }, held?.fit ?: true)
}

private fun rgbOf(color: Color): Int =
    ((color.red * BYTE).roundToInt() shl 16) or ((color.green * BYTE).roundToInt() shl 8) or (color.blue * BYTE).roundToInt()
