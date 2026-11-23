package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.SetNeckStyle
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.KeyLight
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.CharacterCreases
import dev.chieppa.squash.document.CharacterShading
import dev.chieppa.squash.document.MOST_FAR_SIDE_DEPTH
import dev.chieppa.squash.document.SetCharacterShading
import dev.chieppa.squash.document.MOST_CREASE_STRENGTH
import dev.chieppa.squash.document.SetCharacterCreases
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetViewKeys
import dev.chieppa.squash.document.ViewKeys
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.model.RECIPE_FORMAT
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipeCreases
import dev.chieppa.squash.recipe.model.RecipeShading
import dev.chieppa.squash.recipe.model.RecipeTarget

/**
 * A built recipe: the character, the commands that made it from the empty character on its plan, what looked doubtful, and the named
 * poses the recipe asks to be rendered in.
 */
class RecipeBuild(
    val document: SquashDocument,
    val commands: List<DocumentCommand>,
    val warnings: List<String>,
    val poses: Map<String, Pose>,
    val targets: List<RecipeTarget> = emptyList(),
    /** How the recipe's figure was fitted, or null without one. */
    val figure: FigureReport? = null,
    /** How each key's outline was fitted. */
    val keys: List<KeyReport> = emptyList(),
    /** The recipe as fitted, its figure and its keys' outlines given as what they fitted, or null with nothing to fit. */
    val fitted: Recipe? = null,
)

/**
 * Builds recipes into characters by sending the document commands each part of the recipe stands for, in this order: the palette;
 * every body part, placed and shaped in the recipe's order; their colour and surface work; the wardrobe's slots and garments; the
 * parts garments own, placed, shaped and painted; the painted eyes; the membranes; the hair's lock groups and the body's collision shapes; the expressions; the outfits and the one shown; the display pose;
 * the creases; the neck style; the shading; the view keys; the pose keys.
 * A key's outline is fitted on the body posed by [keyBodies] when given, at rest otherwise.
 * Anything wrong fails as a [RecipeException] at the recipe path that caused it. See HLD: architecture, Recipes.
 */
class RecipeBuilder(private val templates: TemplateFolder, private val keyBodies: KeyBodies? = null) {
    fun build(drawn: Recipe): RecipeBuild {
        val figure = drawn.figure?.let { fittedFigure(drawn, it, templates) }
        val (recipe, keys) = fittedKeys(figure?.recipe ?: drawn, templates, keyBodies)
        if (recipe.format != RECIPE_FORMAT) throw RecipeException("$.format", "this build reads recipe format $RECIPE_FORMAT, not ${recipe.format}")
        val plan = templates.plans.filter { it.id == BodyPlanId(recipe.plan) && (recipe.planVersion == null || it.version == recipe.planVersion) }.maxByOrNull { it.version }
            ?: throw RecipeException("$.plan", "no body plan \"${recipe.plan}\"${recipe.planVersion?.let { " version $it" } ?: ""}; plans: ${templates.plans.map { it.id.value }.distinct().sorted().joinToString()}")
        val empty = SquashDocument(Character(CharacterId(recipe.id), recipe.name, plan.ref))
        val context = BuildContext(recipe, templates, plan, empty)
        with(context) {
            recipe.palette.forEachIndexed { i, slot ->
                slot.material?.shadowSlot?.let { slot("$.palette[$i].material.shadowSlot", it) }
                val material = slot.material?.let { guard("$.palette[$i].material") { it.slotMaterial } }
                send("$.palette[$i]", SetPaletteSlot(SlotName(slot.slot), slot.color.slotColor, label = slot.label, material = material))
            }
            val body = recipe.parts.withIndex().filter { it.value.garment == null }
            val owned = recipe.parts.withIndex().filter { it.value.garment != null }
            val bodyIds = body.map { (i, part) -> placePart(i, part) }
            body.zip(bodyIds).forEach { (entry, ids) -> paintPart(entry.index, entry.value, ids.left) }
            garments()
            owned.forEach { (i, part) -> paintPart(i, part, placePart(i, part).left) }
            eyes()
            membranes()
            hair()
            expressions()
            outfits()
            if (recipe.displayPose.isNotEmpty()) send("$.displayPose", SetDisplayPose(pose("$.displayPose", recipe.displayPose)))
            recipe.creases?.let { creases(it) }
            recipe.neck?.let { send("$.neck", SetNeckStyle(it)) }
            recipe.shading?.let { shading(it) }
            recipe.viewKeys?.let { viewKeys(it) }
            if (recipe.keys.isNotEmpty()) poseKeys()
            val poses = recipe.poses.mapValues { (name, bones) -> pose("$.poses.$name", bones) }
            val fitted = recipe.takeIf { figure != null || keys.isNotEmpty() }?.copy(figure = null)
            return RecipeBuild(document, commands.toList(), warnings.toList(), poses, recipe.targets, figure, keys, fitted)
        }
    }
}

/** [bones] as a pose, each naming a part the recipe placed, a pair by its left side or each side by its own id. */
internal fun BuildContext.pose(path: String, bones: List<BonePose>): Pose {
    val named = bones.mapIndexed { i, bone -> bone.copy(part = partOf("$path[$i].part", bone.part.value)) }
    val twice = named.groupBy { it.part to it.joint }.filterValues { it.size > 1 }.keys.firstOrNull()
    if (twice != null) fail(path, "the pose sets ${twice.first.value} ${twice.second} twice")
    return guard(path) { Pose(named) }
}

/** Sends [shading] as the character's, its far side's depth checked at its path. */
private fun BuildContext.shading(shading: RecipeShading) {
    if (shading.farSide !in 0f..MOST_FAR_SIDE_DEPTH) fail("$.shading.farSide", "a far side's depth lies from 0 to $MOST_FAR_SIDE_DEPTH: here ${shading.farSide}")
    val key = shading.key?.let { key -> guard("$.shading.key") { KeyLight(key.x, key.y, key.z) } }
    send("$.shading", SetCharacterShading(CharacterShading(shading.mode, shading.farSideDarker, shading.farSide, shading.light, key)))
}

/** Sends [keys] as the character's view keys, each part a move names read as the recipe names it, at its path. */
private fun BuildContext.viewKeys(keys: ViewKeys) {
    val named = keys.keys.mapIndexed { k, key ->
        key.copy(moves = key.moves.mapIndexed { m, move -> move.part?.let { move.copy(part = partOf("$.viewKeys.keys[$k].moves[$m].part", it.value)) } ?: move })
    }
    send("$.viewKeys", SetViewKeys(keys.copy(keys = named)))
}

/** Sends [creases] as the character's, each strength checked at its path. */
private fun BuildContext.creases(creases: RecipeCreases) {
    if (creases.skin !in 0f..MOST_CREASE_STRENGTH) fail("$.creases.skin", "a skin crease strength lies from 0 to $MOST_CREASE_STRENGTH: here ${creases.skin}")
    if (creases.cloth !in 0f..MOST_CREASE_STRENGTH) fail("$.creases.cloth", "a cloth crease strength lies from 0 to $MOST_CREASE_STRENGTH: here ${creases.cloth}")
    if (creases.rest !in 0f..1f) fail("$.creases.rest", "a rest crease share lies from 0 to 1: here ${creases.rest}")
    send("$.creases", SetCharacterCreases(CharacterCreases(creases.skin, creases.cloth, creases.rest)))
}
