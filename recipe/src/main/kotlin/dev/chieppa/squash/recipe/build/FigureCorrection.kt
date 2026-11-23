package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.surface.RunSolids
import dev.chieppa.squash.character.surface.fieldOf
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.recipe.figure.FieldSilhouette
import dev.chieppa.squash.recipe.figure.FigureFit
import dev.chieppa.squash.recipe.figure.FigureMeasure
import dev.chieppa.squash.recipe.figure.FittedPart
import dev.chieppa.squash.recipe.figure.project
import kotlin.math.abs
import dev.chieppa.squash.recipe.figure.Profile
import dev.chieppa.squash.recipe.figure.corrected
import dev.chieppa.squash.recipe.figure.fitFigure
import dev.chieppa.squash.recipe.figure.measure
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipeFigure
import kotlin.time.Duration
import kotlin.time.measureTime

/** Correction rounds a figure takes when it names none. */
const val DEFAULT_FIGURE_ROUNDS = 3

/** The share of each miss a round takes off: under 1, since neighbouring parts' corrections meet in their joins. */
private const val GAIN = 0.6

/**
 * How a figure was fitted: the recipe as built, the fit it was shaped by, how the body built from it lies off the outline, and what the
 * plain fit and each correction round cost.
 */
class FigureReport(
    val recipe: Recipe,
    val fit: FigureFit,
    val measure: FigureMeasure,
    val fitTime: Duration,
    val roundTimes: List<Duration>,
    val means: List<Double>,
    /** The blend each fitted part joins its parent by, as read off the drawn fillet, by part id. */
    val blends: Map<String, Float> = emptyMap(),
    /** What each fit after the first cost, warm: the fit alone, with each round's corrections. */
    val refitTimes: List<Duration> = emptyList(),
)

/**
 * [recipe] shaped by its [figure], corrected on the body's own silhouette: the parts the figure fits are built alone, their joined field
 * is read straight along each view's depth at every outline sample (at rest, a limb's samples carried there from the pose the outline is
 * drawn in). After the plain fit, each join's blend is read off the drawn fillet: the body is built at a few shares of each joining
 * part's default blend, and each join keeps the one whose silhouette lies nearest the outline round that join. Then each part's widths
 * take off a share of how far the body stands out past the outline, for the figure's rounds, and the round nearest the outline is kept.
 */
fun fittedFigure(recipe: Recipe, figure: RecipeFigure, templates: TemplateFolder, library: TemplateLibrary = templates.library()): FigureReport {
    val input = figureInput(recipe, figure)
    val rounds = figure.rounds ?: DEFAULT_FIGURE_ROUNDS
    var offsets: Map<String, Map<String, Profile>> = emptyMap()
    var best: FigureReport? = null
    val times = mutableListOf<Duration>()
    val refits = mutableListOf<Duration>()
    val means = mutableListOf<Double>()
    var blends: Map<String, Float> = emptyMap()
    lateinit var fit: FigureFit
    val fitTime = measureTime { fit = fitFigure(input) }
    val rootHeight = figure.rootHeight.toDouble()
    fun measured(shaped: Recipe): FigureMeasure = measure(fit, FieldSilhouette(bodyField(shaped, fit.parts.keys, templates, library), rootHeight), rootHeight)
    for (round in 0..rounds) {
        if (round > 0) refits += measureTime { fit = fitFigure(input.withOffsets(offsets)) }
        if (rounds == 0) return FigureReport(withFit(recipe, fit), fit, FigureMeasure(emptyList(), emptyList()), fitTime, times, means)
        lateinit var measure: FigureMeasure
        times += measureTime {
            if (round == 0) blends = joinBlends(recipe, fit, rootHeight) { shaped -> measured(shaped) }
            measure = measured(withFit(recipe, fit, blends))
        }
        means += measure.mean
        if (best == null || measure.mean < best.measure.mean) best = FigureReport(withFit(recipe, fit, blends), fit, measure, fitTime, times, means, blends, refits)
        if (round < rounds) offsets = corrected(offsets, fit, measure, GAIN)
    }
    return requireNotNull(best).let { FigureReport(it.recipe, it.fit, it.measure, fitTime, times, means, blends, refits) }
}

/** The shares of a joining part's default blend a join's fillet is read among. */
private val BLEND_SHARES = listOf(0.35, 0.6, 1.0, 1.5, 2.2)

/** A part rounds into its parent by this many times its root radius when it gives no blend, as the field's default does. */
private const val ROOT_BLEND = 1.4

/** Samples within this many root radii of a part's root, in the view it is drawn in, show its join. */
private const val JOIN_REACH = 2.5

/**
 * The blend each fitted part that gives none joins its parent by, read off the drawn fillet: the body as [fit] shapes it is [measured]
 * at each of a few shares of the part's default blend, all joins at once, and each join keeps the share whose silhouette lies nearest
 * the outline among the samples round its root.
 */
private fun joinBlends(recipe: Recipe, fit: FigureFit, rootHeight: Double, measured: (Recipe) -> FigureMeasure): Map<String, Float> {
    val joining = fit.parts.values.filter { part -> part.spec.parent != null && recipe.parts.first { it.id == part.spec.part }.blend == null }
    if (joining.isEmpty()) return emptyMap()
    val defaults = joining.associate { part -> part.spec.part to ROOT_BLEND * rootRadius(part) }
    val scores = joining.associate { it.spec.part to DoubleArray(BLEND_SHARES.size) }
    BLEND_SHARES.forEachIndexed { k, share ->
        val measure = measured(withFit(recipe, fit, defaults.mapValues { (share * it.value).toFloat() }))
        for (part in joining) {
            val root = fit.posed.getValue(part.spec.part).root
            val reach = JOIN_REACH * rootRadius(part)
            val near = measure.samples.filter { m -> (m.sample.sample.at - project(m.view, root, rootHeight)).length <= reach }
            scores.getValue(part.spec.part)[k] = near.map { abs(it.miss) }.average().takeIf { !it.isNaN() } ?: 0.0
        }
    }
    return joining.associate { part ->
        val best = scores.getValue(part.spec.part).withIndex().minBy { it.value }.index
        part.spec.part to (BLEND_SHARES[best] * defaults.getValue(part.spec.part)).toFloat()
    }
}

/** A fitted part's radius at its root, the mean of its profiles there. */
private fun rootRadius(part: FittedPart): Double = part.radii.values.mapNotNull { it.firstOrNull()?.y }.average()

/** The joined field of the parts [shaped] fits to its figure, built alone: no other part, surface work, garment or pose. */
private fun bodyField(shaped: Recipe, ids: Set<String>, templates: TemplateFolder, library: TemplateLibrary) = run {
    val character = bodyDocument(shaped, ids, templates).character
    val assembled = assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, library.plan(character.bodyPlan), library)
    requireNotNull(fieldOf(assembled, RunSolids())) { "figure: the fitted parts built no field" }.field
}

/** The character of [shaped]'s parts [ids] built alone: no other part, surface work, garment, key or pose. */
internal fun bodyDocument(shaped: Recipe, ids: Set<String>, templates: TemplateFolder): SquashDocument {
    val body = shaped.copy(
        figure = null,
        parts = shaped.parts.filter { it.id in ids }.map { it.copy(regions = emptyList(), marks = emptyList(), groups = emptyList(), fur = null, lineWeights = emptyList(), areas = emptyMap()) },
        eyes = emptyList(), membranes = emptyList(), hair = emptyList(), expressions = emptyList(), wardrobe = null,
        displayPose = emptyList(), poses = emptyMap(), keys = emptyList(), targets = emptyList(), creases = null, shading = null, viewKeys = null,
    )
    return RecipeBuilder(templates).build(body).document
}
