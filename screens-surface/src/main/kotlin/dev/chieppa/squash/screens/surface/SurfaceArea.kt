package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.partColorInput
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character

private const val PATCH_SEPARATOR = ":"

/** The name of the template area [anchor] lies in, the last named there as it overdraws the rest, or null outside them all. */
fun areaAt(character: Character, templates: TemplateLibrary, anchor: Anchor): String? =
    partColorInput(character, anchor.part, templates).areas.lastOrNull { area ->
        val onPatch = anchor.patch == area.patch || anchor.patch.startsWith(area.patch + PATCH_SEPARATOR)
        val around = anchor.v in area.v.start..area.vEnd || anchor.v + 1f in area.v.start..area.vEnd
        onPatch && around && anchor.u in area.u.start..area.u.end
    }?.name
