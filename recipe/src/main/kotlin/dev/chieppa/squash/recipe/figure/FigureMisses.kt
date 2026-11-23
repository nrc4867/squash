package dev.chieppa.squash.recipe.figure

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** How far either way along a sample's normal the built edge is looked for, in body units. */
private const val EDGE_REACH = 0.03

/** A miss larger than this share of the outline's height, the ground to its top (the chin), is marked: about an ink line's width. */
const val MISS_TOLERANCE = 0.004

/** Every this many samples one is measured: neighbours a sample's spacing apart miss alike. */
private const val STRIDE = 2

/** Bins a part's misses are averaged over, in body units along it. */
private const val BIN = 0.008

/** How a built body lies off one sample of its outline: where it is drawn, the part and profile it shapes, and by how much. */
class SampleMiss(val view: FigureView, val sample: PartSample, val profile: String, val miss: Double)

/** A stretch of one part's outline the built body misses by more than [MISS_TOLERANCE] of the figure's height: where it is drawn and its worst miss. */
class FigureMiss(val view: FigureView, val part: String, val profile: String, val stroke: String, val from: P2, val to: P2, val worst: Double)

/** Every sample's miss, the stretches past the tolerance, and the mean and largest miss over the samples read. */
class FigureMeasure(val samples: List<SampleMiss>, val misses: List<FigureMiss>) {
    val mean: Double = samples.map { abs(it.miss) }.average().takeIf { !it.isNaN() } ?: 0.0
    val largest: Double = samples.maxOfOrNull { abs(it.miss) } ?: 0.0
}

/**
 * [sample], drawn on its posed bone, carried onto the same bone at rest: its place and normal moved and turned with the bone, by [share]
 * of the bone's move and turn.
 */
private fun atRest(sample: PartSample, rest: FigureChain, rootHeight: Double, view: FigureView, share: Double): Pair<P2, P2> {
    val posed = sample.bone
    val bone = rest.viewBones(view, rootHeight).firstOrNull { it.xFrom == posed.xFrom } ?: return sample.sample.at to sample.sample.normal
    val a = posed.to - posed.from
    val b = bone.to - bone.from
    val angle = (atan2(b.y, b.x) - atan2(a.y, a.x)) * share
    fun turn(p: P2) = P2(p.x * cos(angle) - p.y * sin(angle), p.x * sin(angle) + p.y * cos(angle))
    val from = posed.from + (bone.from - posed.from) * share
    return from + turn(sample.sample.at - posed.from) to turn(sample.sample.normal)
}

/**
 * How far the body [silhouette] shows lies off each sample [fit] read, measured at rest: a limb's samples are carried from the pose the
 * outline is drawn in onto its bones at rest first, each by [share] of its bone's turn. Misses past the tolerance, a share of the
 * figure's height, are gathered into stretches along each stroke.
 */
fun measure(fit: FigureFit, silhouette: Silhouette, rootHeight: Double, share: (PartSample) -> Double = { 1.0 }): FigureMeasure {
    val read = mutableListOf<SampleMiss>()
    val misses = mutableListOf<FigureMiss>()
    val tolerance = MISS_TOLERANCE * fit.split.values.flatMap { split -> split.taken.map { it.sample.at.y } }.fold(0.0, ::maxOf)
    for (view in FigureView.entries) {
        var run: FigureMiss? = null
        fun close() {
            run?.let(misses::add)
            run = null
        }
        for ((i, s) in fit.split.getValue(view).taken.withIndex()) {
            if (i % STRIDE != 0) continue
            val part = fit.parts[s.part] ?: continue
            val profile = profileName(part.spec.kind, view, s.side)
            val (at, normal) = atRest(s, fit.rest.getValue(s.part), rootHeight, view, share(s))
            val miss = silhouette.edge(view, at, normal, EDGE_REACH)
            if (miss == null) {
                close()
                continue
            }
            read += SampleMiss(view, s, profile, miss)
            val open = run
            if (abs(miss) <= tolerance || (open != null && (open.part != s.part || open.stroke != s.sample.stroke))) close()
            if (abs(miss) > tolerance) {
                val current = run
                run = if (current == null) {
                    FigureMiss(view, s.part, profile, s.sample.stroke, s.sample.at, s.sample.at, miss)
                } else {
                    FigureMiss(view, s.part, profile, s.sample.stroke, current.from, s.sample.at, if (abs(miss) > abs(current.worst)) miss else current.worst)
                }
            }
        }
        close()
    }
    return FigureMeasure(read, misses)
}

/**
 * The corrections [previous] moved by [gain] of [measure]'s misses: each part's profile, averaged in bins along the part and evened
 * between neighbouring bins, takes off its share of how far the body stands out past the outline there.
 */
fun corrected(previous: Map<String, Map<String, Profile>>, fit: FigureFit, measure: FigureMeasure, gain: Double): Map<String, Map<String, Profile>> {
    val out = previous.mapValues { it.value.toMutableMap() }.toMutableMap()
    for ((key, group) in measure.samples.groupBy { it.sample.part to it.profile }) {
        val (part, profile) = key
        val length = fit.parts.getValue(part).spec.length
        val bins = maxOf(2, Math.ceil(length / BIN).toInt())
        val step = length / bins
        val sums = DoubleArray(bins + 1)
        val counts = IntArray(bins + 1)
        for (m in group) {
            val k = Math.round(m.sample.x / step).toInt().coerceIn(0, bins)
            sums[k] += m.miss
            counts[k]++
        }
        val raw = DoubleArray(bins + 1) { if (counts[it] > 0) sums[it] / counts[it] else 0.0 }
        val even = DoubleArray(bins + 1) { k -> (raw[maxOf(0, k - 1)] + raw[k] + raw[minOf(bins, k + 1)]) / 3 }
        val old = out[part]?.get(profile)
        val at = (0..bins).map { it * step }
        val radii = at.mapIndexed { k, x -> (old?.get(x) ?: 0.0) - gain * even[k] }
        out.getOrPut(part) { mutableMapOf() }[profile] = Profile(at, radii)
    }
    return out
}
