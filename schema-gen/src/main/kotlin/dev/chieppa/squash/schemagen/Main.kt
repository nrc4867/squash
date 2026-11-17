package dev.chieppa.squash.schemagen

import dev.chieppa.squash.schemagen.reference.Reference
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** Writes every schema file into the folder named by the first argument, and the recipe reference into the folder named by the second. */
fun main(args: Array<String>) {
    write(Path.of(args[0]), Schemas.render())
    write(Path.of(args[1]), Reference.render())
}

private fun write(folder: Path, files: Map<String, String>) {
    Files.createDirectories(folder)
    files.forEach { (name, text) -> folder.resolve(name).writeText(text) }
}
