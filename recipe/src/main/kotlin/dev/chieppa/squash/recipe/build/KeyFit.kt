package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.keys.KeyedFields
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.figure.FieldSilhouette
import dev.chieppa.squash.recipe.figure.FigureFit
import dev.chieppa.squash.recipe.figure.FigureInput
import dev.chieppa.squash.recipe.figure.FigureKind
import dev.chieppa.squash.recipe.figure.FigureMeasure
import dev.chieppa.squash.recipe.figure.FigurePartSpec
import dev.chieppa.squash.recipe.figure.FigureView
import dev.chieppa.squash.recipe.figure.P2
import dev.chieppa.squash.recipe.figure.PartSample
import dev.chieppa.squash.recipe.figure.Silhouette
import dev.chieppa.squash.recipe.figure.Profile
import dev.chieppa.squash.recipe.figure.corrected
import dev.chieppa.squash.recipe.figure.fitFigure
import dev.chieppa.squash.recipe.figure.measure
import dev.chieppa.squash.recipe.figure.project
import dev.chieppa.squash.recipe.figure.readAt
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipeKey
import dev.chieppa.squash.recipe.model.RecipeKeyChange
import dev.chieppa.squash.recipe.model.RecipeKeyOutline
import dev.chieppa.squash.recipe.model.RecipeFigure
import dev.chieppa.squash.recipe.model.RecipePart
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.measureTime

/** Width rounds a key's outline takes when it names none. */
const val DEFAULT_KEY_ROUNDS = 4

/** The share of each miss a round takes off, as a figure's rounds do. */
private const val GAIN = 0.6

/** The shares of a join's blend a key's outline is read among. */
private val KEY_BLEND_SHARES = listOf(0.6, 1.0, 1.5, 2.2, 3.2)

/** Samples within this many root radii of a part's root, as drawn, show its join. */
private const val JOIN_REACH = 2.5

/** Passes of smoothing a key's width changes take along each part, so a change fades out where the outline stops. */
private const val SMOOTHING = 3

/** The least and most a fitted key scales a width by. */
private const val LEAST_SCALE = 0.5
private const val MOST_SCALE = 2.0

/** How far past the posed chains' joints a traced outline looks, in body units: a limb's own width. */
private const val TRACE_HEADROOM = 0.08

/** Places a fitted scale is written to. */
private const val PLACES = 10_000.0

/**
 * How a key's outline was fitted: the changes it gives, keyed by recipe part, the fit of its strokes in its pose, how the keyed body
 * lies off them, the mean miss before any change and after each round, and what it cost.
 */
class KeyReport(
    val name: String,
    val changes: List<RecipeKeyChange>,
    val fit: FigureFit,
    val measure: FigureMeasure,
    val means: List<Double>,
    val time: Duration,
    /** The body as the key's pose shows it with no change, traced into a figure outline to redraw, when the key was fitted posed. */
    val posed: RecipeFigure? = null,
)

/** Whether [part] is one a key's outline can change: a tube of the body. */
private fun isKeyPart(part: RecipePart): Boolean = part.tube != null && part.garment == null

/** The parts of [recipe] a key's outline is read on, each on its socket where the recipe (or its figure's fit) put it. */
internal fun keySpecs(recipe: Recipe): List<FigurePartSpec> {
    val taken = HashSet<String>()
    val out = mutableListOf<FigurePartSpec>()
    for (part in recipe.parts) {
        if (!isKeyPart(part)) continue
        val parent = part.on
        if (parent != null && parent !in taken) continue
        val tube = requireNotNull(part.tube)
        val rest = part.rest ?: continue
        val socket = parent?.let { on ->
            val n = part.socket?.removePrefix("socket.")?.toIntOrNull()
            recipe.parts.first { it.id == on }.sockets.firstOrNull { it.n == n }
        }
        if (parent != null && socket == null) continue
        taken += part.id
        out += FigurePartSpec(
            part = part.id,
            poseId = part.pair?.let { it.left ?: "${part.id}-l" } ?: part.id,
            kind = if (part.pair != null) FigureKind.LIMB else FigureKind.TRUNK,
            length = tube.length.toDouble(),
            closed = tube.closed,
            rest = rest,
            outward = (part.outward ?: 0f).toDouble(),
            lean = (part.lean ?: 0f).toDouble(),
            joints = part.joints.map { "joint.${it.n}" to it.u.toDouble() },
            parent = parent,
            socketU = (socket?.u ?: 0f).toDouble(),
            socketV = (socket?.v ?: 0f).toDouble(),
            reach = socket?.let { (it.reach ?: 0f).toDouble() },
        )
    }
    return out
}

/**
 * The changes [key]'s [outline] asks of [recipe], as the figure's fit and widths stand: its strokes are split among the bones of the
 * key's pose and read in each limb's own bones, then the body of [recipe]'s tubes is built with the changes so far read over its parts
 * and its silhouette measured against them: posed in the key's pose by [bodies], or without them at rest, each limb's samples carried
 * back by as much of its bone's turn as the rig's seam band gives it. First each join the strokes reach takes the share of its
 * blend nearest them; then, for the outline's rounds, each part's widths take off a share of the miss along it, and the round nearest
 * the strokes is kept. A width changes only along the stretch the strokes reach, eased out past it.
 */
fun fittedKey(
    recipe: Recipe,
    key: RecipeKey,
    outline: RecipeKeyOutline,
    path: String,
    templates: TemplateFolder,
    library: TemplateLibrary = templates.library(),
    bodies: KeyBodies? = null,
): KeyReport {
    lateinit var report: KeyReport
    val time = measureTime {
        val rootHeight = (outline.rootHeight ?: recipe.figure?.rootHeight ?: throw RecipeException("$path.rootHeight", "a key's outline names its root height when the recipe has no figure")).toDouble()
        val specs = keySpecs(recipe)
        if (specs.isEmpty()) throw RecipeException(path, "a key's outline changes tube parts of the body, and this recipe has none")
        val strokes = mapOf(
            FigureView.FRONT to outline.front.mapIndexed { i, s -> figureStroke(s, "$path.front[$i]") },
            FigureView.SIDE to outline.side.mapIndexed { i, s -> figureStroke(s, "$path.side[$i]") },
        )
        val fit = fitFigure(FigureInput(strokes, key.pose, rootHeight, specs))
        val parts = specs.associateBy { it.part }
        val body = bodyDocument(recipe, parts.keys, templates)
        val fields = KeyedFields(body.character, library)
        val pose = Pose(key.pose.map { bone -> bone.copy(part = poseId(recipe, bone.part)) })
        fun measured(changes: List<RecipeKeyChange>): FigureMeasure {
            val made = changes.flatMap { documentChanges(recipe, it, path) }
            if (bodies != null) return measure(fit, bodies.silhouette(body, pose, made, rootHeight), rootHeight) { 0.0 }
            val field = requireNotNull(fields.of(made)) { "key: the body built no field" }
            return measure(fit, FieldSilhouette(field.field, rootHeight), rootHeight) { sample -> seamShare(recipe, parts.getValue(sample.part), sample) }
        }
        val blends = if (outline.joins) joinShares(recipe, fit, specs, rootHeight, ::measured) else emptyMap()
        var offsets: Map<String, Map<String, Profile>> = emptyMap()
        var best: Pair<List<RecipeKeyChange>, FigureMeasure>? = null
        val means = mutableListOf(measured(emptyList()).mean)
        repeat(outline.rounds ?: DEFAULT_KEY_ROUNDS) {
            val changes = keyChanges(recipe, offsets, blends)
            val measure = measured(changes)
            means += measure.mean
            if (best == null || measure.mean < best.second.mean) best = changes to measure
            offsets = corrected(offsets, fit, measure, GAIN)
        }
        val (changes, measure) = requireNotNull(best)
        val traced = bodies?.let { posedOutline(it.silhouette(body, pose, emptyList(), rootHeight), fit, rootHeight) }
        report = KeyReport(key.name, changes, fit, measure, means, Duration.ZERO, traced)
    }
    return KeyReport(report.name, report.changes, report.fit, report.measure, report.means, time, report.posed)
}

/** The document part a pose's bone on recipe part [name] turns: a side by its own id, a pair by its left side, or the part. */
private fun poseId(recipe: Recipe, name: PartId): PartId {
    val part = recipePartNamed(recipe, name.value) ?: return name
    return if (part.id == name.value) documentIds(part).first() else name
}

/** [silhouette] traced into a figure outline over the reach of [fit]'s posed chains, in both views. */
private fun posedOutline(silhouette: Silhouette, fit: FigureFit, rootHeight: Double): RecipeFigure {
    val joints = fit.posed.values.flatMap { chain -> chain.joints.map { it.at } }
    val height = joints.maxOf { it.y } + rootHeight + TRACE_HEADROOM
    fun reach(view: FigureView) = joints.maxOf { abs(project(view, it, rootHeight).x) } + TRACE_HEADROOM
    return RecipeFigure(
        rootHeight.toFloat(),
        front = tracedStrokes(FigureView.FRONT, silhouette, reach(FigureView.FRONT), height),
        side = tracedStrokes(FigureView.SIDE, silhouette, reach(FigureView.SIDE), height),
        pose = emptyList(),
    )
}

/** [change] as the document's changes, its part's ids named as the build names them. */
private fun documentChanges(recipe: Recipe, change: RecipeKeyChange, path: String): List<PartChange> {
    val part = recipePartNamed(recipe, change.part) ?: return emptyList()
    return partChanges(recipe, change, documentIds(part), path)
}

/**
 * The share of its blend each join the outline reaches takes: the body is measured with every such join's blend scaled by each of a few
 * shares at once, and each keeps the share whose silhouette lies nearest the strokes round its root.
 */
private fun joinShares(recipe: Recipe, fit: FigureFit, specs: List<FigurePartSpec>, rootHeight: Double, measured: (List<RecipeKeyChange>) -> FigureMeasure): Map<String, Float> {
    val samples = fit.split.flatMap { (view, split) -> split.taken.map { view to it } }
    val joining = specs.filter { spec ->
        val radius = rootRadius(recipe, spec.part) ?: return@filter false
        if (spec.parent == null || recipe.parts.first { it.id == spec.part }.blend == null) return@filter false
        val root = fit.posed.getValue(spec.part).root
        samples.any { (view, sample) -> sample.part == spec.part && (sample.sample.at - project(view, root, rootHeight)).length <= JOIN_REACH * radius }
    }
    if (joining.isEmpty()) return emptyMap()
    val scores = joining.associate { it.part to DoubleArray(KEY_BLEND_SHARES.size) }
    KEY_BLEND_SHARES.forEachIndexed { k, share ->
        val measure = measured(joining.map { RecipeKeyChange(it.part, blend = share.toFloat()) })
        for (spec in joining) {
            val root = fit.posed.getValue(spec.part).root
            val reach = JOIN_REACH * requireNotNull(rootRadius(recipe, spec.part))
            val near = measure.samples.filter { m -> (m.sample.sample.at - project(m.view, root, rootHeight)).length <= reach }
            scores.getValue(spec.part)[k] = near.map { abs(it.miss) }.average().takeIf { !it.isNaN() } ?: 0.0
        }
    }
    return joining.associate { spec -> spec.part to KEY_BLEND_SHARES[scores.getValue(spec.part).withIndex().minBy { it.value }.index].toFloat() }
}

/**
 * How much of its bone's turn a sample of [spec] is carried back by, as the rig's seam band blends a limb's first bone with its
 * parent's: half at the limb's root, all of it a root radius out along the limb, none a root radius behind it.
 */
private fun seamShare(recipe: Recipe, spec: FigurePartSpec, sample: PartSample): Double {
    if (spec.kind != FigureKind.LIMB) return 1.0
    val radius = rootRadius(recipe, spec.part)?.takeIf { it > 0 } ?: return 1.0
    return (0.5 + 0.5 * sample.x / radius).coerceIn(0.0, 1.0)
}

/** A tube part's radius at its root, the mean of its profiles' first places. */
private fun rootRadius(recipe: Recipe, part: String): Double? =
    profilesOf(recipe, part).values.mapNotNull { it.firstOrNull()?.y }.takeIf { it.isNotEmpty() }?.average()

/** A tube part's radius curves by profile name, each as the places it runs through. */
private fun profilesOf(recipe: Recipe, part: String): Map<String, List<P2>> =
    recipe.parts.firstOrNull { it.id == part }?.tube?.profiles.orEmpty().mapValues { (_, outline) ->
        val through = outline.through ?: outline.bezier?.filterIndexed { i, _ -> i % 3 == 0 }.orEmpty()
        through.map { P2(it[0].toDouble(), it[1].toDouble()) }
    }

/**
 * [offsets], radius changes by part and profile, as width scales: each change eased along its part and read over the radius the part's
 * own profile gives there, and [blends] as the blends' scales.
 */
private fun keyChanges(recipe: Recipe, offsets: Map<String, Map<String, Profile>>, blends: Map<String, Float>): List<RecipeKeyChange> =
    (offsets.keys + blends.keys).map { part ->
        val radii = profilesOf(recipe, part)
        val widths = offsets[part].orEmpty().mapNotNull { (name, change) ->
            val drawn = radii[name]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val eased = smoothed(change.radii)
            name to change.at.indices.map { i ->
                val r = readAt(drawn, change.at[i])
                val scale = if (r > 1e-4) ((r + eased[i]) / r).coerceIn(LEAST_SCALE, MOST_SCALE) else 1.0
                listOf(rounded(change.at[i]), rounded(scale))
            }
        }.toMap()
        RecipeKeyChange(part, widths, blends[part])
    }

private fun smoothed(values: List<Double>): List<Double> {
    var out = values
    repeat(SMOOTHING) {
        val v = out
        out = v.indices.map { i -> (v[maxOf(0, i - 1)] + 2 * v[i] + v[minOf(v.lastIndex, i + 1)]) / 4 }
    }
    return out
}

private fun rounded(value: Double): Float = (Math.round(value * PLACES) / PLACES).toFloat()
