package dev.chieppa.squash.screens.look.recipe

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY

/** Writes to one [file], as the file system reports them: [next] blocks until the next one, with no polling. */
class FileChanges(private val file: File) : AutoCloseable {
    private val service = FileSystems.getDefault().newWatchService()

    init {
        file.absoluteFile.parentFile.toPath().register(service, ENTRY_CREATE, ENTRY_MODIFY)
    }

    /** Waits for the file to be written or replaced; false when the watch was closed. */
    fun next(): Boolean {
        while (true) {
            val key = try {
                service.take()
            } catch (_: java.nio.file.ClosedWatchServiceException) {
                return false
            }
            val written = key.pollEvents().any { (it.context() as? Path)?.fileName?.toString() == file.name }
            if (!key.reset()) return false
            if (written) return true
        }
    }

    override fun close() = service.close()
}
