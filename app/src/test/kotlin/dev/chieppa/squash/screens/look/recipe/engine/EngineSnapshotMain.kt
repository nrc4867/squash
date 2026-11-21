package dev.chieppa.squash.screens.look.recipe.engine

import java.io.File
import java.nio.file.Path
import kotlin.io.path.readLines

/** The class the snapshot's argument file starts. */
private const val RECIPE_MAIN = "dev.chieppa.squash.screens.look.recipe.RecipeMainKt"

/**
 * Snapshots the recipe engine's classpath and points the checkout at it, for `:app:recipeLauncher`. Arguments, `key=value`:
 * `classpath=<file>` (one entry a line, in order), `engines=<folder>`, `pointer=<file>`, `java=<path>`, `templates=<folder>`,
 * `root=<checkout>` and `immutable=<folder>` (a folder whose files are used in place), given once or more.
 */
fun main(args: Array<String>) {
    val pairs = args.map { it.substringBefore('=') to it.substringAfter('=') }
    fun one(key: String) = requireNotNull(pairs.lastOrNull { it.first == key }?.second) { "give $key=" }
    val pointer = Path.of(one("pointer"))
    val snapshot = EngineSnapshot(Path.of(one("engines")), pointer, pairs.filter { it.first == "immutable" }.map { Path.of(it.second) })
    val classpath = Path.of(one("classpath")).readLines().filter { it.isNotBlank() }.map(Path::of)
    val version = snapshot.take(classpath, one("java")) { folder, entries ->
        launchArgs(folder, entries, pointer, Path.of(one("templates")), Path.of(one("root")))
    }
    snapshot.prune()
    println("recipe engine: $version")
}

/** The argument file's lines that start the recipe command from the snapshot in [folder] on [classpath]. */
internal fun launchArgs(folder: Path, classpath: List<Path>, pointer: Path, templates: Path, root: Path): List<String> = listOf(
    "--enable-native-access=ALL-UNNAMED",
    quoted("-Dsquash.templates=$templates"),
    quoted("-D$ENGINE_PROPERTY=$folder"),
    quoted("-D$POINTER_PROPERTY=$pointer"),
    "-cp",
    quoted(classpath.joinToString(File.pathSeparator) { it.toString() }),
    RECIPE_MAIN,
    quoted("root=$root"),
)

private fun quoted(text: String) = "\"" + text.replace(File.separatorChar, '/') + "\""
