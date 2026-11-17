package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.RecipeJson
import dev.chieppa.squash.recipe.build.RecipeBuild
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import dev.chieppa.squash.screens.look.LookArgs
import dev.chieppa.squash.screens.look.measureDocument
import dev.chieppa.squash.screens.look.renderDocument
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess
import kotlin.time.measureTime

/** What the command exits with when the recipe is wrong, apart from a render that failed. */
private const val RECIPE_WRONG = 2

/**
 * Builds a recipe into a character and judges it, headless: writes the character file, renders its standard views and close-ups, prints
 * and keeps its measurements, and with a reference image renders it through the reference's camera beside it with a silhouette overlay
 * and IoU. With `export=<character file>` it instead writes the character as a recipe. Run through `tools/recipe.sh`.
 */
fun main(args: Array<String>) {
    val options = RecipeArgs(args)
    val code = try {
        if (options.export != null) export(options) else build(options)
    } catch (e: RecipeException) {
        System.err.println("recipe error at ${e.report}")
        RECIPE_WRONG
    }
    exitProcess(code)
}

private fun build(options: RecipeArgs): Int {
    val file = options.recipe ?: throw RecipeException("$", "name the recipe file: tools/recipe.sh <recipe.json>")
    if (!file.isFile) throw RecipeException("$", "no recipe file at ${file.absolutePath}")
    lateinit var built: RecipeBuild
    val buildTime = measureTime { built = RecipeBuilder(TemplateFolder.shipped()).build(RecipeJson.read(file.readText())) }
    val document = built.document
    val id = document.character.id.value
    val out = options.out ?: File(options.root, "build/recipes/$id")
    out.mkdirs()
    val saved = File(out, "$id.json").apply { writeText(SquashFormat().plainText(document) + "\n") }
    built.warnings.forEach { System.err.println("recipe warning at $it") }
    println("recipe: built ${built.commands.size} commands in ${buildTime.inWholeMilliseconds} ms")
    println("character: ${saved.absolutePath}")
    if (options.buildOnly) return 0
    val errors = mutableListOf<String>()
    val look = LookArgs(options.lookArgs(document.character.wardrobe.shown?.value))
    val renders = File(out, "renders")
    val renderTime = measureTime { renderDocument(document, built.poses, look, errors, renders) }
    println("renders: ${renders.absolutePath} (${renderTime.inWholeMilliseconds} ms)")
    renders.listFiles { f -> f.extension == "png" }?.sortedBy { it.name }?.forEach { println("  ${it.absolutePath}") }
    if (options.measure) {
        val table = File(out, "measure.txt")
        val measureTime = measureTime { printInto(table) { measureDocument(id, document, built.poses, look, timings = false) } }
        println("measure: ${table.absolutePath} (${measureTime.inWholeMilliseconds} ms)")
    }
    options.reference?.let { reference -> ReferenceCompare(options, errors).compare(document, reference, File(out, "compare")) }
    errors.forEach { System.err.println("render error: $it") }
    return if (errors.isEmpty()) 0 else 1
}

private fun export(options: RecipeArgs): Int {
    val source = requireNotNull(options.export)
    if (!source.isFile) throw RecipeException("$", "no character file at ${source.absolutePath}")
    val exported = exportRecipe(SquashFormat().decode(source.readBytes()))
    val out = options.out ?: File(source.parentFile, source.name.removeSuffix(".json").removeSuffix(".squash") + ".recipe.json")
    out.parentFile?.mkdirs()
    out.writeText(RecipeJson.write(exported.recipe))
    exported.notCarried.forEach { System.err.println("not carried: $it") }
    println("recipe: ${out.absolutePath}")
    return 0
}

/** Runs [block] with what it prints to standard out written to [file] instead. */
private fun printInto(file: File, block: () -> Unit) {
    val console = System.out
    PrintStream(FileOutputStream(file), true).use { stream ->
        System.setOut(stream)
        try {
            block()
        } finally {
            System.setOut(console)
        }
    }
}
