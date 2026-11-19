package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.model.RECIPE_FORMAT
import dev.chieppa.squash.recipe.model.Recipe

/**
 * A built recipe: the character, the commands that made it from the empty character on its plan, what looked doubtful, and the named
 * poses the recipe asks to be rendered in.
 */
class RecipeBuild(val document: SquashDocument, val commands: List<DocumentCommand>, val warnings: List<String>, val poses: Map<String, Pose>)

/**
 * Builds recipes into characters by sending the document commands each part of the recipe stands for, in this order: the palette;
 * every body part, placed and shaped in the recipe's order; their colour and surface work; the wardrobe's slots and garments; the
 * parts garments own, placed, shaped and painted; the painted eyes; the expressions; the outfits and the one shown; the display pose.
 * Anything wrong fails as a [RecipeException] at the recipe path that caused it. See HLD: architecture, Recipes.
 */
class RecipeBuilder(private val templates: TemplateFolder) {
    fun build(recipe: Recipe): RecipeBuild {
        if (recipe.format != RECIPE_FORMAT) throw RecipeException("$.format", "this build reads recipe format $RECIPE_FORMAT, not ${recipe.format}")
        val plan = templates.plans.filter { it.id == BodyPlanId(recipe.plan) && (recipe.planVersion == null || it.version == recipe.planVersion) }.maxByOrNull { it.version }
            ?: throw RecipeException("$.plan", "no body plan \"${recipe.plan}\"${recipe.planVersion?.let { " version $it" } ?: ""}; plans: ${templates.plans.map { it.id.value }.distinct().sorted().joinToString()}")
        val empty = SquashDocument(Character(CharacterId(recipe.id), recipe.name, plan.ref))
        val context = BuildContext(recipe, templates, plan, empty)
        with(context) {
            recipe.palette.forEachIndexed { i, slot -> send("$.palette[$i]", SetPaletteSlot(SlotName(slot.slot), slot.color.slotColor, shadow = slot.shadow?.slotColor, label = slot.label, opacity = slot.opacity)) }
            val body = recipe.parts.withIndex().filter { it.value.garment == null }
            val owned = recipe.parts.withIndex().filter { it.value.garment != null }
            val bodyIds = body.map { (i, part) -> placePart(i, part) }
            body.zip(bodyIds).forEach { (entry, ids) -> paintPart(entry.index, entry.value, ids.left) }
            garments()
            owned.forEach { (i, part) -> paintPart(i, part, placePart(i, part).left) }
            eyes()
            expressions()
            outfits()
            if (recipe.displayPose.isNotEmpty()) send("$.displayPose", SetDisplayPose(pose("$.displayPose", recipe.displayPose)))
            val poses = recipe.poses.mapValues { (name, bones) -> pose("$.poses.$name", bones) }
            return RecipeBuild(document, commands.toList(), warnings.toList(), poses)
        }
    }
}

/** [bones] as a pose, each naming a part the recipe placed, a pair by its left side or each side by its own id. */
private fun BuildContext.pose(path: String, bones: List<BonePose>): Pose {
    val named = bones.mapIndexed { i, bone -> bone.copy(part = partOf("$path[$i].part", bone.part.value)) }
    val twice = named.groupBy { it.part to it.joint }.filterValues { it.size > 1 }.keys.firstOrNull()
    if (twice != null) fail(path, "the pose sets ${twice.first.value} ${twice.second} twice")
    return guard(path) { Pose(named) }
}
