package dev.chieppa.squash.screens.look

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

    /** The close-ups asked for, among face, hand and foot. */
    val close: Set<String> = values["close"]?.split(',')?.toSet() ?: setOf(FACE, HAND, FOOT)

    val examplesFolder = File(values["root"] ?: ".", EXAMPLES_FOLDER)
    val imagesFolder = File(values["out"] ?: File(values["root"] ?: ".", IMAGES_FOLDER).path)

    companion object {
        const val FACE = "face"
        const val HAND = "hand"
        const val FOOT = "foot"
    }
}
