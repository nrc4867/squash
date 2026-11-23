package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.RecipeJson
import dev.chieppa.squash.recipe.build.RecipeBuild
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.build.figureOutlineJson
import dev.chieppa.squash.recipe.model.RecipeTarget
import dev.chieppa.squash.screens.look.LookArgs
import dev.chieppa.squash.screens.look.measureSession
import dev.chieppa.squash.screens.look.renderSession
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.time.measureTime

/**
 * One build of the recipe [RecipeArgs.recipe] names: the character file, its renders, its measurements and, with a reference, the compare,
 * each file it writes printed. [templates] are read once and kept for every run, and the character's session from one run to the next
 * (see [KeptSession]).
 */
class RecipeRun(private val options: RecipeArgs, private val templates: TemplateFolder = TemplateFolder.shipped()) : AutoCloseable {
    private val sessions = KeptSession()
    private val keyBodies = PosedKeyBodies(templates.library())

    /** The recipe file. */
    val file: File = options.recipe ?: throw RecipeException("$", "name the recipe file: tools/recipe.sh <recipe.json>")

    /** Builds [text], the recipe as read, and judges it; the exit status: 0, or 1 when a render failed. */
    fun run(text: String = readRecipe()): Int {
        lateinit var built: RecipeBuild
        val buildTime = measureTime { built = RecipeBuilder(templates, keyBodies).build(RecipeJson.read(text)) }
        val document = built.document
        val id = document.character.id.value
        val out = options.out ?: File(options.root, "build/recipes/$id")
        out.mkdirs()
        val saved = File(out, "$id.json").apply { writeText(SquashFormat().plainText(document) + "\n") }
        built.warnings.forEach { System.err.println("recipe warning at $it") }
        println("recipe: built ${built.commands.size} commands in ${buildTime.inWholeMilliseconds} ms")
        built.figure?.let { figure ->
            val rounds = figure.roundTimes.joinToString { "${it.inWholeMilliseconds}" }
            val means = figure.means.joinToString { "%.4f".format(it) }
            val refits = figure.refitTimes.joinToString { "${it.inWholeMilliseconds}" }
            println("figure: fit ${figure.fitTime.inWholeMilliseconds} ms (again with corrections: ${refits.ifEmpty { "none" }} ms); rounds on the field ${rounds.ifEmpty { "none" }} ms; mean miss by round $means; ${figure.measure.misses.size} stretches past the tolerance")
        }
        built.keys.forEach { key ->
            val means = key.means.joinToString { "%.4f".format(it) }
            val misses = File(out, "key-${key.name}.txt").apply {
                writeText(key.measure.samples.joinToString("\n", postfix = "\n") { m -> "%s %s %.4f %.4f %s %.4f".format(m.view, m.sample.part, m.sample.sample.at.x, m.sample.sample.at.y, m.profile, m.miss) })
            }
            key.posed?.let { posed ->
                val traced = File(out, "key-${key.name}-posed.json").apply { writeText(figureOutlineJson(posed)) }
                println("key ${key.name}: the body in its pose, traced, ${traced.absolutePath}")
            }
            println("key ${key.name}: fitted in ${key.time.inWholeMilliseconds} ms; mean miss unchanged, then by round $means; ${key.measure.misses.size} stretches past the tolerance; each sample's miss in ${misses.absolutePath}")
        }
        built.fitted?.let { recipe ->
            val fitted = File(out, "$id.fitted.recipe.json").apply { writeText(RecipeJson.write(recipe)) }
            println("fit: the recipe as fitted, ${fitted.absolutePath}")
        }
        println("character: ${saved.absolutePath}")
        if (options.buildOnly) return 0
        val look = LookArgs(options.lookArgs(document.character.wardrobe.shown?.value))
        requireParts(document.character, look)
        val errors = mutableListOf<String>()
        val renders = File(out, "renders")
        renders.listFiles { f -> f.extension == "png" }?.forEach { it.delete() }
        val open = sessions.open(built)
        val renderTime = measureTime { renderSession(open.session, open.pose, open.work, built.poses, look, errors, renders) }
        println("renders: ${renders.absolutePath} (${renderTime.inWholeMilliseconds} ms)")
        renders.listFiles { f -> f.extension == "png" }?.sortedBy { it.name }?.forEach { println("  ${it.absolutePath}") }
        built.figure?.let { FigureOverlay(options, errors).keep(open, it, renders) }
        hairFitReport(document.character, open.session.assembly.output.value, File(out, "hair-fit.txt"))
        if (options.measure) {
            val table = File(out, "measure.txt")
            document.character.wardrobe.shown?.let { open.session.send(ShowOutfit(it)) }
            val measureTime = measureTime { printInto(table) { measureSession(id, open.session, open.work, built.poses, look, timings = false, targets = built.targets + referenceTargets(), rig = open.pose.rig.rig) } }
            println("measure: ${table.absolutePath} (${measureTime.inWholeMilliseconds} ms)")
        }
        options.reference?.let { reference -> ReferenceCompare(options, errors).compare(open, reference, File(out, "compare")) }
        errors.forEach { System.err.println("render error: $it") }
        return if (errors.isEmpty()) 0 else 1
    }

    /** The targets the `targets=<file>,<file>` option names, as `kit sheet measure` writes them. */
    private fun referenceTargets(): List<RecipeTarget> = options.targets.flatMap { file ->
        if (!file.isFile) throw RecipeException("$", "no targets file at ${file.absolutePath}")
        RecipeJson.readTargets(file.readText())
    }

    override fun close() {
        sessions.close()
        keyBodies.close()
    }

    /** The recipe file's text, failing as a recipe error when there is none. */
    fun readRecipe(): String {
        if (!file.isFile) throw RecipeException("$", "no recipe file at ${file.absolutePath}")
        return file.readText()
    }

    /** Fails as a recipe error when `close=` or `only=` names a part [character] does not have. */
    private fun requireParts(character: Character, look: LookArgs) {
        val ids = character.parts.map { it.id.value }.toSet()
        val asked = listOf("close" to look.closeParts, "only" to look.show.orEmpty().toList())
        for ((key, names) in asked) {
            val missing = names.filter { it !in ids }
            if (missing.isEmpty()) continue
            val words = if (key == "close") "; close-ups: ${LookArgs.CLOSE_WORDS.sorted().joinToString()}" else ""
            throw RecipeException("$", "$key=${missing.joinToString(",")}: no such part; parts: ${ids.joinToString()}$words")
        }
    }
}

/** Runs [block] with what this thread prints to standard out written to [file] instead. */
internal fun printInto(file: File, block: () -> Unit) {
    PrintStream(FileOutputStream(file), true, Charsets.UTF_8).use { stream -> ThreadOutput.into(stream) { block() } }
}
