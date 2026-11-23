package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.figure.FigureFit
import dev.chieppa.squash.recipe.figure.FigureInput
import dev.chieppa.squash.recipe.figure.FigureKind
import dev.chieppa.squash.recipe.figure.FigurePartSpec
import dev.chieppa.squash.recipe.figure.FigureStroke
import dev.chieppa.squash.recipe.figure.FigureView
import dev.chieppa.squash.recipe.figure.P2
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipeFigure
import dev.chieppa.squash.recipe.model.RecipeFigureStroke
import dev.chieppa.squash.recipe.model.RecipeOutline
import dev.chieppa.squash.recipe.model.RecipePart

/** Rounds a fitted value to this many places, as a recipe writes it. */
private const val PLACES = 10_000f

/** Whether [part] is one a figure fits: a tube of the body that gives no profiles. */
fun isFigurePart(part: RecipePart): Boolean = part.tube != null && part.tube.profiles.isEmpty() && part.garment == null

/** The parts of [recipe] its figure fits, as the fit reads them, each after the part it sits on. */
internal fun figureSpecs(recipe: Recipe): List<FigurePartSpec> {
    val fitted = recipe.parts.withIndex().filter { isFigurePart(it.value) }
    val ids = fitted.map { it.value.id }.toSet()
    return fitted.map { (i, part) ->
        val path = "$.parts[$i]"
        val tube = requireNotNull(part.tube)
        val rest = part.rest ?: throw RecipeException("$path.rest", "a part the figure fits names the way it rests")
        val parent = part.on
        if (parent != null && parent !in ids) throw RecipeException("$path.on", "a part the figure fits sits on another it fits, or on none; \"$parent\" gives its own profiles")
        val socket = parent?.let { on ->
            val name = part.socket ?: throw RecipeException("$path.socket", "name the socket on $on this part sits on")
            val n = name.removePrefix("socket.").toIntOrNull()
            recipe.parts.first { it.id == on }.sockets.firstOrNull { it.n == n }
                ?: throw RecipeException("$path.socket", "a part the figure fits sits on a hand-made socket of the part under it, not \"$name\"")
        }
        FigurePartSpec(
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
            reach = socket?.reach?.toDouble(),
        )
    }
}

/** [figure]'s strokes by view, each as the fit reads it. */
internal fun figureStrokes(figure: RecipeFigure): Map<FigureView, List<FigureStroke>> = mapOf(
    FigureView.FRONT to figure.front.mapIndexed { i, s -> figureStroke(s, "$.figure.front[$i]") },
    FigureView.SIDE to figure.side.mapIndexed { i, s -> figureStroke(s, "$.figure.side[$i]") },
    FigureView.TOP to figure.top.mapIndexed { i, s -> figureStroke(s, "$.figure.top[$i]") },
)

/** [stroke] as the fit reads it, failing at [path] where it is not one. */
fun figureStroke(stroke: RecipeFigureStroke, path: String = "$.figure"): FigureStroke {
    val through = stroke.through
    val bezier = stroke.bezier
    if ((through == null) == (bezier == null)) throw RecipeException(path, "a stroke gives either through or bezier, not ${if (through == null) "neither" else "both"}")
    fun place(field: String, p: List<Float>): P2 {
        if (p.size != 2) throw RecipeException("$path.$field", "a place is [x, y], not $p")
        return P2(p[0].toDouble(), p[1].toDouble())
    }
    if (bezier != null) {
        val points = bezier.mapIndexed { i, p -> place("bezier[$i]", p) }
        if (points.size < 4 || (points.size - 1) % 3 != 0) throw RecipeException("$path.bezier", "a Bézier stroke holds 3n + 1 points, not ${points.size}")
        return FigureStroke(stroke.id, points)
    }
    val places = requireNotNull(through).mapIndexed { i, p -> place("through[$i]", p) }
    if (places.size < 2) throw RecipeException("$path.through", "a stroke passes through at least 2 places, not ${places.size}")
    stroke.corners.forEachIndexed { i, c -> if (c !in places.indices) throw RecipeException("$path.corners[$i]", "corner $c is not an index into through (0 to ${places.lastIndex})") }
    return FigureStroke.through(stroke.id, places, stroke.corners.toSet(), stroke.mirrored)
}

/** The fit's input for [recipe]: its figure's strokes, the pose they are drawn in, and the parts it fits. */
internal fun figureInput(recipe: Recipe, figure: RecipeFigure): FigureInput = FigureInput(
    strokes = figureStrokes(figure),
    pose = figure.pose ?: recipe.displayPose,
    rootHeight = figure.rootHeight.toDouble(),
    parts = figureSpecs(recipe),
)

/**
 * [recipe] with what [fit] read written in: each part it fits takes its radius curves as its tube's profiles and, where it gives none,
 * its join's blend from [blends], and each socket a fitted part sits on that gives no reach takes the reach and v the fit put its root at.
 */
internal fun withFit(recipe: Recipe, fit: FigureFit, blends: Map<String, Float> = emptyMap()): Recipe {
    val placed = fit.parts.values.filter { it.spec.parent != null && it.spec.reach == null }
    return recipe.copy(
        parts = recipe.parts.map { part ->
            val fitted = fit.parts[part.id]
            val tube = part.tube
            val shaped = if (fitted != null && tube != null) {
                part.copy(blend = part.blend ?: blends[part.id]?.let { rounded(it.toDouble()) }, tube = tube.copy(profiles = fitted.places.mapValues { (_, places) -> RecipeOutline(through = places.map { listOf(rounded(it.x), rounded(it.y)) }) }))
            } else {
                part
            }
            val children = placed.filter { it.spec.parent == part.id }
            if (children.isEmpty()) return@map shaped
            shaped.copy(
                sockets = shaped.sockets.map { socket ->
                    val child = children.firstOrNull { child -> recipe.parts.first { it.id == child.spec.part }.socket == "socket.${socket.n}" }
                    if (child == null) socket else socket.copy(v = rounded(child.v ?: socket.v.toDouble()), reach = rounded(child.reach ?: 0.0))
                },
            )
        },
    )
}

private fun rounded(value: Double): Float = Math.round(value * PLACES) / PLACES
