package dev.chieppa.squash.recipe

/**
 * What is wrong with a recipe, in recipe terms: [path] is where in the recipe file, as a JSON path from `$`, and the message says what
 * is wrong there.
 */
class RecipeException(val path: String, message: String) : Exception(message) {
    /** The error as one line: its path and its message. */
    val report: String get() = "$path: $message"
}
