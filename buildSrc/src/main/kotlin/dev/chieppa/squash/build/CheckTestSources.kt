package dev.chieppa.squash.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** What a test may not write: waits on the wall clock, and reflection that widens visibility. */
private val FORBIDDEN = listOf("""\bThread\.sleep\(""", """\bdelay\(""", """\bwaitUntil\w*\(""", """\bsetAccessible\b""", """\bisAccessible\s*=\s*true""").map(::Regex)

/**
 * Fails when a test source in [sources] waits on the wall clock or widens visibility through reflection, naming each file and
 * line. Tests run queued work themselves and wait on a hook instead.
 */
@CacheableTask
abstract class CheckTestSources : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** What the report's paths are relative to. */
    @get:Internal
    abstract val root: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val root = root.get().asFile
        val found = sources.asFileTree.files.filter { it.extension == "kt" }.sorted().flatMap { file ->
            file.readLines().withIndex().mapNotNull { (index, line) ->
                FORBIDDEN.firstOrNull { it.containsMatchIn(line) }?.let { "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" }
            }
        }
        report.get().asFile.writeText(found.joinToString("\n", postfix = "\n"))
        if (found.isNotEmpty()) throw GradleException("Tests may not wait on the clock or widen visibility:\n" + found.joinToString("\n"))
    }
}
