package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.RecipeJson
import dev.chieppa.squash.recipe.export.exportRecipe
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
 * [probe]), and with `crop=<sheet>` splits a character sheet into its views (see [crop]). With `status=<file>` it writes its exit status
 * there and exits 0, so Gradle reports no failure over the recipe's own error. Run through `tools/recipe.sh`.
 */
fun main(args: Array<String>) {
    val options = RecipeArgs(args)
    val code = try {
        when {
            options.export != null -> export(options)
            options.probe != null -> probe(options)
            options.crop != null -> crop(options)
            options.watch -> FrameSnapshots.keep { watch(options) }
            options.buildOnly -> RecipeRun(options).use { it.run() }
            else -> FrameSnapshots.keep { RecipeRun(options).use { it.run() } }
        }
    } catch (e: RecipeException) {
        System.err.println("recipe error at ${e.report}")
        RECIPE_WRONG
    }
    options.status?.writeText("$code")
    exitProcess(if (options.status != null) 0 else code)
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
