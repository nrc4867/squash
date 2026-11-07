package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.load.TemplateFolder
import java.nio.file.Path

/** The shipped templates and body plans, as the app loads them. */
val shipped: TemplateFolder by lazy { TemplateFolder.load(Path.of(System.getProperty("squash.templates"))) }

val caninePlan get() = shipped.plans.first { it.id.value == "canine-quadruped" }

/** The plan with one tube and nothing else. */
val blankPlan get() = shipped.plans.first { it.id.value == "blank" }
