package dev.chieppa.squash.character.load

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

private const val PART_SUFFIX = ".part.json"
private const val PLAN_SUFFIX = ".plan.json"
private const val PLANS_FOLDER = "plans"

/** Where the build packs the templates folder on the classpath, and the file there naming what it holds. */
private const val SHIPPED_ROOT = "dev/chieppa/squash/templates"
private const val SHIPPED_INDEX = "index.txt"

/** Everything in a templates folder. */
data class TemplateFolder(val parts: List<PartTemplate>, val plans: List<BodyPlan>) {
    companion object {
        /**
         * Loads every `*.part.json` in [folder] and every `*.plan.json` in its plans folder, in name order, except that a plan whose
         * root holds no sockets, as Blank's does, comes after those that place parts.
         */
        fun load(folder: Path): TemplateFolder {
            val parts = folder.listDirectoryEntries().map { it.name }
            val plans = folder.resolve(PLANS_FOLDER).listDirectoryEntries().map { "$PLANS_FOLDER/${it.name}" }
            return load(parts + plans) { folder.resolve(it).readText() }
        }

        /** Loads the templates packed into the app's resources, by the same rules as a folder. */
        fun shipped(): TemplateFolder = load(shippedResource(SHIPPED_INDEX).lines().filter { it.isNotEmpty() }, ::shippedResource)

        private fun load(files: List<String>, read: (String) -> String): TemplateFolder {
            val parts = files.filter { '/' !in it && it.endsWith(PART_SUFFIX) }.sorted()
                .map { TemplateLoader.loadPart(it, read(it)) }
            val plans = files.filter { it.startsWith("$PLANS_FOLDER/") && it.endsWith(PLAN_SUFFIX) }.sorted()
                .map { TemplateLoader.loadPlan(it.substringAfterLast('/'), read(it), parts) }
                .sortedBy { it.sockets.isEmpty() }
            return TemplateFolder(parts, plans)
        }

        private fun shippedResource(name: String): String {
            val stream = checkNotNull(TemplateFolder::class.java.classLoader.getResourceAsStream("$SHIPPED_ROOT/$name")) {
                "No shipped template resource $name"
            }
            return stream.use { it.readBytes().decodeToString() }
        }
    }
}
