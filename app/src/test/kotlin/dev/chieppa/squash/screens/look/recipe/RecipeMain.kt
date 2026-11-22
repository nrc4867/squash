package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.RecipeJson
import dev.chieppa.squash.recipe.build.convertToFigure
import dev.chieppa.squash.recipe.build.figureOutlineJson
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.recipe.export.exportRecipe
import dev.chieppa.squash.screens.look.recipe.engine.EngineLease
import dev.chieppa.squash.screens.look.recipe.server.serve
import dev.chieppa.squash.screens.shell.SquashFormat
import dev.chieppa.squash.screens.smoke.FrameSnapshots
import java.io.File
import kotlin.system.exitProcess

/** What the command exits with when the recipe is wrong, apart from a render that failed. */
internal const val RECIPE_WRONG = 2

/**
 * Builds a recipe into a character and judges it, headless: writes the character file, renders its standard views and close-ups, prints
 * and keeps its measurements, and with a reference image renders it through the reference's camera beside it with a silhouette overlay
 * and IoU (see [RecipeRun]). With `watch=true` it builds again each time the recipe changes, until stopped (see [RecipeWatch]). With
 * `export=<character file>` it instead writes the character as a recipe, with `probe=<recipe>` answers frame questions about it (see
 * [probe]), with `figure=<recipe>` converts its body to a figure (see [figure]), and with `crop=<sheet>` splits a character sheet into its views (see [crop]). With `serve=true` it serves these builds to
 * clients on this machine (see [serve]). With `status=<file>` it writes its exit status there and exits 0, so Gradle reports no failure
 * over the recipe's own error. Run through `tools/recipe.sh`.
 */
fun main(args: Array<String>) {
    EngineLease.hold()
    val options = RecipeArgs(args)
    val code = when {
        options.serve -> serve(options)
        options.watch -> reportingRecipeErrors { FrameSnapshots.keep { watch(options) } }
        options.renders -> FrameSnapshots.keep { runCommand(options) }
        else -> runCommand(options)
    }
    options.status?.writeText("$code")
    exitProcess(if (options.status != null) 0 else code)
}

/** Runs the one build, export, probe or crop [options] asks for, its renders on the device [FrameSnapshots] keeps; its exit status. */
internal fun runCommand(options: RecipeArgs): Int = reportingRecipeErrors {
    when {
        options.export != null -> export(options)
        options.probe != null -> probe(options)
        options.crop != null -> crop(options)
        options.figure != null -> figure(options)
        else -> RecipeRun(options).use { it.run() }
    }
}

/** [block]'s exit status, or [RECIPE_WRONG] with the error printed when the recipe is wrong. */
internal fun reportingRecipeErrors(block: () -> Int): Int = try {
    block()
} catch (e: RecipeException) {
    System.err.println("recipe error at ${e.report}")
    RECIPE_WRONG
}

private fun watch(options: RecipeArgs): Int {
    RecipeRun(options).use { run ->
        run.readRecipe()
        FileChanges(run.file).use { changes -> RecipeWatch(run::readRecipe, run::run, changes::next, run.file.name).run() }
    }
    return 0
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

/**
 * Converts the recipe `figure=` names to a figure: its body's tubes traced at rest into a figure outline, written as `<id>.figure.json`,
 * and the recipe with those tubes fitted to it, as `<id>.figure.recipe.json` beside it or [RecipeArgs.out].
 */
private fun figure(options: RecipeArgs): Int {
    val source = requireNotNull(options.figure)
    if (!source.isFile) throw RecipeException("$", "no recipe file at ${source.absolutePath}")
    val recipe = RecipeJson.read(source.readText())
    val converted = convertToFigure(recipe, TemplateFolder.shipped())
    val out = options.out ?: File(source.parentFile, "${recipe.id}.figure.recipe.json")
    out.parentFile?.mkdirs()
    val outline = File(out.parentFile, "${recipe.id}.figure.json")
    outline.writeText(figureOutlineJson(converted.figure))
    out.writeText(RecipeJson.write(converted.recipe))
    println("figure: ${outline.absolutePath}")
    println("recipe: ${out.absolutePath}")
    return 0
}
