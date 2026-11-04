package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.load.TemplateFolder
import java.nio.file.Path

/** The shipped templates and body plan, as the app loads them. */
val shipped: TemplateFolder by lazy { TemplateFolder.load(Path.of(System.getProperty("squash.templates"))) }

val caninePlan get() = shipped.plans.single()
