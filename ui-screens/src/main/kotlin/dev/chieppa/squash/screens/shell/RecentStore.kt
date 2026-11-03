package dev.chieppa.squash.screens.shell

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

/** Keeps the recent files between runs: one path a line in [file]. */
class RecentStore(private val file: Path) {
    fun load(): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    fun save(entries: List<String>) {
        file.parent?.let { Files.createDirectories(it) }
        file.writeLines(entries)
    }
}
