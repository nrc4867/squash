package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.Mat3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** How much of a key a pose takes on each side of the body: the [left] and [right] halves, each from the bones on its side and the middle. */
data class KeyWeight(val left: Float, val right: Float)

/** One bone a key is driven by: the part and joint it names. */
private data class Driver(val part: PartId, val joint: String)

/** The turn of a bone, named by part and joint, that its angles in a pose give it, or null where the rig has no such bone. */
typealias BoneTurn = (part: PartId, joint: String, bone: BonePose?) -> Mat3?

/** How many numbers a bone's place in pose space takes: its turn's nine entries, or its four angles where it has no turn. */
private const val COORDS = 9

/** Scales a turn's entries so that the distance between two turns reads about as the angle between them, in degrees. */
private val TURN_SCALE = 180.0 / PI / sqrt(2.0)

/**
 * A bone's place in pose space: by its turn ([turnOf]) where there is one, so two poses lie as far apart as the bone's turns, whichever
 * angles give them; else by its angles in degrees, its hinge turn, its lean across the hinge, its turn and its twist.
 */
private fun coordsOf(driver: Driver, bone: BonePose?, turnOf: BoneTurn?): DoubleArray {
    val turn = turnOf?.invoke(driver.part, driver.joint, bone)
    if (turn != null) return listOf(turn.x, turn.y, turn.z).flatMap { listOf(it.x, it.y, it.z) }.map { it * TURN_SCALE }.toDoubleArray()
    val angles = if (bone == null) DoubleArray(4) else doubleArrayOf(bone.degrees.toDouble(), bone.lean.toDouble(), bone.turn.toDouble(), bone.twist.toDouble())
    return angles + DoubleArray(COORDS - angles.size)
}

/**
 * How much of each of [keys] [pose] takes, per side, by pose space deformation's radial basis interpolation (Lewis, Cordner and Fong,
 * 2000): each side's keys are grouped by the bones that drive them, the bones the key's pose names on that side and on the body's
 * middle (a key naming bones on one side only drives the other side through each bone's twin); within a group, Gaussian radial basis
 * functions of the distance between the bones' places in pose space ([coordsOf]: their turns where [turnOf] gives them, else their
 * angles) are solved to give each key 1 at its own place and 0 at every other key's and at the rest, where every bone's angles are 0,
 * and read at [pose]'s. A weight below 0 is read as 0, and the group's weights, the rest's among them, are shared out to sum to 1, so a
 * pose between two keys of one joint, far from the rest, takes the two of them between it rather than fading toward the rest.
 */
fun keyWeights(keys: List<PoseKey>, pose: Pose, sideOf: (PartId) -> Side?, twinOf: (PartId) -> PartId?, turnOf: BoneTurn? = null): List<KeyWeight> {
    val bySide = Side.entries.associateWith { side ->
        val drivers = keys.map { key -> driversOn(key, side, sideOf, twinOf) }
        val weights = DoubleArray(keys.size)
        drivers.withIndex().filter { it.value.isNotEmpty() }.groupBy { (_, d) -> d.keys }.forEach { (bones, members) ->
            val order = bones.toList()
            fun place(of: (Driver) -> BonePose?) = flatten(order.map { coordsOf(it, of(it), turnOf) })
            val samples = listOf(place { null }) + members.map { (_, d) -> place { d.getValue(it) } }
            val read = interpolate(samples, place { pose.of(it.part, it.joint) }).map { it.coerceAtLeast(0.0) }
            val total = read.sum()
            members.forEachIndexed { m, (k, _) -> weights[k] = if (total > 1e-12) read[m + 1] / total else 0.0 }
        }
        weights
    }
    return keys.indices.map { k -> KeyWeight(bySide.getValue(Side.LEFT)[k].toFloat(), bySide.getValue(Side.RIGHT)[k].toFloat()) }
}

/** The bones driving [key] on [side], each as the key holds it. */
private fun driversOn(key: PoseKey, side: Side, sideOf: (PartId) -> Side?, twinOf: (PartId) -> PartId?): Map<Driver, BonePose> {
    val middle = key.pose.filter { sideOf(it.part) == null }
    val own = key.pose.filter { sideOf(it.part) == side }
    val other = key.pose.filter { sideOf(it.part).let { s -> s != null && s != side } }
    val sided = own.ifEmpty { other.mapNotNull { bone -> twinOf(bone.part)?.let { bone.copy(part = it) } } }
    return (middle + sided).associate { Driver(it.part, it.joint) to it }
}

private fun flatten(parts: List<DoubleArray>): DoubleArray = parts.fold(DoubleArray(0)) { acc, a -> acc + a }

private fun distance(a: DoubleArray, b: DoubleArray): Double = sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })

/**
 * The radial basis interpolants through [samples], one a sample, each 1 at its own sample and 0 at the others, read at [at]: Gaussians
 * as wide as the mean distance from each sample to its nearest other.
 */
internal fun interpolate(samples: List<DoubleArray>, at: DoubleArray): DoubleArray {
    val n = samples.size
    val nearest = samples.indices.map { i -> samples.indices.filter { it != i }.minOfOrNull { distance(samples[i], samples[it]) } ?: 1.0 }
    val width = nearest.average().takeIf { it > 1e-9 } ?: 1.0
    fun kernel(d: Double) = exp(-(d / width) * (d / width))
    val phi = Array(n) { i -> DoubleArray(n) { j -> kernel(distance(samples[i], samples[j])) } }
    val inverse = inverted(phi)
    val basis = DoubleArray(n) { j -> kernel(distance(at, samples[j])) }
    return DoubleArray(n) { k -> (0 until n).sumOf { j -> inverse[j][k] * basis[j] } }
}

/** The inverse of the square matrix [m], by Gauss-Jordan elimination with partial pivoting. */
private fun inverted(m: Array<DoubleArray>): Array<DoubleArray> {
    val n = m.size
    val a = Array(n) { i -> m[i].copyOf() }
    val inv = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }
    for (c in 0 until n) {
        val pivot = (c until n).maxBy { abs(a[it][c]) }
        a[c] = a[pivot].also { a[pivot] = a[c] }
        inv[c] = inv[pivot].also { inv[pivot] = inv[c] }
        val p = a[c][c]
        require(abs(p) > 1e-12) { "Two pose keys hold the same angles" }
        for (j in 0 until n) {
            a[c][j] /= p
            inv[c][j] /= p
        }
        for (r in 0 until n) if (r != c) {
            val f = a[r][c]
            if (f == 0.0) continue
            for (j in 0 until n) {
                a[r][j] -= f * a[c][j]
                inv[r][j] -= f * inv[c][j]
            }
        }
    }
    return inv
}
