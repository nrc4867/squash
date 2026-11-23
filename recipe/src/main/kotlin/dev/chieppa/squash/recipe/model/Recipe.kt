package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.DEFAULT_CREASE_STRENGTH
import dev.chieppa.squash.document.FAR_SIDE_DEPTH
import dev.chieppa.squash.document.ShadingLight
import dev.chieppa.squash.document.ShadingMode
import dev.chieppa.squash.document.REST_CREASE
import dev.chieppa.squash.document.JointAngle
import dev.chieppa.squash.document.NeckStyle
import dev.chieppa.squash.document.ViewKeys
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
    @Doc("The body drawn as a figure outline that the tube parts giving no profiles are fitted to.")
    val figure: RecipeFigure? = null,
    @Doc("The parts, each after the part it sits on. A garment's own parts (shades, a heel, a buckle) name their garment.")
    val parts: List<RecipePart> = emptyList(),
    @Doc("Painted eyes on a head.")
    val eyes: List<RecipeEye> = emptyList(),
    @Doc("Thin sheets of skin spanned between parts, such as a patagium or a wing membrane.")
    val membranes: List<RecipeMembrane> = emptyList(),
    @Doc("Hair made of locks, one entry a head: a preset hairstyle, its groups changed or added to, over an inner mass.")
    val hair: List<RecipeHair> = emptyList(),
    @Doc("Named expressions: joint angles of the face's parts, as ears back or a dropped jaw, and how far each opens a mouth. The build renders each one's face close-up.")
    val expressions: List<RecipeExpression> = emptyList(),
    @Doc("Clothes: slots, garments and outfits.")
    val wardrobe: RecipeWardrobe? = null,
    @Doc("The pose the character is saved and shown in, over the rest it is built in: a biped's arms brought down from A-pose.")
    val displayPose: List<BonePose> = emptyList(),
    @Doc("Named poses the build renders besides the display pose; not saved in the character.")
    val poses: Map<String, List<BonePose>> = emptyMap(),
    @Doc("Pose keys: the body's shape corrected in a pose, each blended in by joint angle as a pose nears it and gone at the rest.")
    val keys: List<RecipeKey> = emptyList(),
    @Doc("Proportions the built character is checked against, read off a reference: measure.txt flags each one the build is off by more than its tolerance.")
    val targets: List<RecipeTarget> = emptyList(),
    @Doc("Creases and wrinkles: ink and a shade band on the inner side of each bending joint, deepening with its bend, on the body and on cloth, and wrinkles and folds where cloth bunches and hangs. On at their defaults when left out; skin 0 and cloth 0 turn them off.")
    val creases: RecipeCreases? = null,
    @Doc("How the head meets the neck: blended, no visible neck, the head flowing into the shoulders in one curve; between, a short neck under a soft jaw line with a light shade under the chin; defined, a slim neck under the head's own closed outline, its jaw inked across the neck and a shade under the chin. It sets the head's join blend and ink where the head part gives neither. Blended when left out.")
    val neck: NeckStyle? = null,
    @Doc("How the character is shaded: flat or two-tone, how much darker its far side is, and where its light comes from. Two-tone renders read the far side and the light. As the defaults when left out.")
    val shading: RecipeShading? = null,
    @Doc("Head cheats that follow the camera: the face style, how far the face follows the camera, and keys per view moving the muzzle, ears, nose, any part or the head's shape. Every head's defaults when left out.")
    val viewKeys: ViewKeys? = null,
)

/** One proportion a build is checked against. */
@Serializable
@Doc("A proportion the built character should have.")
data class RecipeTarget(
    @Doc("What is measured: handLength, handWidth, footLength, footWidth, headHeight, headWidth, eyeLine, eyeWidth, eyeSpacing.")
    val name: String,
    @Doc("What the value is a share of: head, the head's height; or height, the figure's height from the ground to the top of the head. For a place (eyeLine) head is its depth down the head from the crown and height its height above the ground.")
    val of: String,
    @Doc("The share.") @Range(min = 0.0)
    val value: Float,
    @Doc("How far off it may be before it is flagged: a share of the value for a size (0.15 when left out), a share of the head for a place (0.05 when left out).")
    val tolerance: Float? = null,
)

/** A character's creases and wrinkles, on once given. */
@Serializable
@Doc("How strongly the character creases and wrinkles, each 0 (off) to 2, 1 when left out.")
data class RecipeCreases(
    @Doc("Joint creases on the body's skin: a short ink line and a shade band on the inner side of each bending joint (elbow, knee, hip, shoulder, neck, wrist, ankle, waist), faint at rest where a sheet draws one and deepening and lengthening with the bend.") @Range(min = 0.0, max = 2.0)
    val skin: Float = DEFAULT_CREASE_STRENGTH,
    @Doc("Every garment's creases, wrinkles and drape, times each garment's own creases, wrinkles and drape.") @Range(min = 0.0, max = 2.0)
    val cloth: Float = DEFAULT_CREASE_STRENGTH,
    @Doc("How much of a joint's crease shows at rest, as a share of a full bend's, 0 to 1.") @Range(min = 0.0, max = 1.0)
    val rest: Float = REST_CREASE,
)

/** A character's shading choice. */
@Serializable
@Doc("How the character is shaded.")
data class RecipeShading(
    @Doc("flat, one colour a part; or twoTone, a lit and a shadow colour with a hard edge. Renders are always two-tone.")
    val mode: ShadingMode = ShadingMode.FLAT,
    @Doc("Whether the side of a pair away from the camera darkens.")
    val farSideDarker: Boolean = true,
    @Doc("How much darker the far side is, as a share of its colour, 0 to 0.5: raise it where the sheet sets far legs and the far ear clearly darker.") @Range(min = 0.0, max = 0.5)
    val farSide: Float = FAR_SIDE_DEPTH,
    @Doc("view: the light turns with the camera, from in front of the viewer, above and to their left, so a back view is lit as the front is; body: fixed in front of the character, so a back view falls in shade.")
    val light: ShadingLight = ShadingLight.VIEW,
    @Doc("The shot's key light, fixed to the body: a surface is lit where it or the light above reaches, and materials' spots, bands and streaks come from it, so they stay toward it as the camera turns. From the light above when left out.")
    val key: RecipeKeyLight? = null,
)

/** The way to a key light in body space. */
@Serializable
@Doc("The way to a key light in body space: x forward, y up, z to the character's right; any length but zero.")
data class RecipeKeyLight(
    @Doc("Forward.")
    val x: Float,
    @Doc("Up.")
    val y: Float,
    @Doc("To the character's right.")
    val z: Float,
)

/** A named colour of the palette. */
@Serializable
@Doc("A named colour.")
data class RecipeSlot(
    @Doc("The name parts, regions and garments use for it.")
    val slot: String,
    @Doc("The colour: \"#rrggbb\", or [red, green, blue] from 0 to 1.")
    val color: RecipeColor,
    @Doc("What the app calls it; the slot's name when left out.")
    val label: String? = null,
    @Doc("Its toon material, which anything painted in it takes: a preset's name (\"metal\"), or an object with a preset and the settings it changes. Matte when left out.")
    @Serializable(with = RecipeMaterialSerializer::class)
    val material: RecipeMaterial? = null,
)

/** A named expression: joints of the face's parts set to angles, and how far it opens mouths. */
@Serializable
@Doc("A named expression.")
data class RecipeExpression(
    val id: String,
    val name: String,
    @Doc("Joint angles, each naming its part and joint; a mirrored part names each side's id.")
    val joints: List<JointAngle> = emptyList(),
    @Doc("How far it opens mouth groups, by group id, 0 closed to 1 open, in place of each group's own controls.open: parts the lips on any head, with or without a jaw.")
    val open: Map<String, Float> = emptyMap(),
)
