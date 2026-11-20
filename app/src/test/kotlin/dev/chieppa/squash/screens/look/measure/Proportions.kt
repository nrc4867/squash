package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.SurfaceEyes
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.look.LookParts
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Every proportion the build reports and a recipe may set a target for. */
val PROPORTION_NAMES = listOf("handLength", "handWidth", "footLength", "footWidth", "headHeight", "headWidth", "eyeLine", "eyeWidth", "eyeSpacing")

/** A power iteration is run this many times for each axis of a point cloud. */
private const val POWER_STEPS = 64

/**
 * One measured proportion: a size's [value] in body units, or, for a place (`eyeLine`), its height above the ground. [ofHead] is a size's
 * share of the head's height, or a place's depth down the head from its top; [ofHeight] the share of the figure's height, ground to the
 * top of the head.
 */
data class Proportion(val name: String, val value: Float, val ofHead: Float, val ofHeight: Float, val isPlace: Boolean = false)

/** What was measured, and what could not be and why. */
data class ProportionReport(val measured: List<Proportion>, val unmeasured: Map<String, String>)

private fun PlacedPart.points(): List<Vec3> = List(surface.mesh.vertexCount) { origin + surface.mesh.position(it) }

/** The principal frame of [points]: three orthogonal unit vectors, the longest spread first. */
internal fun principalAxes(points: List<Vec3>): List<Vec3> {
    val mean = points.fold(Vec3(0f, 0f, 0f)) { a, p -> a + p } * (1f / points.size)
    val c = Array(3) { DoubleArray(3) }
    for (p in points) {
        val d = doubleArrayOf((p.x - mean.x).toDouble(), (p.y - mean.y).toDouble(), (p.z - mean.z).toDouble())
        for (i in 0..2) for (j in 0..2) c[i][j] += d[i] * d[j]
    }
    fun apply(v: DoubleArray) = DoubleArray(3) { i -> c[i][0] * v[0] + c[i][1] * v[1] + c[i][2] * v[2] }
    fun unit(v: DoubleArray): DoubleArray {
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (n == 0.0) v else DoubleArray(3) { v[it] / n }
    }
    fun top(start: DoubleArray, against: DoubleArray?): DoubleArray {
        var v = unit(start)
        repeat(POWER_STEPS) {
            var w = apply(v)
            if (against != null) {
                val k = w[0] * against[0] + w[1] * against[1] + w[2] * against[2]
                w = DoubleArray(3) { w[it] - k * against[it] }
            }
            v = unit(w)
        }
        return v
    }
    val first = top(doubleArrayOf(1.0, 0.3, 0.2), null)
    val second = top(doubleArrayOf(0.2, 1.0, 0.4), first)
    val third = unit(doubleArrayOf(first[1] * second[2] - first[2] * second[1], first[2] * second[0] - first[0] * second[2], first[0] * second[1] - first[1] * second[0]))
    return listOf(first, second, third).map { Vec3(it[0].toFloat(), it[1].toFloat(), it[2].toFloat()) }
}

private fun extentAlong(points: List<Vec3>, axis: Vec3): Float {
    var low = Float.MAX_VALUE
    var high = -Float.MAX_VALUE
    for (p in points) {
        val t = p dot axis
        if (t < low) low = t
        if (t > high) high = t
    }
    return high - low
}

/** A hand's length along its longest axis and its width, the larger of its other two extents. */
internal fun handExtent(points: List<Vec3>): Pair<Float, Float> {
    val axes = principalAxes(points)
    return extentAlong(points, axes[0]) to maxOf(extentAlong(points, axes[1]), extentAlong(points, axes[2]))
}

/**
 * [points] of a hand from its socket on, along its principal axis: the wrist the arm runs into reaches back inside the arm, and a hand
 * is read from the wrist to the fingertip.
 */
internal fun pastSocket(points: List<Vec3>, socket: Vec3): List<Vec3> {
    val axis = principalAxes(points)[0]
    val middle = points.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / points.size)
    val out = if (((middle - socket) dot axis) >= 0f) axis else axis * -1f
    return points.filter { ((it - socket) dot out) >= 0f }.ifEmpty { points }
}

/** A foot's length and width on the ground: its footprint's extents along its principal axis and across it. */
internal fun footExtent(points: List<Vec3>): Pair<Float, Float> {
    val mx = points.sumOf { it.x.toDouble() } / points.size
    val mz = points.sumOf { it.z.toDouble() } / points.size
    var sxx = 0.0
    var szz = 0.0
    var sxz = 0.0
    for (p in points) {
        val dx = p.x - mx
        val dz = p.z - mz
        sxx += dx * dx
        szz += dz * dz
        sxz += dx * dz
    }
    val angle = 0.5 * atan2(2 * sxz, sxx - szz)
    val along = Vec3(cos(angle).toFloat(), 0f, sin(angle).toFloat())
    val across = Vec3(-sin(angle).toFloat(), 0f, cos(angle).toFloat())
    return extentAlong(points, along) to extentAlong(points, across)
}

/**
 * The proportions of [body] as built, in body units, each as a share of the head and of the figure: the head's box, the first hand and
 * foot by their use, and the first painted eye on [surface] (its look's units made body units by the surface under it). Heights run from
 * the lowest vertex to the top of the head, which is the figure's height.
 */
fun bodyProportions(body: AssembledCharacter, character: Character, surface: CharacterSurface?): ProportionReport {
    val uses = LookParts(character)
    val unmeasured = linkedMapOf<String, String>()
    val head = uses.head?.let(body::part)?.takeIf { it.box != null }
    if (head == null) {
        for (name in PROPORTION_NAMES) unmeasured[name] = "the character has no head part"
        return ProportionReport(emptyList(), unmeasured)
    }
    val headBox = checkNotNull(head.box)
    val ground = body.parts.filter { it.id != null }.minOf { part -> part.points().minOf { it.y } }
    val top = headBox.high.y
    val height = top - ground
    val headHeight = headBox.high.y - headBox.low.y
    val measured = mutableListOf<Proportion>()
    fun size(name: String, value: Float) = measured.add(Proportion(name, value, value / headHeight, value / height))
    size("headHeight", headHeight)
    size("headWidth", headBox.high.z - headBox.low.z)
    val hand = uses.hand?.let(body::part)
    if (hand != null) {
        val (length, width) = handExtent(pastSocket(hand.points(), hand.origin))
        size("handLength", length)
        size("handWidth", width)
    } else {
        unmeasured["handLength"] = "no hand part"
        unmeasured["handWidth"] = "no hand part"
    }
    val foot = uses.foot?.let(body::part)
    if (foot != null) {
        val (length, width) = footExtent(foot.points())
        size("footLength", length)
        size("footWidth", width)
    } else {
        unmeasured["footLength"] = "no foot part"
        unmeasured["footWidth"] = "no foot part"
    }
    val eye = character.paintedEyes.firstOrNull { it.anchor.part == uses.head }
    val frames = if (eye != null && surface != null) SurfaceEyes.framesOf(surface, eye) else emptyList()
    if (eye == null || frames.isEmpty()) {
        val why = if (eye == null) "no painted eye on the head" else "no one surface to read the eye's units from"
        for (name in listOf("eyeLine", "eyeWidth", "eyeSpacing")) unmeasured[name] = why
    } else {
        size("eyeWidth", 2f * eye.look.width * frames.map { it.perU.length }.average().toFloat())
        val middleY = frames.map { it.middle.y }.average().toFloat()
        measured.add(Proportion("eyeLine", middleY - ground, (top - middleY) / headHeight, (middleY - ground) / height, isPlace = true))
        if (frames.size == 2) size("eyeSpacing", (frames[0].middle - frames[1].middle).length) else unmeasured["eyeSpacing"] = "the eye is not mirrored: one eye has no spacing"
    }
    return ProportionReport(measured, unmeasured)
}

/** The lines `measure.txt` prints for [report], each headed [name], then each target's check. */
fun proportionLines(name: String, report: ProportionReport, checks: List<ProportionTargetCheck>): List<String> {
    val lines = mutableListOf<String>()
    for (p in report.measured) {
        lines += if (p.isPlace) {
            "$name proportions: ${p.name} ${"%.3f".format(p.ofHead)} down the head, ${"%.3f".format(p.ofHeight)} of the height up, at ${"%.4f".format(p.value)} above the ground"
        } else {
            "$name proportions: ${p.name} ${"%.4f".format(p.value)} body units, ${"%.3f".format(p.ofHead)} of the head, ${"%.3f".format(p.ofHeight)} of the height"
        }
    }
    for ((n, why) in report.unmeasured) lines += "$name proportions: $n not measured: $why"
    lines += checks.map { "$name ${it.line}" }
    return lines
}
