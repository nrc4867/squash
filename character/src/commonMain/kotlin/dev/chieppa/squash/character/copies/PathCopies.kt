package dev.chieppa.squash.character.copies

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.CopySpec
import dev.chieppa.squash.character.model.SurfacePlace
import dev.chieppa.squash.geom.Vec3
import kotlin.math.cosh
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Copies repeated along a path on a part: the smooth curve through the path's places, read on the part's surface, drooping by the
 * spec's sag as a chain hangs from its two ends and lying on the part wherever the droop would take it inside; then copies at even
 * steps along it, each standing on the surface with its axis along the normal and its up along the path, every one rolled about the path
 * by the spec's roll more than the one before. A closed ring's steps are its inside length, so each link passes through the next.
 */
internal object PathCopies {
    /** Points read along the path between two of its places. */
    private const val SAMPLES_PER_SPAN = 48

    /** How sharply the droop bends near the ends, the catenary's span over its parameter. */
    private const val CATENARY = 1.6f

    /** How far, as multiples of the droop, a point that has sunk inside the part is pushed out along the normal looking for its outside. */
    private const val PUSH_REACH = 3f
    private const val PUSH_STEPS = 12

    private class Sample(val point: Vec3, val normal: Vec3)

    fun place(rule: CopyRule, parent: PlacedPart): List<CopyPlace> {
        val spec = rule.spec
        val line = line(spec, parent, rule.flipV)
        if (line.size < 2) return emptyList()
        val lengths = FloatArray(line.size)
        for (i in 1 until line.size) lengths[i] = lengths[i - 1] + (line[i].point - line[i - 1].point).length
        val length = lengths.last()
        val linkPitch = linkPitch(rule) * spec.size
        val pitch = when {
            spec.pitch > 0f -> spec.pitch
            spec.count > 1 -> length / (spec.count - 1)
            else -> linkPitch
        }
        if (pitch <= 0f) return emptyList()
        val fits = floor(length / pitch + 1e-4f).toInt() + 1
        val count = (if (spec.count > 0) minOf(spec.count, fits) else fits).coerceIn(1, CopySpec.MAX_COPIES)
        val start = (length - pitch * (count - 1)) / 2f
        return List(count) { i ->
            val at = start + pitch * i
            val k = lengths.indexOfFirst { it >= at }.coerceIn(1, line.lastIndex)
            val share = ((at - lengths[k - 1]) / (lengths[k] - lengths[k - 1]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
            val point = line[k - 1].point * (1f - share) + line[k].point * share
            val tangent = (line[k].point - line[k - 1].point).normalized()
            val normal = squareTo(tangent, line[k - 1].normal * (1f - share) + line[k].normal * share)
            val along = normal.turnedAbout(tangent, spec.roll * i * DEGREES_TO_RADIANS)
            val turn = (frameOf(along, tangent, rule.mirrored) * rule.definition.transposed).scaled(spec.size)
            CopyPlace(point + normal * (below(rule, turn, normal) + spec.outset), turn, null)
        }
    }

    /** How far [rule]'s mesh, taken through [turn], reaches below its origin along [normal]: what it is stood up by to rest on the surface. */
    private fun below(rule: CopyRule, turn: dev.chieppa.squash.geom.Mat3, normal: Vec3): Float {
        val mesh = rule.surface.mesh
        var most = 0f
        for (v in 0 until mesh.vertexCount) most = max(most, -((turn * mesh.position(v)) dot normal))
        return most
    }

    /**
     * The step between copies of [rule]'s part at its own size: its length along its up, the path's way, end to end; for a closed ring
     * that length less twice the ring's thickness, its inside length, so the next ring hooks through it.
     */
    private fun linkPitch(rule: CopyRule): Float {
        val mesh = rule.surface.mesh
        if (mesh.vertexCount == 0) return 0f
        val up = rule.definition.y
        val along = rule.definition.z
        var lowUp = Float.POSITIVE_INFINITY
        var highUp = Float.NEGATIVE_INFINITY
        var lowAlong = Float.POSITIVE_INFINITY
        var highAlong = Float.NEGATIVE_INFINITY
        for (v in 0 until mesh.vertexCount) {
            val p = mesh.position(v)
            val a = p dot up
            val b = p dot along
            if (a < lowUp) lowUp = a
            if (a > highUp) highUp = a
            if (b < lowAlong) lowAlong = b
            if (b > highAlong) highAlong = b
        }
        val length = highUp - lowUp
        return if (rule.looped) (length - 2f * (highAlong - lowAlong)).coerceAtLeast(length * MIN_LINK_SHARE) else length
    }

    private const val MIN_LINK_SHARE = 0.2f

    /** The path's points on [parent] with the surface's normal at each, drooped by the spec's sag. */
    private fun line(spec: CopySpec, parent: PlacedPart, flipV: Boolean): List<Sample> {
        val places = unwrapped(spec.path)
        if (places.size < 2) return emptyList()
        val read = ArrayList<Sample>()
        for (span in 0 until places.size - 1) {
            val a = places[maxOf(span - 1, 0)]
            val b = places[span]
            val c = places[span + 1]
            val d = places[minOf(span + 2, places.lastIndex)]
            val last = if (span == places.size - 2) SAMPLES_PER_SPAN else SAMPLES_PER_SPAN - 1
            for (s in 0..last) {
                val t = s.toFloat() / SAMPLES_PER_SPAN
                val u = catmullRom(a.u, b.u, c.u, d.u, t).coerceIn(0f, 1f)
                val v = wrapped(catmullRom(a.v, b.v, c.v, d.v, t).let { if (flipV) 1f - it else it })
                val point = parent.pointOn(u, v, spec.patch) ?: continue
                val normal = parent.normalAt(u, v, spec.patch)?.normalized() ?: continue
                read += Sample(point, normal)
            }
        }
        if (spec.sag <= 0f || read.size < 2) return read
        val lengths = FloatArray(read.size)
        for (i in 1 until read.size) lengths[i] = lengths[i - 1] + (read[i].point - read[i - 1].point).length
        val total = lengths.last().coerceAtLeast(1e-6f)
        val depth = cosh(CATENARY) - 1f
        val inside = parent.inside
        return read.mapIndexed { i, sample ->
            val x = 2f * lengths[i] / total - 1f
            val droop = spec.sag * (cosh(CATENARY) - cosh(CATENARY * x)) / depth
            val hung = sample.point - Vec3.Y * droop
            if (inside == null || hung !in inside) Sample(hung, sample.normal) else Sample(pushedOut(hung, sample.normal, droop, inside), sample.normal)
        }
    }

    /** [point], inside [inside], moved along [normal] to the nearest place found outside it, within a few times [reach]. */
    private fun pushedOut(point: Vec3, normal: Vec3, reach: Float, inside: dev.chieppa.squash.geom.ShellInside): Vec3 {
        var low = 0f
        var high = reach * PUSH_REACH
        if (point + normal * high in inside) return point + normal * high
        repeat(PUSH_STEPS) {
            val mid = (low + high) / 2f
            if (point + normal * mid in inside) low = mid else high = mid
        }
        return point + normal * high
    }

    /** [places] with each v moved by whole turns to lie nearest the one before it, so a path round a part takes the short way. */
    private fun unwrapped(places: List<SurfacePlace>): List<SurfacePlace> {
        if (places.isEmpty()) return places
        val out = ArrayList<SurfacePlace>(places.size)
        out += places.first()
        for (place in places.drop(1)) {
            val before = out.last().v
            out += SurfacePlace(place.u, place.v + (before - place.v).roundToInt())
        }
        return out
    }

    private fun catmullRom(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (2f * b + (c - a) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (3f * b - a - 3f * c + d) * t3)
    }
}
