package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.rig.CurlHandle
import dev.chieppa.squash.rig.PoseHandles
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.OverlayBuilder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val CIRCLE_STEPS = 40
private const val CURL_SWEEP = 1.7f
private const val ARROW_SPREAD = 0.5f

/**
 * How the Pose stage's handles look, in pixels: [ring] the lit or held handle, [fill] a handle's middle, [edge] its rim, [muted]
 * the pins, poles' leads and stretch tags; with the sizes of each.
 */
class PoseHandleLook(
    val ring: Color,
    val accent: Color,
    val fill: Color,
    val edge: Color,
    val muted: Color,
    val density: Float,
) {
    val width: Float get() = 2f * density
    val thin: Float get() = 1.5f * density
    val body: Float get() = 15f * density
    val end: Float get() = 6f * density
    val litEnd: Float get() = 8f * density
    val pole: Float get() = 5f * density
    val look: Float get() = 8f * density
    val curl: Float get() = 18f * density
    val pin: Float get() = 5f * density
    val dash: Float get() = 4f * density
    val reach: Float get() = 10f * density
}

/** Where each handle a press can take shows through [sight], in pixels. */
fun PoseHandles.grips(sight: SurfaceView): Map<PoseGrip, Offset> = buildMap {
    body?.let(sight::toPixel)?.let { put(PoseGrip.Body, it) }
    ends.forEach { (id, at) -> sight.toPixel(at)?.let { put(PoseGrip.End(id), it) } }
    poles.forEach { (id, pole) -> sight.toPixel(pole.at)?.let { put(PoseGrip.Pole(id), it) } }
    curls.forEach { curl -> sight.toPixel(curl.tip)?.let { put(PoseGrip.Curl(curl.tail.part), it) } }
    look?.let(sight::toPixel)?.let { put(PoseGrip.Look, it) }
}

/**
 * The handle among [allowed] nearest [pixel] through [sight], within [PoseHandleLook.reach] pixels, or null. A tail's curl is reached
 * along the arc it draws, a dot at its middle.
 */
fun PoseHandles.gripAt(pixel: Offset, sight: SurfaceView, look: PoseHandleLook, allowed: (PoseGrip) -> Boolean): PoseGrip? {
    val arcs = curls.mapNotNull { curl -> curlArcPoints(curl, sight, look)?.let { PoseGrip.Curl(curl.tail.part) to it } }.toMap()
    fun distance(grip: PoseGrip, at: Offset): Float = arcs[grip]?.minOf { (it - pixel).getDistance() } ?: (at - pixel).getDistance()
    return grips(sight).filterKeys(allowed).entries
        .map { it.key to distance(it.key, it.value) }
        .filter { it.second <= look.reach }
        .minByOrNull { it.second }?.first
}

/**
 * Draws [handles] through [sight]: pins and leads first, then the handles, [lit] and the [picked] limb's end in the accent; [shown]
 * picks which handles show.
 */
fun OverlayBuilder.poseHandles(handles: PoseHandles, sight: SurfaceView, look: PoseHandleLook, lit: PoseGrip?, picked: PoseGrip?, shown: (PoseGrip) -> Boolean) {
    handles.pins.values.forEach { at -> sight.toPixel(at)?.let { pinGlyph(it, look) } }
    handles.stretches.forEach { tag ->
        val from = sight.toPixel(tag.from) ?: return@forEach
        val to = sight.toPixel(tag.to) ?: return@forEach
        stretchGlyph(from, to, look)
    }
    if (shown(PoseGrip.Look)) {
        val at = handles.look?.let(sight::toPixel)
        val from = handles.lookFrom?.let(sight::toPixel)
        if (at != null && from != null) dashed(listOf(from, at), look.muted, look.thin, look.dash, look.dash)
        if (at != null) handleDot(at, look.look, PoseGrip.Look == lit, look)
    }
    handles.poles.forEach { (id, pole) ->
        val grip = PoseGrip.Pole(id)
        if (!shown(grip)) return@forEach
        val knee = sight.toPixel(pole.knee) ?: return@forEach
        val at = sight.toPixel(pole.at) ?: return@forEach
        dashed(listOf(knee, at), look.muted, look.thin, look.dash, look.dash)
        handleDot(at, look.pole, grip == lit, look)
    }
    handles.curls.forEach { curl ->
        val grip = PoseGrip.Curl(curl.tail.part)
        if (!shown(grip)) return@forEach
        val points = curlArcPoints(curl, sight, look) ?: return@forEach
        curlArc(points, if (grip == lit) look.accent else look.ring, look)
    }
    if (shown(PoseGrip.Body)) handles.body?.let(sight::toPixel)?.let { at ->
        val color = if (lit == PoseGrip.Body) look.accent else look.ring
        circle(at, look.body, color, look.width)
        dot(at, look.width * 1.5f, color)
    }
    handles.ends.forEach { (id, at) ->
        val grip = PoseGrip.End(id)
        if (!shown(grip)) return@forEach
        val on = grip == lit || grip == picked
        sight.toPixel(at)?.let { handleDot(it, if (on) look.litEnd else look.end, on, look) }
    }
}

private fun OverlayBuilder.handleDot(at: Offset, radius: Float, lit: Boolean, look: PoseHandleLook) {
    dot(at, radius, if (lit) look.edge else look.ring)
    dot(at, radius - look.thin, if (lit) look.accent else look.fill)
}

internal fun OverlayBuilder.circle(center: Offset, radius: Float, color: Color, width: Float) {
    val path = List(CIRCLE_STEPS + 1) { i ->
        val angle = 2.0 * PI * i / CIRCLE_STEPS
        center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
    }
    path.zipWithNext().forEach { (a, b) -> line(a, b, color, width) }
}

/** The arc a tail's [curl] handle draws through [sight]: round its tip, curling toward the way it turns, in pixels. */
private fun curlArcPoints(curl: CurlHandle, sight: SurfaceView, look: PoseHandleLook): List<Offset>? {
    val tip = sight.toPixel(curl.tip) ?: return null
    val ahead = sight.toPixel(curl.tip + curl.toward * (1f / sight.pixelsPerUnitAt(curl.tip).coerceAtLeast(1e-6f))) ?: return null
    val start = atan2(ahead.y - tip.y, ahead.x - tip.x) - CURL_SWEEP
    return List(CIRCLE_STEPS / 2 + 1) { i ->
        val angle = start + CURL_SWEEP * i / (CIRCLE_STEPS / 2)
        tip + Offset(cos(angle), sin(angle)) * look.curl
    }
}

/** A curl handle's arc along [points], with a head at its leading end. */
private fun OverlayBuilder.curlArc(points: List<Offset>, color: Color, look: PoseHandleLook) {
    points.zipWithNext().forEach { (a, b) -> line(a, b, color, look.width) }
    val end = points.last()
    val along = (end - points[points.size - 2]).let { it / it.getDistance().coerceAtLeast(1e-6f) }
    val back = Offset(-along.x, -along.y) * (look.curl * ARROW_SPREAD)
    val side = Offset(-along.y, along.x) * (look.curl * ARROW_SPREAD * 0.6f)
    line(end, end + back + side, color, look.width)
    line(end, end + back - side, color, look.width)
}

/** The pin glyph standing on the ground at [at]: a needle, a head and its cap, as the Pin icon draws it. */
private fun OverlayBuilder.pinGlyph(at: Offset, look: PoseHandleLook) {
    val s = look.pin
    val head = at + Offset(0f, -s * 1.8f)
    line(at, head, look.muted, look.thin)
    line(head + Offset(-s, 0f), head + Offset(s, 0f), look.muted, look.thin)
    line(head + Offset(-s * 0.5f, 0f), head + Offset(-s * 0.4f, -s * 1.6f), look.muted, look.thin)
    line(head + Offset(s * 0.5f, 0f), head + Offset(s * 0.4f, -s * 1.6f), look.muted, look.thin)
    line(head + Offset(-s * 0.6f, -s * 1.6f), head + Offset(s * 0.6f, -s * 1.6f), look.muted, look.thin)
}

/** A double-headed arrow beside the stretched span from [from] to [to]. */
private fun OverlayBuilder.stretchGlyph(from: Offset, to: Offset, look: PoseHandleLook) {
    val span = to - from
    val length = span.getDistance()
    if (length < look.end * 2f) return
    val along = span / length
    val side = Offset(-along.y, along.x) * (look.end * 2f)
    val a = from + side + along * look.end
    val b = to + side - along * look.end
    line(a, b, look.muted, look.thin)
    val head = look.end * 0.8f
    val across = Offset(-along.y, along.x) * head
    line(a, a + along * head + across, look.muted, look.thin)
    line(a, a + along * head - across, look.muted, look.thin)
    line(b, b - along * head + across, look.muted, look.thin)
    line(b, b - along * head - across, look.muted, look.thin)
}
