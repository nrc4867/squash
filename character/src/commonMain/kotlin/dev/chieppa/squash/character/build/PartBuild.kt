package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.Patch

/** A part's patches with the template they came from, null for a part missing from the document. */
data class PartBuild(val template: PartTemplate?, val patches: List<Patch>)
