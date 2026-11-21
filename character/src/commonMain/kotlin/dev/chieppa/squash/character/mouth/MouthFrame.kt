package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/** The step of u and v the mouth's frame is read across, either side of a place. */
private const val FRAME_STEP = 0.004f

/** How many steps a flat place is walked toward its place on the patch, and how near is near enough, in body units. */
private const val WALK_STEPS = 8
private const val WALK_NEAR = 1e-5f

/** The most one step of the walk moves in u or v. */
private const val WALK_STRIDE = 0.05f

private const val TINY = 1e-7f

/** How far either side of the middle the walk's starting places reach in u and v, and in how many steps each way. */
private const val SEED_U = 0.12f
private const val SEED_V = 0.5f
private const val SEED_STEPS = 8

/** How far off a closed end's pole, in u, a walk starts or steps where u and v run together there. */
private const val POLE_STEP = 0.005f

/** Half a turn round the patch in v: a walk past a closed end comes back on the far side of its pole. */
private const val HALF_TURN = 0.5f

/**
 * A flat frame laid on a part's patch at a middle place: x across the face and y down it, in body units, a place on the patch read as its
 * body point seen square to the surface there and unrolled to keep its distance from the middle, so a mouth's shapes are worked level whichever way the head's coordinates run, a muzzle's
 * tip where u and v circle a pole among them. Down is the body's down laid on the surface. A flat place is found on the patch again by
 * walking u and v toward it from the nearest of a few known places, back round the pole where the walk passes a closed end.
 */
class MouthFrame private constructor(
    private val holder: PartId,
    private val patch: String,
    private val surface: PartSurface,
    private val middle: SurfaceUv,
    private val origin: Vec3,
    private val across: Vec3,
    private val down: Vec3,
) {
    private fun at(u: Float, v: Float): Vec3? = surface.pointAt(Anchor(holder, patch, u, v - floor(v)))?.position

    /** The place at [u] and [v] unrolled onto the frame: its direction square to the surface's normal at the middle, its distance from the middle kept, so places round a tight curve are not squashed. */
    private fun flatAt(u: Float, v: Float): Vec2? = at(u, v)?.let { point ->
        val d = point - origin
        val x = d dot across
        val y = d dot down
        val square = hypot(x, y)
        if (square < TINY) Vec2(0f, 0f) else (d.length / square).let { Vec2(x * it, y * it) }
    }

    fun flat(anchor: Anchor): Vec2 = flatAt(anchor.u, middle.v + unwrapped(anchor.v - middle.v)) ?: Vec2(0f, 0f)

    /** Places of known u and v about the middle and their flat places, which a walk starts from the nearest of. */
    private val seeds: List<Pair<Vec2, SurfaceUv>> by lazy {
        (-SEED_STEPS..SEED_STEPS).flatMap { i ->
            (-SEED_STEPS..SEED_STEPS).mapNotNull { j ->
                val u = (middle.u + i * SEED_U / SEED_STEPS).coerceIn(POLE_STEP, 1f - POLE_STEP)
                val v = middle.v + j * SEED_V / SEED_STEPS
                flatAt(u, v)?.let { it to SurfaceUv(u, v) }
            }
        }
    }

    fun onPatch(p: Vec2): SurfaceUv {
        val seed = seeds.minByOrNull { (flat, _) -> hypot(flat.x - p.x, flat.y - p.y) }?.second ?: middle
        var u = seed.u
        var v = seed.v
        repeat(WALK_STEPS) {
            val here = flatAt(u, v) ?: return SurfaceUv(u, v - floor(v))
            val miss = here - p
            if (hypot(miss.x, miss.y) < WALK_NEAR) return SurfaceUv(u, v - floor(v))
            val du = flatAt(u + FRAME_STEP, v)?.let { (it - here) * (1f / FRAME_STEP) } ?: return SurfaceUv(u, v - floor(v))
            val dv = flatAt(u, v + FRAME_STEP)?.let { (it - here) * (1f / FRAME_STEP) } ?: return SurfaceUv(u, v - floor(v))
            val det = du.x * dv.y - dv.x * du.y
            if (abs(det) < TINY) {
                u = u.coerceIn(POLE_STEP, 1f - POLE_STEP)
                return@repeat
            }
            val stepU = (miss.x * dv.y - dv.x * miss.y) / det
            val stepV = (du.x * miss.y - miss.x * du.y) / det
            u -= stepU.coerceIn(-WALK_STRIDE, WALK_STRIDE)
            v -= stepV.coerceIn(-WALK_STRIDE, WALK_STRIDE)
            if (u > 1f) {
                u = 2f - u
                v += HALF_TURN
            } else if (u < 0f) {
                u = -u
                v += HALF_TURN
            }
        }
        return SurfaceUv(u, v - floor(v))
    }

    companion object {
        private val BODY_DOWN = Vec3(0f, -1f, 0f)

        private fun unwrapped(dv: Float) = dv - floor(dv + 0.5f)

        /** The frame on [patch] of [holder]'s [surface] at the middle of [places], or null where the surface there is degenerate. */
        fun of(holder: PartId, patch: String, places: List<Anchor>, surface: PartSurface): MouthFrame? {
            if (places.isEmpty()) return null
            val first = places.first().v
            val u = places.map { it.u }.average().toFloat()
            val v = places.map { first + unwrapped(it.v - first) }.average().toFloat().let { it - floor(it) }
            fun at(du: Float, dv: Float) = surface.pointAt(Anchor(holder, patch, u + du, v + dv))?.position
            val origin = at(0f, 0f) ?: return null
            val toU = (at(FRAME_STEP, 0f) ?: return null) - (at(-FRAME_STEP, 0f) ?: return null)
            val toV = (at(0f, FRAME_STEP) ?: return null) - (at(0f, -FRAME_STEP) ?: return null)
            val normal = (toU cross toV).takeIf { it.length > TINY }?.normalized() ?: return null
            val down = (BODY_DOWN - normal * (BODY_DOWN dot normal)).takeIf { it.length > TINY }?.normalized() ?: return null
            val across = (normal cross down).normalized()
            return MouthFrame(holder, patch, surface, SurfaceUv(u, v), origin, across, down)
        }
    }
}
