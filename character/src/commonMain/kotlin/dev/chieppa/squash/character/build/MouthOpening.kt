package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** The rank an open mouth's fills paint at: over every region and stroke of its part. */
internal const val RANK_MOUTH = 4

/** How deep a fully open mouth drops its lower lip below the lip line at its middle, a share of the lip line's width, when no inner shape is drawn. */
private const val OPEN_DEPTH = 0.45f

/** Places along each lip of a mouth opened from its lip line alone. */
private const val LIP_SAMPLES = 11

/** The step of u and v the mouth's frame is read across, either side of its middle. */
private const val FRAME_STEP = 0.004f

private const val TINY = 1e-7f

/** What an open mouth adds to its part: fills in layer order and ink lines. */
data class OpenMouths(val shapes: List<LandedShape>, val lines: List<ResolvedMark>) {
    companion object {
        val NONE = OpenMouths(emptyList(), emptyList())
    }
}

/**
 * Every mouth [holder] holds that shows open, its lips parted on the surface: a lower lip drops from its lip line toward the face's
 * underside by the open, from nothing at the corners, and the opening between is filled in the inner role's slot; or, when the inner
 * role names a mark, that mark's shape is the fully open mouth, closed toward the lip line by the open. The tongue's marks ride the
 * lower lip and the teeth's stay where drawn, both kept between the lips and filled in their role's slot, inked where the role is. The
 * lower lip is inked as the lip line is. A mouth at open 0 adds nothing. See HLD: look, Mouth.
 */
fun openMouths(holder: PartId, work: ResolvedSurfaceWork, surfaces: Map<PartId, PartSurface>): OpenMouths {
    val surface = surfaces[holder] ?: return OpenMouths.NONE
    val opened = work.groups.filter { it.isMouth && it.controls.open > 0f }.mapNotNull { MouthOpening.of(holder, it, work, surface) }
    if (opened.isEmpty()) return OpenMouths.NONE
    return OpenMouths(opened.flatMap { it.shapes() }, opened.flatMap { it.lines() })
}

/** One open mouth, worked in a flat frame at its lip line's middle: x across the face, y down it, both in body units. */
private class MouthOpening(
    private val holder: PartId,
    private val group: MarkGroup,
    private val work: ResolvedSurfaceWork,
    private val surface: PartSurface,
    private val patch: String,
    private val frame: MouthFrame,
    private val lip: List<Pair<Float, Float>>,
) {
    private val open = group.controls.open.coerceIn(0f, 1f)
    private val role = group.roles.associateBy { it.role }
    private val left = lip.first().first
    private val right = lip.last().first

    /** The lip line's y at [x], straight between its places and held level past its ends. */
    fun lipAt(x: Float): Float {
        if (x <= left) return lip.first().second
        if (x >= right) return lip.last().second
        val i = lip.indexOfLast { it.first <= x }.coerceAtMost(lip.size - 2)
        val (x0, y0) = lip[i]
        val (x1, y1) = lip[i + 1]
        return if (x1 - x0 < TINY) y0 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    }

    /** [p] with its drop below the lip line scaled by the open, as the lower jaw carries it. */
    private fun carried(p: Pair<Float, Float>): Pair<Float, Float> {
        val lipY = lipAt(p.first)
        return p.first to (lipY + maxOf(0f, p.second - lipY) * open)
    }

    /** The opening's outline: the drawn inner shape carried by the open, or the lip line and a lower lip dropped below it. */
    val opening: List<Pair<Float, Float>> by lazy {
        val drawn = marksOf(MarkRole.INNER).firstOrNull { it.size >= MIN_SHAPE_POINTS }
        if (drawn != null) return@lazy drawn.map(::carried)
        val depth = OPEN_DEPTH * (right - left) * open
        val xs = List(LIP_SAMPLES) { left + (right - left) * it / (LIP_SAMPLES - 1) }
        val upper = xs.map { it to lipAt(it) }
        val lower = xs.mapIndexed { i, x -> x to (lipAt(x) + depth * sin(PI.toFloat() * i / (LIP_SAMPLES - 1))) }
        upper + lower.reversed().drop(1).dropLast(1)
    }

    /** Whether the opening was dropped from the lip line, so its two ends are the mouth's corners. */
    private val fromLip: Boolean get() = marksOf(MarkRole.INNER).none { it.size >= MIN_SHAPE_POINTS }

    /** The opening's top and bottom at [x]: where a line down the face there crosses its outline, or null outside it. */
    private fun spanAt(x: Float): Pair<Float, Float>? {
        val ys = opening.indices.mapNotNull { i ->
            val (x0, y0) = opening[i]
            val (x1, y1) = opening[(i + 1) % opening.size]
            if ((x < minOf(x0, x1)) || (x > maxOf(x0, x1)) || abs(x1 - x0) < TINY) null else y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        }
        return if (ys.isEmpty()) null else ys.min() to ys.max()
    }

    /** [p] kept within the opening: across its reach, then between its top and bottom there. */
    private fun kept(p: Pair<Float, Float>): Pair<Float, Float> {
        val low = opening.minOf { it.first }
        val high = opening.maxOf { it.first }
        val x = p.first.coerceIn(low, high)
        val (top, bottom) = spanAt(x) ?: return x to lipAt(x)
        return x to p.second.coerceIn(top, bottom)
    }

    private fun marksOf(role: MarkRole): List<List<Pair<Float, Float>>> {
        val ids = this.role[role]?.marks.orEmpty().toSet()
        return work.marks.filter { it.id in ids }.map { mark -> mark.points.filter { it.anchor.part == holder && it.anchor.patch == patch }.map { frame.flat(it.anchor) } }
    }

    private fun fill(role: GroupRole?, points: List<Pair<Float, Float>>, corners: Set<Int>): LandedShape? {
        val slot = role?.fill ?: return null
        if (points.size < MIN_SHAPE_POINTS) return null
        return LandedShape(RANK_MOUTH, slot, holder, patch, ColorShape.Border(points.map(frame::onPatch), corners = corners, curved = true))
    }

    private val tongue: List<List<Pair<Float, Float>>> get() = marksOf(MarkRole.TONGUE).map { mark -> mark.map { kept(carried(it)) } }

    private val teeth: List<List<Pair<Float, Float>>> get() = marksOf(MarkRole.TEETH).map { mark -> mark.map(::kept) }

    fun shapes(): List<LandedShape> {
        val corners = if (fromLip) setOf(0, LIP_SAMPLES - 1) else emptySet()
        return listOfNotNull(fill(role[MarkRole.INNER], opening, corners)) +
            tongue.mapNotNull { fill(role[MarkRole.TONGUE], it, emptySet()) } +
            teeth.mapNotNull { fill(role[MarkRole.TEETH], it, it.indices.toSet()) }
    }

    fun lines(): List<ResolvedMark> {
        val lipMark = work.marks.firstOrNull { it.id in role[MarkRole.LIP_LINE]?.marks.orEmpty() }
        val low = opening.minOf { it.first }
        val high = opening.maxOf { it.first }
        val lower = List(LIP_SAMPLES) { low + (high - low) * it / (LIP_SAMPLES - 1) }.mapNotNull { x -> spanAt(x)?.let { x to it.second } }
        val lowerLip = line("${group.id.value}-lower-lip", lower, lipMark)
        fun inked(role: MarkRole, shapes: List<List<Pair<Float, Float>>>) =
            if (this.role[role]?.ink == true) shapes.mapIndexedNotNull { i, s -> line("${group.id.value}-${role.name.lowercase()}-$i", s + s.take(1), lipMark) } else emptyList()
        return listOfNotNull(lowerLip) + inked(MarkRole.TONGUE, tongue) + inked(MarkRole.TEETH, teeth)
    }

    private fun line(id: String, places: List<Pair<Float, Float>>, like: ResolvedMark?): ResolvedMark? {
        val points = places.mapNotNull { surface.pointAt(Anchor(holder, patch, frame.onPatch(it).u, frame.onPatch(it).v)) }
        if (points.size < MIN_LINE_POINTS) return null
        return ResolvedMark(MarkId(id), points, like?.width ?: MarkWidth(), ink = true, cameraLocked = false, versions = emptyMap(), slot = like?.slot)
    }

    companion object {
        private const val MIN_SHAPE_POINTS = 3
        private const val MIN_LINE_POINTS = 2

        fun of(holder: PartId, group: MarkGroup, work: ResolvedSurfaceWork, surface: PartSurface): MouthOpening? {
            val lipIds = group.roles.filter { it.role == MarkRole.LIP_LINE }.flatMap { it.marks }.toSet()
            val lipPoints = work.marks.filter { it.id in lipIds }.flatMap { it.points }.filter { it.anchor.part == holder }
            val patch = lipPoints.firstOrNull()?.anchor?.patch ?: return null
            val own = lipPoints.filter { it.anchor.patch == patch }
            val frame = MouthFrame.of(holder, patch, own.map { it.anchor }, surface) ?: return null
            val lip = own.map { frame.flat(it.anchor) }.sortedBy { it.first }
            if (lip.size < MIN_LINE_POINTS || lip.last().first - lip.first().first < TINY) return null
            return MouthOpening(holder, group, work, surface, patch, frame, lip)
        }
    }
}

/**
 * A flat frame on a part's patch about a middle place: x across the face and y down it, in body units, from the surface's own run of
 * u and v there, so a mouth's shapes are worked level whichever way the head's coordinates run. Down is the body's down laid on the
 * surface; v is read unwrapped about the middle's.
 */
private class MouthFrame(
    private val holder: PartId,
    private val patch: String,
    private val middle: SurfaceUv,
    private val toU: Vec3,
    private val toV: Vec3,
    private val across: Vec3,
    private val down: Vec3,
) {
    private val uu = toU dot toU
    private val uv = toU dot toV
    private val vv = toV dot toV
    private val det = uu * vv - uv * uv

    fun flat(anchor: Anchor): Pair<Float, Float> {
        val dv = anchor.v - middle.v - floor(anchor.v - middle.v + 0.5f)
        val step = toU * (anchor.u - middle.u) + toV * dv
        return (step dot across) to (step dot down)
    }

    fun onPatch(p: Pair<Float, Float>): SurfaceUv {
        val w = across * p.first + down * p.second
        val a = w dot toU
        val b = w dot toV
        val du = (a * vv - b * uv) / det
        val dv = (b * uu - a * uv) / det
        val v = middle.v + dv
        return SurfaceUv(middle.u + du, v - floor(v))
    }

    companion object {
        private val BODY_DOWN = Vec3(0f, -1f, 0f)

        fun of(holder: PartId, patch: String, places: List<Anchor>, surface: PartSurface): MouthFrame? {
            if (places.isEmpty()) return null
            val first = places.first().v
            val u = places.map { it.u }.average().toFloat()
            val v = places.map { first + (it.v - first - floor(it.v - first + 0.5f)) }.average().toFloat().let { it - floor(it) }
            fun at(du: Float, dv: Float) = surface.pointAt(Anchor(holder, patch, u + du, v + dv))?.position
            val toU = (at(FRAME_STEP, 0f) ?: return null) - (at(-FRAME_STEP, 0f) ?: return null)
            val toV = (at(0f, FRAME_STEP) ?: return null) - (at(0f, -FRAME_STEP) ?: return null)
            val scaledU = toU * (1f / (2f * FRAME_STEP))
            val scaledV = toV * (1f / (2f * FRAME_STEP))
            val normal = (scaledU cross scaledV).takeIf { it.length > TINY }?.normalized() ?: return null
            val down = (BODY_DOWN - normal * (BODY_DOWN dot normal)).takeIf { it.length > TINY }?.normalized() ?: return null
            val across = (normal cross down).normalized()
            if (abs(scaledU.dot(scaledU) * scaledV.dot(scaledV) - scaledU.dot(scaledV).let { it * it }) < TINY * TINY) return null
            return MouthFrame(holder, patch, SurfaceUv(u, v), scaledU, scaledV, across, down)
        }
    }
}
