package dev.chieppa.squash.screens.look

import dev.chieppa.squash.screens.look.shots.ShotCamera
import java.io.File

/** Says to take every value there is: every outfit, every named pose. */
private const val ALL = "all"

/** Where the look and measure commands find the examples and leave their images, from the project's root. */
private const val EXAMPLES_FOLDER = "examples"
private const val IMAGES_FOLDER = "app/build/look"

/** What the look or measure command was asked for, from `key=value` arguments. */
class LookArgs(args: Array<String>) {
    private val values: Map<String, String> = args.flatMap { it.split(' ') }.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

    /** The examples to look at, by file name. */
    val examples: List<String> = values["example"]?.split(',') ?: listOf("woman-dressed")

    /** Read each example's saved file rather than build it from its fixture's commands. */
    val fromFile: Boolean = values["file"] == "true"

    /** The outfit ids asked for, or null for every outfit the example has. */
    val outfits: List<String>? = values["outfits"]?.takeIf { it != ALL }?.split(',')

    /** The pose names asked for, or null for every named pose. */
    val poses: List<String>? = values["poses"]?.takeIf { it != ALL }?.split(',')

    /** The close-ups asked for, among face, hand, foot, hands, the bare hand at rest, curled into a fist and open, and feet, the bare foot or paw at rest, curled, open and spread. */
    val close: Set<String> = values["close"]?.split(',')?.toSet() ?: setOf(FACE, HAND, FOOT)

    /** The shot list to render instead of the usual views: `reference` for the reference's 24 shots. */
    val shots: String? = values["shots"]

    /** An explicit camera in the reference frame to render through instead of the usual views, from `eye`, `target`, `fov` and `size`. */
    val camera: ShotCamera? = values["eye"]?.let { eye -> ShotCamera.parse(eye, requireNotNull(values["target"]) { "eye needs a target" }, values["fov"], values["size"]) }

    /**
     * Parameters to set on parts before rendering, from `set=part:name:value,...`, each sent as Set parameter over the example as it
     * is built, a pair's both sides: a variant of an example without a fixture of its own.
     */
    val settings: List<Triple<String, String, Float>> = values["set"]?.split(',')?.map { entry ->
        val (part, name, value) = entry.split(':').also { require(it.size == 3) { "set takes part:name:value, not $entry" } }
        Triple(part, name, value.toFloat())
    }.orEmpty()

    /** Whether measure prints every part's origin and box. */
    val parts: Boolean = values["parts"] == "true"

    /** Body units to a metre of the reference frame, for the shots and the explicit camera. */
    val unitsPerMetre: Float = values["scale"]?.toFloat() ?: 1f

    val examplesFolder = File(values["root"] ?: ".", EXAMPLES_FOLDER)
    val imagesFolder = File(values["out"] ?: File(values["root"] ?: ".", IMAGES_FOLDER).path)

    companion object {
        const val FACE = "face"
        const val HAND = "hand"
        const val FOOT = "foot"
        const val HANDS = "hands"
        const val FEET = "feet"
    }
}
