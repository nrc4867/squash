package dev.chieppa.squash.character

import dev.chieppa.squash.character.load.TemplateFolder
import java.nio.file.Path

/** The templates folder of this checkout, loaded once. */
val shipped: TemplateFolder by lazy { TemplateFolder.load(Path.of(System.getProperty("squash.templates"))) }
