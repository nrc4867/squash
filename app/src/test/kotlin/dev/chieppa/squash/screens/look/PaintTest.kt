package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Points along each test border. */
private const val BORDER_POINTS = 48

/**
 * Paints test regions whose borders are hard for a coarse mesh: on the torso a narrow stripe, a jagged fur line and a skull print with
 * its eye sockets, nose and teeth gaps in the torso's slot, and on the head a thin eyeliner over the eye at [eye] (u, v). Each is a
 * region of the part, in its (u, v); the slots are whatever the palette names.
 */
fun paintTest(session: CharacterSession, eye: Pair<Float, Float>?, dark: String, light: String, base: String) {
    val character = session.document.value.character
    val torso = PartId("torso").takeIf { character.part(it) != null }
    if (torso != null) {
        region(session, torso, "test-stripe", dark, rect(0.50f, 0.525f, 0.08f, 0.32f))
        region(session, torso, "test-fur", light, jagged())
        skull(session, torso, light, base)
    }
    val head = PartId("head")
    if (eye != null && character.part(head) != null) region(session, head, "test-liner", dark, liner(eye.first, eye.second))
}

private fun region(session: CharacterSession, part: PartId, id: String, slot: String, border: List<Pair<Float, Float>>) {
    val anchors = border.map { (u, v) -> Anchor(part, u = u, v = ((v % 1f) + 1f) % 1f) }
    session.send(SetRegion(part, RegionId(id), Region(RegionId(id), anchors, SlotName(slot))))
}

private fun rect(u0: Float, u1: Float, v0: Float, v1: Float) = listOf(u0 to v0, u0 to v1, u1 to v1, u1 to v0)

/** A band low on the torso whose upper edge is a row of sharp teeth, 0.025 of v apart and 0.04 of u high. */
private fun jagged(): List<Pair<Float, Float>> {
    val teeth = 12
    val v0 = 0.36f
    val v1 = 0.66f
    val top = ArrayList<Pair<Float, Float>>()
    for (k in 0..teeth * 2) top += (if (k % 2 == 0) 0.30f else 0.34f) to v0 + (v1 - v0) * k / (teeth * 2)
    return listOf(0.22f to v1, 0.22f to v0) + top
}

/** A skull about 0.12 of u tall on the torso's front-left: a cranium and jaw, two sockets, a nose and three gaps between teeth. */
private fun skull(session: CharacterSession, torso: PartId, light: String, base: String) {
    val cu = 0.66f
    val cv = 0.82f
    val head = (0 until BORDER_POINTS).map { k ->
        val a = 2.0 * PI * k / BORDER_POINTS
        val r = if (sin(a) < -0.3) 0.6f else 1f
        (cu + 0.05f * sin(a).toFloat() * r) to (cv + 0.05f * cos(a).toFloat())
    }
    region(session, torso, "test-skull", light, head)
    region(session, torso, "test-socket-l", base, oval(cu + 0.005f, cv - 0.02f, 0.012f, 0.014f))
    region(session, torso, "test-socket-r", base, oval(cu + 0.005f, cv + 0.02f, 0.012f, 0.014f))
    region(session, torso, "test-nose", base, listOf((cu - 0.012f) to (cv - 0.006f), (cu - 0.012f) to (cv + 0.006f), (cu - 0.002f) to cv))
    for (t in -1..1) region(session, torso, "test-teeth-$t", base, rect(cu - 0.032f, cu - 0.022f, cv + t * 0.012f - 0.0015f, cv + t * 0.012f + 0.0015f))
}

private fun oval(cu: Float, cv: Float, ru: Float, rv: Float) =
    (0 until BORDER_POINTS / 2).map { k -> val a = 2.0 * PI * k / (BORDER_POINTS / 2); (cu + ru * sin(a).toFloat()) to (cv + rv * cos(a).toFloat()) }

/** A thin crescent over an eye at ([u], [v]): 0.004 of u at its thickest, tapering to its ends, 0.07 of v wide. */
private fun liner(u: Float, v: Float): List<Pair<Float, Float>> {
    val n = BORDER_POINTS / 2
    val upper = (0..n).map { k -> val s = -1f + 2f * k / n; (u + 0.03f * sqrt(1f - s * s) + 0.004f * (1f - s * s)) to (v + 0.035f * s) }
    val lower = (n - 1 downTo 1).map { k -> val s = -1f + 2f * k / n; (u + 0.03f * sqrt(1f - s * s)) to (v + 0.035f * s) }
    return upper + lower
}
