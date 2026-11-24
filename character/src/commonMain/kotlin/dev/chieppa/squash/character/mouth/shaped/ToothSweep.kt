package dev.chieppa.squash.character.mouth.shaped

import dev.chieppa.squash.document.ToothTip
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Rings along a tooth's shown length, and round each ring. */
private const val TOOTH_RINGS = 7
private const val TOOTH_SEGMENTS = 12

/** How deep a tooth is front to back, a share of its width, by tip: a cone round, a stub a little flat, a wedge and a chisel flat. */
private const val POINT_DEPTH = 0.9f
private const val ROUND_DEPTH = 0.75f
private const val FLAT_DEPTH = 0.55f
private const val CHISEL_DEPTH = 0.45f

/** How thin a wedge's or chisel's edge gets at its tip, a share of its depth at the root. */
private const val EDGE_DEPTH = 0.18f

/** How far a cone's sides bow out half way along, a share of its width. */
private const val POINT_BOW = 0.12f

/** How far a chisel's edge slants back across it, a share of its width. */
private const val CHISEL_SLANT = 0.35f

/** The ring past a wedge's or chisel's last that closes its edge, flat front to back. */
private const val EDGE_CLOSE = 1.0001f

/** How much narrower a wedge's edge is than its root, a share. */
private const val FLAT_NARROW = 0.1f

/**
 * Where a tooth stands: its root's middle at [root], reaching [length] along the unit [along] past it and sunk [sink] behind it, [width]
 * wide along the unit [across] and its depth's half-axis along the unit [depth], its tip hooked [hook] along [hookWay] by the square of the
 * share of its length, and, for a chisel, its long side toward [inner] along across (1 or -1).
 */
internal class ToothPlace(
    val root: Vec3,
    val along: Vec3,
    val across: Vec3,
    val depth: Vec3,
    val width: Float,
    val length: Float,
    val sink: Float,
    val hook: Float,
    val hookWay: Vec3,
    val inner: Float,
)

/**
 * The rings of a tooth with tip [tip] at [place], from its sunk root to its tip: a cone for a point, closing to a point; a rounded stub
 * for round, closing round; a wedge for flat, thinning front to back to a straight edge; a chisel, a wedge whose edge slants back from
 * its inner side. [map] carries each point where the tooth is built, a jaw's turn for the lower.
 */
internal fun toothRings(tip: ToothTip, place: ToothPlace, map: (Vec3) -> Vec3 = { it }): Pair<List<List<Vec3>>, FloatArray> {
    val half = place.width / 2f
    val depthShare = when (tip) {
        ToothTip.POINT -> POINT_DEPTH
        ToothTip.ROUND -> ROUND_DEPTH
        ToothTip.FLAT -> FLAT_DEPTH
        ToothTip.CHISEL -> CHISEL_DEPTH
    }
    val halfDepth = half * depthShare
    val across = acrossOf(place.depth, place.along)
    val flip = if ((across dot place.across) < 0f) -1f else 1f
    val edged = tip == ToothTip.FLAT || tip == ToothTip.CHISEL
    val ts = listOf(-place.sink / place.length) + List(TOOTH_RINGS) { i -> i.toFloat() / (TOOTH_RINGS - 1) } + listOfNotNull(EDGE_CLOSE.takeIf { edged })
    val rings = ts.map { at ->
        val closing = at > 1f
        val t = at.coerceAtMost(1f)
        val shown = t.coerceIn(0f, 1f)
        val centre = place.root + place.along * (t * place.length) + place.hookWay * (place.hook * shown * shown)
        ring(TOOTH_SEGMENTS, centre, across * half, place.depth * halfDepth, place.along) { a ->
            val x = sin(a) * flip * place.inner
            when (tip) {
                ToothTip.POINT -> {
                    val w = (1f - shown) * (1f + POINT_BOW * 4f * sin(PI.toFloat() * shown) * shown)
                    Triple(w, w, 0f)
                }
                ToothTip.ROUND -> {
                    val cap = (half / place.length).coerceIn(0.05f, 1f)
                    val w = if (shown < 1f - cap) 1f else sqrt((1f - ((shown - (1f - cap)) / cap).let { it * it }).coerceAtLeast(0f))
                    Triple(w, w, 0f)
                }
                ToothTip.FLAT -> Triple(1f - FLAT_NARROW * shown, if (closing) 0f else 1f - (1f - EDGE_DEPTH) * easeOut(shown), 0f)
                ToothTip.CHISEL -> {
                    val back = if (shown > 0f) -CHISEL_SLANT * place.width * shown * shown * (1f - x) / 2f else 0f
                    Triple(1f, if (closing) 0f else 1f - (1f - EDGE_DEPTH) * easeOut(shown), back)
                }
            }
        }.map(map)
    }
    val us = FloatArray(rings.size) { i -> i.toFloat() / (rings.size - 1) }
    return rings to us
}
