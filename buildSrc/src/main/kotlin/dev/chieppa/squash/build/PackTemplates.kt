package dev.chieppa.squash.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Copies every file of the [templates] folder into [resources] under [root], with an [indexName] file listing their
 * relative paths: a jar cannot be listed, so the index is how the app finds what ships.
 */
@CacheableTask
abstract class PackTemplates : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templates: DirectoryProperty

    @get:Input
    abstract val root: Property<String>

    @get:Input
    abstract val indexName: Property<String>

    @get:OutputDirectory
    abstract val resources: DirectoryProperty

    @TaskAction
    fun pack() {
        val from = templates.get().asFile
        val to = resources.get().asFile.apply { deleteRecursively() }.resolve(root.get()).apply { mkdirs() }
        val files = from.walkTopDown().filter { it.isFile }.map { it.relativeTo(from).invariantSeparatorsPath }.sorted().toList()
        files.forEach { from.resolve(it).copyTo(to.resolve(it)) }
        to.resolve(indexName.get()).writeText(files.joinToString("\n", postfix = "\n"))
    }
}
