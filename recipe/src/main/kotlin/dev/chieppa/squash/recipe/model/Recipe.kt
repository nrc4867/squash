package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointAngle
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The recipe grammar this build reads. */
const val RECIPE_FORMAT = 1

/** The body plan a recipe names when it names none: no sockets of its own, every part placed on hand-made sockets. */
const val DEFAULT_PLAN = "blank"

/**
 * A character as an artist describes it: its parts and where they sit, their drawn outlines, its colours, face, clothes and poses. A
 * recipe is built by sending the document commands it stands for, in order, over an empty character. See HLD: architecture, Recipes.
 */
@Serializable
@Doc("A Squash character described as parts, outlines, colours, face, clothes and poses; built into a character file by sending the document commands it stands for.")
data class Recipe(
    @SerialName("\$schema")
    val schemaPath: String? = null,
    @Doc("The recipe grammar version.") @Range(min = 1.0, max = 1.0) @Required
    val format: Int = RECIPE_FORMAT,
    @Doc("The character's id, also the name of the files a build writes.")
    val id: String,
    @Doc("The character's name as the app shows it.")
    val name: String,
    @Doc("The body plan the character starts on: blank, biped, bird, canine-quadruped or fish.")
    val plan: String = DEFAULT_PLAN,
    @Doc("The plan's version; the newest shipped when left out.") @Range(min = 1.0)
    val planVersion: Int? = null,
    @Doc("Named colours, in order. Every colour the recipe uses is a slot named here.")
    val palette: List<RecipeSlot> = emptyList(),
    @Doc("The parts, each after the part it sits on. A garment's own parts (shades, a heel, a buckle) name their garment.")
    val parts: List<RecipePart> = emptyList(),
    @Doc("Painted eyes on a head.")
    val eyes: List<RecipeEye> = emptyList(),
    @Doc("Named expressions: joint angles of the face's parts, as ears back or a dropped jaw.")
    val expressions: List<RecipeExpression> = emptyList(),
    @Doc("Clothes: slots, garments and outfits.")
    val wardrobe: RecipeWardrobe? = null,
    @Doc("The pose the character is saved and shown in, over the rest it is built in: a biped's arms brought down from A-pose.")
    val displayPose: List<BonePose> = emptyList(),
    @Doc("Named poses the build renders besides the display pose; not saved in the character.")
    val poses: Map<String, List<BonePose>> = emptyMap(),
)

/** A named colour of the palette. */
@Serializable
@Doc("A named colour.")
data class RecipeSlot(
    @Doc("The name parts, regions and garments use for it.")
    val slot: String,
    @Doc("The colour: \"#rrggbb\", or [red, green, blue] from 0 to 1.")
    val color: RecipeColor,
    @Doc("Its own shadow tone; a warm shift of the colour when left out.")
    val shadow: RecipeColor? = null,
    @Doc("What the app calls it; the slot's name when left out.")
    val label: String? = null,
)

/** A named expression: joints of the face's parts set to angles. */
@Serializable
@Doc("A named expression.")
data class RecipeExpression(
    val id: String,
    val name: String,
    @Doc("Joint angles, each naming its part and joint; a mirrored part names each side's id.")
    val joints: List<JointAngle> = emptyList(),
)
