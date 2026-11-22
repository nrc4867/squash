package dev.chieppa.squash.recipe.figure

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.recipe.model.RecipeRest
import kotlin.math.abs

/** Body units between outline samples. */
const val FIGURE_SPACING = 0.002

/** Rounds of placing each limb's root on its drawn middle, each a split and a reading of its sides. */
private const val ROOT_ROUNDS = 5

/** A limb resting out from the body by at least this many degrees owns outline behind its root: a shoulder's cap. */
private const val SPREAD_LIMB = 20.0

/** How far behind its root, as a share of its first bone, a spread limb takes outline. */
private const val SPREAD_ROOT_REACH = 0.15

/** The stretch of a limb, as shares of its length, whose two sides place its root on its drawn middle. */
private const val MIDDLE_FROM = 0.2
private const val MIDDLE_TO = 0.85

/** A first guess at a limb root's reach from its parent's axis, as a share of the outline's half width there. */
private const val ROOT_GUESS_SHARE = 0.6

/** Rows either side of a limb root's height its first guess reads the outline over, in body units. */
private const val ROOT_GUESS_BAND = 0.01

/** A limb is read in the side view when at least this share of it lies beyond its parent's height there. */
private const val SIDE_SHOWN_SHARE = 0.5



/** One part the figure fits: its chain and the socket it sits on. A pair is described as its left side. */
data class FigurePartSpec(
    val part: String,
    /** The id its left side takes in poses. */
    val poseId: String,
    val kind: FigureKind,
    val length: Double,
    val closed: Boolean,
    val rest: RecipeRest,
    val outward: Double = 0.0,
    val lean: Double = 0.0,
    /** Its joints between its base and its tip, by name and u. */
    val joints: List<Pair<String, Double>> = emptyList(),
    val parent: String? = null,
    val socketU: Double = 0.0,
    val socketV: Double = 0.0,
    /** The reach of the socket it sits on, as given; fitted from the drawing when null. */
    val reach: Double? = null,
)

/** What the fit reads: the strokes of each view, the pose they are drawn in, where the ground lies, the parts, and corrections to add. */
class FigureInput(
    val strokes: Map<FigureView, List<FigureStroke>>,
    val pose: List<BonePose>,
    val rootHeight: Double,
    val parts: List<FigurePartSpec>,
    val spacing: Double = FIGURE_SPACING,
    /** Radius corrections by part and profile name, added to what the outline gives. */
    val offsets: Map<String, Map<String, Profile>> = emptyMap(),
) {
    /** This input with [offsets] in place of its own. */
    fun withOffsets(offsets: Map<String, Map<String, Profile>>) = FigureInput(strokes, pose, rootHeight, parts, spacing, offsets)
}

/** A part as fitted: its radius curves' places by profile name, the dense radii they follow, and the socket it sits on. */
class FittedPart(
    val spec: FigurePartSpec,
    val places: Map<String, List<P2>>,
    val radii: Map<String, Radii>,
    /** The reach and v of the socket it sits on, or null for a part on no socket. */
    val reach: Double?,
    val v: Double?,
)

/** The fit: each part fitted, the chains at rest and as drawn, and each view's samples by the part they went to. */
class FigureFit(
    val parts: Map<String, FittedPart>,
    val rest: Map<String, FigureChain>,
    val posed: Map<String, FigureChain>,
    val split: Map<FigureView, FigureSplit>,
    /** The parts each view reads. */
    val reads: Map<FigureView, Set<String>>,
)

/** Which profile a side of a part takes in a view. */
fun profileName(kind: FigureKind, view: FigureView, side: BoneSide): String = when {
    view == FigureView.SIDE -> if (side == BoneSide.PLUS) "front" else "back"
    kind == FigureKind.TRUNK -> "left"
    else -> if (side == BoneSide.PLUS) "outer" else "inner"
}

/**
 * Fits the parts to a figure outline. Each limb's root is first moved onto its drawn middle: the outline is split among the bones of
 * the pose it is drawn in, and the root moves across by however far the limb's two sides read off its bone together. Then each part's
 * radii are read across its own bones along its length, so a limb drawn hanging at any angle is fitted in its own frame, and what the
 * outline does not show is filled by rules read off the skeleton: a trunk's closed end under a part standing on it rounds over from where
 * that part's width begins, gaps are bridged, an open end the outline leaves (under a hand, a head) holds level, a limb's inner side
 * under its parent keeps the ratio its seen stretch has to its outer side, and a limb the side view cannot show is as deep as it is wide.
 */
fun fitFigure(input: FigureInput): FigureFit {
    val specs = input.parts.associateBy { it.part }
    val frames = input.parts.associate { it.part to PartFrame.of(it.rest, it.outward, it.lean) }
    val roots = HashMap<String, P3>()
    var rest = chains(input, specs, frames, roots)
    val front = input.strokes[FigureView.FRONT].orEmpty().flatMap { it.samples(input.spacing) }
    for (spec in input.parts) if (spec.kind == FigureKind.LIMB && spec.reach == null) roots[spec.part] = guessRoot(spec, rest, frames, front, input.rootHeight)
    lateinit var posed: Map<String, FigureChain>
    lateinit var split: Map<FigureView, FigureSplit>
    lateinit var reads: Map<FigureView, Set<String>>
    repeat(ROOT_ROUNDS + 1) { round ->
        rest = chains(input, specs, frames, roots)
        posed = rest.mapValues { (_, chain) -> chain.posedBy(input.pose) }
        reads = mapOf(FigureView.FRONT to posed.keys, FigureView.SIDE to posed.values.filter { shownFromSide(it, posed) }.map { it.part }.toSet())
        split = FigureView.entries.associateWith { view ->
            val bones = posed.values.filter { it.part in reads.getValue(view) }.flatMap { it.viewBones(view, input.rootHeight) }
            splitByBones(input.strokes[view].orEmpty().flatMap { it.samples(input.spacing) }, bones)
        }
        if (round == ROOT_ROUNDS) return@repeat
        for (spec in input.parts) {
            if (spec.kind != FigureKind.LIMB || spec.reach != null) continue
            var root = roots.getValue(spec.part)
            for (view in FigureView.entries) {
                if (spec.part !in reads.getValue(view)) continue
                root = movedRoot(root, spec, posed.getValue(spec.part), split.getValue(view), view)
            }
            roots[spec.part] = root
        }
    }
    val parts = LinkedHashMap<String, FittedPart>()
    for (spec in input.parts) {
        val sockets = socketOf(spec, rest, frames)
        val radii = profiles(spec, input, split, reads, specs)
        parts[spec.part] = FittedPart(spec, radii.mapValues { (_, r) -> fewestPlaces(r) }, radii, sockets?.first, sockets?.second)
    }
    return FigureFit(parts, rest, posed, split, reads)
}

/** The point on [parent]'s axis at [u], at rest. */
private fun axisPoint(parent: FigureChain, frame: PartFrame, u: Double): P3 = parent.root + frame.along * (u * parent.length)

/** Every part's chain at rest: a root at the origin, a part on a socket rooted where [roots] holds it or by its socket's reach. */
private fun chains(input: FigureInput, specs: Map<String, FigurePartSpec>, frames: Map<String, PartFrame>, roots: Map<String, P3>): Map<String, FigureChain> {
    val out = LinkedHashMap<String, FigureChain>()
    for (spec in input.parts) {
        val frame = frames.getValue(spec.part)
        val parentId = spec.parent
        val root = when {
            parentId == null -> P3.ZERO
            spec.part in roots -> roots.getValue(spec.part)
            else -> {
                val parent = out[parentId] ?: error("figure: ${spec.part} sits on $parentId, which the figure does not fit before it")
                axisPoint(parent, frames.getValue(parentId), spec.socketU) + frames.getValue(parentId).round(spec.socketV) * (spec.reach ?: 0.0)
            }
        }
        val joints = listOf("base" to 0.0) + spec.joints.sortedBy { it.second } + ("tip" to 1.0)
        out[spec.part] = FigureChain(
            spec.part, spec.poseId, spec.kind, spec.length,
            joints.map { (name, u) -> ChainJoint(name, u, root + frame.along * (u * spec.length)) },
            parentId?.takeIf { it in specs },
            rootReach = if (spec.kind == FigureKind.LIMB && spec.outward >= SPREAD_LIMB) SPREAD_ROOT_REACH else 0.0,
        )
    }
    return out
}

/** A first place for a limb's root: at its socket's height, a share of the way out to the front outline there. */
private fun guessRoot(spec: FigurePartSpec, rest: Map<String, FigureChain>, frames: Map<String, PartFrame>, front: List<StrokeSample>, rootHeight: Double): P3 {
    val parentId = requireNotNull(spec.parent)
    val axis = axisPoint(rest.getValue(parentId), frames.getValue(parentId), spec.socketU)
    val height = axis.y + rootHeight
    val outer = front.filter { abs(it.at.y - height) < ROOT_GUESS_BAND && it.normal.x > 0 }.maxOfOrNull { it.at.x } ?: 0.0
    return axis + frames.getValue(parentId).round(spec.socketV) * (outer * ROOT_GUESS_SHARE)
}

/** Whether the side view shows [chain]: a trunk always, a limb where most of it lies above or below its parent. */
private fun shownFromSide(chain: FigureChain, posed: Map<String, FigureChain>): Boolean {
    val parent = chain.parent?.let(posed::get) ?: return true
    if (chain.kind == FigureKind.TRUNK) return true
    val low = parent.joints.minOf { it.at.y }
    val high = parent.joints.maxOf { it.at.y }
    val steps = 20
    val outside = (0..steps).count { k -> chain.at(k.toDouble() / steps).y.let { it < low || it > high } }
    return outside.toDouble() / (steps + 1) >= SIDE_SHOWN_SHARE
}

/** [root] moved across in [view] by however far [spec]'s two sides there read off its bone together over its middle. */
private fun movedRoot(root: P3, spec: FigurePartSpec, chain: FigureChain, split: FigureSplit, view: FigureView): P3 {
    val mine = split.taken.filter { it.part == spec.part && it.x in spec.length * MIDDLE_FROM..spec.length * MIDDLE_TO }
    val step = spec.length / 10
    val offsets = (0 until 10).mapNotNull { k ->
        val from = k * step
        val bin = mine.filter { it.x >= from && it.x < from + step }
        val plus = bin.filter { it.side == BoneSide.PLUS }.map { it.radius }.takeIf { it.isNotEmpty() }?.average() ?: return@mapNotNull null
        val minus = bin.filter { it.side == BoneSide.MINUS }.map { it.radius }.takeIf { it.isNotEmpty() }?.average() ?: return@mapNotNull null
        (plus - minus) / 2
    }
    if (offsets.isEmpty()) return root
    val shift = offsets.average()
    val bone = chain.viewBones(view, 0.0).first()
    val d = bone.to - bone.from
    val across = (if (d.y <= 0) P2(-d.y, d.x) else P2(d.y, -d.x)).unit()
    if (abs(across.x) < 0.3) return root
    val dx = shift / across.x
    return if (view == FigureView.FRONT) root + P3(0.0, 0.0, -dx) else root + P3(dx, 0.0, 0.0)
}

/** The reach and v of the socket [spec] sits on that put its root where its rest chain starts; null for the root part. */
private fun socketOf(spec: FigurePartSpec, rest: Map<String, FigureChain>, frames: Map<String, PartFrame>): Pair<Double, Double>? {
    val parentId = spec.parent ?: return null
    val frame = frames.getValue(parentId)
    val axis = axisPoint(rest.getValue(parentId), frame, spec.socketU)
    val off = (rest.getValue(spec.part).root - axis).let { it - frame.along * (it dot frame.along) }
    if (off.length < 1e-9) return 0.0 to spec.socketV
    return off.length to frame.vToward(off)
}

