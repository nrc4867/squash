package dev.chieppa.squash.screens

import dev.chieppa.stretch.document.DocumentStorage
import java.nio.file.Files
import java.nio.file.Path

/** Files read and written on the caller's own thread, so a save or an open finishes inside the work a test runs. */
object DirectStorage : DocumentStorage {
    override suspend fun read(path: String): ByteArray = Files.readAllBytes(Path.of(path))

    override suspend fun write(path: String, bytes: ByteArray) {
        val target = Path.of(path).toAbsolutePath()
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }

    override suspend fun exists(path: String): Boolean = Files.isRegularFile(Path.of(path))

    override fun displayName(path: String): String = Path.of(path).fileName.toString().substringBeforeLast('.')
}
