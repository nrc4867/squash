package dev.chieppa.squash.schemagen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** Writes every schema file into the folder named by the first argument. */
fun main(args: Array<String>) {
    val folder = Path.of(args.single())
    Files.createDirectories(folder)
    Schemas.render().forEach { (name, text) -> folder.resolve(name).writeText(text) }
}
