package dev.chieppa.squash.screens.look

import dev.chieppa.squash.fieldgpu.GpuFieldReads
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads
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

    /** Where the sessions read their fields: `reads=gpu` on the GPU, the CPU otherwise. */
    val reads: FieldReads by lazy { if (values["reads"] == "gpu") GpuFieldReads() else CpuFieldReads }

    /** Read each example's saved file rather than build it from its fixture's commands. */
    val fromFile: Boolean = values["file"] == "true"

    /** The outfit ids asked for, or null for every outfit the example has. */
    val outfits: List<String>? = values["outfits"]?.takeIf { it != ALL }?.split(',')

    /** The pose names asked for, or null for every named pose. */
    val poses: List<String>? = values["poses"]?.takeIf { it != ALL }?.split(',')

    /**
     * The close-ups asked for, among face, hand, foot, hands, the bare hand at rest, curled into a fist and open, feet, the bare foot or
     * paw at rest, curled, open and spread, joins, where the head, a forelimb and a hind limb meet the body, surface, the joins' spots, the
     * outline close up and the mesh, drag, a torso drag's end, and release, a Move drag of a forelimb's socket during and after it.
     * `none` asks for no close-up at all.
     */
    val close: Set<String> = values["close"]?.split(',')?.filter { it != NONE }?.toSet() ?: setOf(FACE, HAND, FOOT)

    /** The entries of [close] that name no close-up, which are part ids, each framed close per outfit. */
    val closeParts: List<String> = close.filter { it !in CLOSE_WORDS }

    /** With `around=true`, each outfit's back and top views as well, beside each other in `<outfit>-back-top`. */
    val around: Boolean = values["around"] == "true"

    /** How wide each whole-character view is, in pixels, from `tile=`, its height half as much again: larger to see marks close. */
    val tile: Int = values["tile"]?.toInt() ?: VIEW_TILE

    /** With `show=<part>,...`, the views draw and frame only those parts, and no close-up is rendered. */
    val show: Set<String>? = values["show"]?.split(',')?.filter { it.isNotEmpty() }?.toSet()

    /** With `thumbs=true`, each example's Library outfit and garment thumbnails, timed, instead of the usual views. */
    val thumbs: Boolean = values["thumbs"] == "true"

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

    /** Templates to swap parts to before anything else, from `template=part:id:version,...`, each sent as Set template. */
    val templates: List<Pair<String, String>> = values["template"]?.split(',')?.map { entry ->
        require(entry.count { it == ':' } == 2) { "template takes part:id:version, not $entry" }
        entry.substringBefore(':') to entry.substringAfter(':')
    }.orEmpty()

    /** Outlines to start from template and drag after the parameters are set, from `outline=part:along:across,...` (see [OutlineDrag]). */
    val outlineDrags: List<OutlineDrag> = values["outline"]?.split(',')?.map(OutlineDrag::parse).orEmpty()

    /** The character's skin and cloth crease strengths to render with, from `creases=skin,cloth`, or null to keep its own. */
    val creases: Pair<Float, Float>? = values["creases"]?.split(',')?.let { strengths ->
        require(strengths.size == 2) { "creases takes skin,cloth, not ${values["creases"]}" }
        strengths[0].toFloat() to strengths[1].toFloat()
    }

    /** With `paint=test`, test regions painted over the example before it renders (see [paintTest]). */
    val paintTest: Boolean = values["paint"] == "test"

    /** Whether measure prints every part's origin and box. */
    val parts: Boolean = values["parts"] == "true"

    /** With `profile=true`, `measure` also prints where a surface build's time goes. */
    val profile: Boolean = values["profile"] == "true"

    /** With `only=stack`, `measure` prints only the one surface's stack timings. */
    val stackOnly: Boolean = values["only"] == "stack"

    /** The surface edits `measure` times, among head, front-leg-l and torso, or null for all of them. */
    val edits: List<String>? = values["edits"]?.split(',')

    /** With `jfr=<folder>` and a profile, `measure` records the profiled edits under the JDK's flight recorder into that folder. */
    val record: File? = values["jfr"]?.let(::File)

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
        const val JOINS = "joins"
        const val SURFACE = "surface"
        const val DRAG = "drag"
        const val RELEASE = "release"
        const val ANCHORS = "anchors"
        const val ORBIT = "orbit"

        /** How wide each whole-character view is by default, in pixels. */
        const val VIEW_TILE = 600

        /** `close=none` asks for no close-up. */
        const val NONE = "none"

        /** Every word `close=` takes; anything else it holds is a part id. */
        val CLOSE_WORDS: Set<String> = setOf(FACE, HAND, FOOT, HANDS, FEET, JOINS, SURFACE, DRAG, RELEASE, ANCHORS, ORBIT)
    }
}
