package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.model.PartTemplate

/** How many joints the template's blocks hold. */
fun PartTemplate.jointCount(): Int = blocks.sumOf { it.joints().size }
