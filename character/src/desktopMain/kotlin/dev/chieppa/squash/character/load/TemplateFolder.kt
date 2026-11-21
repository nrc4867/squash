package dev.chieppa.squash.character.load

import dev.chieppa.squash.character.catalog.partCount
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.TeethPreset
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

internal const val PART_SUFFIX = ".part.json"
private const val PLAN_SUFFIX = ".plan.json"
private const val PLANS_FOLDER = "plans"
private const val MOUTH_SUFFIX = ".mouth.json"
private const val MOUTHS_FOLDER = "mouths"
private const val TEETH_SUFFIX = ".teeth.json"
private const val TEETH_FOLDER = "teeth"

/** Where the build packs the templates folder on the classpath, and the file there naming what it holds. */
private const val SHIPPED_ROOT = "dev/chieppa/squash/templates"
private const val SHIPPED_INDEX = "index.txt"

/**
 * Everything in a templates folder: its part templates, body plans, [mouths], the mouth styles of its mouths folder, and [teeth], the
 * teeth presets of its teeth folder; and [saved], the folder templates saved from parts are written to and loaded from, if any.
 */
data class TemplateFolder(
    val parts: List<PartTemplate>,
    val plans: List<BodyPlan>,
    val saved: Path? = null,
    val mouths: List<MouthStyle> = emptyList(),
    val teeth: List<TeethPreset> = emptyList(),
) {
    /** A library over the parts, plans, mouth styles and teeth presets. */
    fun library(): TemplateLibrary = TemplateLibrary(parts, plans, mouths, teeth)

    companion object {
        /**
         * Loads every `*.part.json` in [folder] in name order, a version number read as a number so `paw-10` follows `paw-9`, and every `*.plan.json` in its plans folder, the plans placing the most
         * parts first, a pair counting once, then among equals the most counting each side, then by name, so Blank, which places its
         * root alone, comes last.
         */
        fun load(folder: Path): TemplateFolder {
            val parts = folder.listDirectoryEntries().map { it.name }
            val plans = folder.resolve(PLANS_FOLDER).listDirectoryEntries().map { "$PLANS_FOLDER/${it.name}" }
            fun sub(name: String) = folder.resolve(name).takeIf { it.isDirectory() }?.listDirectoryEntries()?.map { "$name/${it.name}" }.orEmpty()
            return load(parts + plans + sub(MOUTHS_FOLDER) + sub(TEETH_FOLDER)) { folder.resolve(it).readText() }
        }

        private val packed: TemplateFolder by lazy { load(shippedResource(SHIPPED_INDEX).lines().filter { it.isNotEmpty() }, ::shippedResource) }

        /** The templates packed into the app's resources, loaded by the same rules as a folder once per process. */
        fun shipped(): TemplateFolder = packed

        private fun load(files: List<String>, read: (String) -> String): TemplateFolder {
            val parts = files.filter { '/' !in it && it.endsWith(PART_SUFFIX) }.sortedBy(::nameOrder)
                .map { TemplateLoader.loadPart(it, read(it)) }
            val plans = files.filter { it.startsWith("$PLANS_FOLDER/") && it.endsWith(PLAN_SUFFIX) }.sorted()
                .map { TemplateLoader.loadPlan(it.substringAfterLast('/'), read(it), parts) }
            val mouths = files.filter { it.startsWith("$MOUTHS_FOLDER/") && it.endsWith(MOUTH_SUFFIX) }.sorted()
                .map { TemplateLoader.loadMouth(it.substringAfterLast('/'), read(it)) }
            val teeth = files.filter { it.startsWith("$TEETH_FOLDER/") && it.endsWith(TEETH_SUFFIX) }.sorted()
                .map { TemplateLoader.loadTeeth(it.substringAfterLast('/'), read(it)) }
            val unordered = TemplateFolder(parts, plans, mouths = mouths, teeth = teeth)
            return unordered.copy(
                plans = plans.sortedWith(
                    compareByDescending<BodyPlan> { it.partCount(unordered) }.thenByDescending { it.partCount(unordered, bothSides = true) }.thenBy { it.name },
                ),
            )
        }

        private fun shippedResource(name: String): String {
            val stream = checkNotNull(TemplateFolder::class.java.classLoader.getResourceAsStream("$SHIPPED_ROOT/$name")) {
                "No shipped template resource $name"
            }
            return stream.use { it.readBytes().decodeToString() }
        }
    }
}

/** How wide a number in a file name is padded for its name order. */
private const val NAME_NUMBER_WIDTH = 6

/** [name] with each run of digits padded to the same width, so names sort by their numbers' values. */
private fun nameOrder(name: String): String = Regex("\\d+").replace(name) { it.value.padStart(NAME_NUMBER_WIDTH, '0') }
