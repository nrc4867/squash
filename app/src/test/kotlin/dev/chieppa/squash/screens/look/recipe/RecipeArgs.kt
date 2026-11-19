package dev.chieppa.squash.screens.look.recipe

import java.io.File

/** The close-ups a recipe build renders unless told otherwise. */
private const val DEFAULT_CLOSE = "face,hand,foot"

/** The look arguments a recipe build passes on to the renderer and measure as given. */
private val PASSED_ON = setOf("close", "outfits", "poses", "set", "reads", "scale", "around")

/** A recipe build renders each outfit's back and top views unless told otherwise. */
private const val DEFAULT_AROUND = "true"

/**
 * What the recipe command was asked for, from `key=value` arguments: the [recipe] to build or the character file to [export], where to
 * write ([out]), whether to stop after the character file ([buildOnly]) or skip [measure], and a [reference] image with its camera.
 */
class RecipeArgs(args: Array<String>) {
    /** Every `key=value` argument in order, a key given more than once kept each time; a value's spaces come percent-encoded. */
    val entries: List<Pair<String, String>> = args.flatMap { it.split(' ') }.filter { '=' in it }.map { it.substringBefore('=') to decoded(it.substringAfter('=')) }

    private val values: Map<String, String> = entries.toMap()

    val root = File(values["root"] ?: ".")

    private fun file(key: String): File? = values[key]?.let { path -> File(path).let { if (it.isAbsolute) it else File(root, path) } }

    val recipe: File? = file("recipe")
    val export: File? = file("export")

    /** A recipe to build and answer frame questions about, in place of rendering it. */
    val probe: File? = file("probe")

    /** Where the command writes its exit status, for the script that runs it through Gradle to exit with. */
    val status: File? = file("status")
    val out: File? = file("out")

    /** With `render=false`, only the character file is written. */
    val buildOnly: Boolean = values["render"] == "false"

    /** The parts the views draw alone, from `only=<id>,...`, or null for the whole character. */
    val only: List<String>? = values["only"]?.split(',')?.filter { it.isNotEmpty() }

    /** With `measure=false`, no measurements; with [only] and no `measure=true`, none either. */
    val measure: Boolean = values["measure"]?.let { it != "false" } ?: (only == null)

    /** With `watch=true`, the build runs again whenever the recipe file changes, in this one process, until stopped. */
    val watch: Boolean = values["watch"] == "true"

    /** A character sheet to split into one image a view, into [out]. */
    val crop: File? = file("crop")

    /** A reference image to set the character beside, rendered through the camera `eye`, `target` and `fov` give in its frame. */
    val reference: File? = file("reference")
    val eye: String? = values["eye"]
    val target: String? = values["target"]
    val fov: String? = values["fov"]

    /** The reference's flat background as `#rrggbb`, which its silhouette is keyed against; its top-left pixel when not given. */
    val ground: String? = values["ground"]

    /** With `fit=true`, the reference's camera is searched for, from `eye`, `target` and `fov` when given, before the compare. */
    val fit: Boolean = values["fit"] == "true"

    /** Body units to a metre of the reference frame. */
    val scale: String? = values["scale"]

    /**
     * The look command's arguments for this build: close-ups of the face, a hand and a foot, the back and top views, and the [shown] outfit
     * alone, unless asked otherwise; with [only], those parts alone.
     */
    fun lookArgs(shown: String?): Array<String> {
        val passed = values.filterKeys { it in PASSED_ON } + listOfNotNull(only?.let { "show" to it.joinToString(",") })
        val defaults = listOfNotNull("close" to DEFAULT_CLOSE, "around" to DEFAULT_AROUND, shown?.let { "outfits" to it }).toMap()
        return (defaults + passed).map { (k, v) -> "$k=$v" }.toTypedArray()
    }
}

/** [value] with `%20` and `%25`, which the recipe script writes for a space and a percent sign, read back. */
private fun decoded(value: String): String = value.replace("%20", " ").replace("%25", "%")
