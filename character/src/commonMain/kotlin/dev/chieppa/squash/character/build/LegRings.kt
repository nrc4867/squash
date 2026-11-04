package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.geom.Vec3

/**
 * The surface a part of [template] resolving to [data] would build at full length, read ring by ring from its outlines and
 * parameters with nothing built, the way a built patch's surface is read.
 */
class LegRings(template: PartTemplate, data: PartData) {
    private val context = PartBuilder.context(template, data.params, data.outlines)
    private val cell = 1f / (template.topology.rings - 1)
    private val aroundCell = 1f / template.topology.segments

    /** The ring at [u], or null where no run reaches. */
    fun ringAt(u: Float): Slice? = context.ringAt(u)

    fun positionAt(u: Float, v: Float): Vec3? = ringAt(u)?.pointAt(v)

    /** The outward normal at [u] and [v], held off the ends as a patch's is. */
    fun normalAt(u: Float, v: Float): Vec3? {
        val held = u.coerceIn(cell, 1f - cell)
        val alongU = (positionAt(held + cell, v) ?: return null) - (positionAt(held - cell, v) ?: return null)
        val aroundV = (positionAt(held, v + aroundCell) ?: return null) - (positionAt(held, v - aroundCell) ?: return null)
        return (alongU cross aroundV).normalized()
    }
}
