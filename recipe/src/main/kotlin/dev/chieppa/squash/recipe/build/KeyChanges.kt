package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.document.MAX_KEY_BEND
import dev.chieppa.squash.document.OutlineScale
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.WidthScale
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipeKeyChange
import dev.chieppa.squash.recipe.model.RecipePart

/** The width names that scale one side of a part's Side or Top outline itself, whatever way the part rests. */
private val OUTLINE_SIDES = mapOf("side+" to (View.SIDE to true), "side-" to (View.SIDE to false), "top+" to (View.TOP to true), "top-" to (View.TOP to false))

/** The recipe part [name] names: a part by its id, or a pair by its id or either side's. */
internal fun recipePartNamed(recipe: Recipe, name: String): RecipePart? = recipe.parts.firstOrNull { part ->
    part.id == name || part.pair?.let { pair -> (pair.left ?: "${part.id}-l") == name || (pair.right ?: "${part.id}-r") == name } == true
}

/** The document parts a recipe part stands for as the build names them: one part, or a pair's two sides. */
internal fun documentIds(part: RecipePart): List<PartId> =
    part.pair?.let { listOf(PartId(it.left ?: "${part.id}-l"), PartId(it.right ?: "${part.id}-r")) } ?: listOf(PartId(part.id))

/**
 * [change] as the document's changes of [ids], the parts it names: each width named by a direction is read as the Side or Top side the
 * part's rest turns that way, as a tube's profiles are, a Top side named by direction alone scaling the other too where the part's tube
 * does not draw it apart; failing at [path].
 */
internal fun partChanges(recipe: Recipe, change: RecipeKeyChange, ids: List<PartId>, path: String): List<PartChange> {
    fun fail(at: String, message: String): Nothing = throw RecipeException(at, message)
    val part = recipePartNamed(recipe, change.part) ?: fail("$path.part", "no part \"${change.part}\"")
    val sides = HashMap<Pair<View, Boolean>, List<WidthScale>>()
    val byDirection = HashSet<Pair<View, Boolean>>()
    for ((name, places) in change.widths) {
        val at = "$path.widths.$name"
        val side = OUTLINE_SIDES[name] ?: run {
            val direction = tubeDirection(name) ?: fail(at, "a width is named by a direction (forward, front, back, up, down, left, right, outer, inner) or side+, side-, top+ or top-, not \"$name\"")
            val rest = part.rest ?: fail(at, "a width named by a direction needs the part's rest; name side+, side-, top+ or top- instead")
            val (sideUp, topUp) = tubeViews(rest)
            when (direction) {
                sideUp -> View.SIDE to true
                oppositeDirection(sideUp) -> View.SIDE to false
                topUp -> View.TOP to true
                oppositeDirection(topUp) -> View.TOP to false
                else -> fail(at, "a part resting ${rest.name.lowercase()} has no width toward $name")
            }
        }
        if (side in sides) fail(at, "two widths scale the same side of the ${side.first.name.lowercase()} outline")
        if (name !in OUTLINE_SIDES) byDirection += side
        sides[side] = places.mapIndexed { i, p ->
            if (p.size != 2) fail("$at[$i]", "a width scale is [x, scale], not $p")
            try {
                WidthScale(p[0], p[1])
            } catch (e: IllegalArgumentException) {
                fail("$at[$i]", e.message ?: e.toString())
            }
        }
        if (sides[side].orEmpty().zipWithNext().any { (a, b) -> a.x >= b.x }) fail(at, "width scales run in order of x")
    }
    val drawnApart = part.tube?.profiles?.keys.orEmpty().mapNotNull(::tubeDirection).toSet()
    val topUp = part.rest?.let { tubeViews(it).second }
    for (plus in listOf(true, false)) {
        val given = sides[View.TOP to plus]?.takeIf { (View.TOP to plus) in byDirection } ?: continue
        val other = topUp?.let { if (plus) oppositeDirection(it) else it }
        if ((View.TOP to !plus) !in sides && (other == null || other !in drawnApart)) sides[View.TOP to !plus] = given
    }
    val widths = listOf(View.SIDE, View.TOP).mapNotNull { view ->
        val plus = sides[view to true].orEmpty()
        val minus = sides[view to false].orEmpty()
        if (plus.isEmpty() && minus.isEmpty()) null else view to OutlineScale(plus, minus)
    }.toMap()
    return ids.map { id ->
        try {
            PartChange(id, widths, change.blend ?: 1f, change.bend ?: 0f)
        } catch (e: IllegalArgumentException) {
            fail(if (change.bend?.let { it in 0f..MAX_KEY_BEND } == false) "$path.bend" else "$path.blend", e.message ?: e.toString())
        }
    }
}
