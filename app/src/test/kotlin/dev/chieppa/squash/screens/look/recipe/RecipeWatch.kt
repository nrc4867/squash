package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.recipe.RecipeException
import kotlin.time.measureTime

/**
 * Builds a recipe again each time its text changes, in one process, so every build after the first runs warm. [read] gives the recipe's
 * text, [build] builds it and gives its exit status, and [changed] waits for the file to change, false to stop. A text equal to the last
 * one built is not built again, so an editor's several writes of one save build once; a text that does not read as a recipe prints why and
 * waits for the next change.
 */
class RecipeWatch(private val read: () -> String, private val build: (String) -> Int, private val changed: () -> Boolean, private val name: String) {
    /** How many builds ran. */
    var builds = 0
        private set

    fun run() {
        var last: String? = null
        do {
            val text = try {
                read()
            } catch (e: RecipeException) {
                System.err.println("recipe error at ${e.report}")
                continue
            }
            if (text == last) continue
            last = text
            builds++
            var code = 0
            val took = measureTime {
                code = try {
                    build(text)
                } catch (e: RecipeException) {
                    System.err.println("recipe error at ${e.report}")
                    RECIPE_WRONG
                } catch (e: Exception) {
                    System.err.println("build failed: $e")
                    1
                }
            }
            println("watch: build $builds ${if (code == 0) "done" else "exited $code"} in ${took.inWholeMilliseconds} ms; waiting for $name to change (Ctrl-C stops)")
        } while (changed())
    }
}
