package dev.chieppa.squash.screens.look.recipe

import java.io.File

/** The close-ups a recipe build renders unless told otherwise. */
private const val DEFAULT_CLOSE = "face,hand,foot"

/** The look arguments a recipe build passes on to the renderer and measure as given. */
private val PASSED_ON = setOf("close", "outfits", "poses", "set", "reads", "scale")

/**
 * What the recipe command was asked for, from `key=value` arguments: the [recipe] to build or the character file to [export], where to
 * write ([out]), whether to stop after the character file ([buildOnly]) or skip [measure], and a [reference] image with its camera.
 */
class RecipeArgs(args: Array<String>) {
    private val values: Map<String, String> = args.flatMap { it.split(' ') }.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

    val root = File(values["root"] ?: ".")

    private fun file(key: String): File? = values[key]?.let { path -> File(path).let { if (it.isAbsolute) it else File(root, path) } }

    val recipe: File? = file("recipe")
    val export: File? = file("export")
    val out: File? = file("out")

    /** With `render=false`, only the character file is written. */
    val buildOnly: Boolean = values["render"] == "false"

    /** With `measure=false`, no measurements. */
    val measure: Boolean = values["measure"] != "false"

    /** A reference image to set the character beside, rendered through the camera `eye`, `target` and `fov` give in its frame. */
    val reference: File? = file("reference")
    val eye: String? = values["eye"]
    val target: String? = values["target"]
    val fov: String? = values["fov"]

    /** The reference's flat background as `#rrggbb`, which its silhouette is keyed against; its top-left pixel when not given. */
    val ground: String? = values["ground"]

    /** The look command's arguments for this build: close-ups of the face, a hand and a foot, and the [shown] outfit alone, unless asked otherwise. */
    fun lookArgs(shown: String?): Array<String> {
        val passed = values.filterKeys { it in PASSED_ON }
        val defaults = listOfNotNull("close" to DEFAULT_CLOSE, shown?.let { "outfits" to it }).toMap()
        return (defaults + passed).map { (k, v) -> "$k=$v" }.toTypedArray()
    }
}
