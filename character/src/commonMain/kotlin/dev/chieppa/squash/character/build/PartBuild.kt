package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.Patch

/**
 * A part's patches with the template they came from, null for a part missing from the document, and the fillet its template asks
 * for that is still to be applied to them, see [partBlendNode].
 */
data class PartBuild(val template: PartTemplate?, val patches: List<Patch>, val blend: BlendSpec? = null)
