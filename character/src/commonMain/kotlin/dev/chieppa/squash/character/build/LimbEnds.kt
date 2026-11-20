package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.geom.OutlineProfile

/** The share of a limb's length back from its end where its end's thickness is first read, before its closing cap. */
private const val FIRST_READ = 0.05f

/**
 * What a part that ends a limb reads from the limb: the radius of the limb's end, so a foot's ankle or a hand's wrist is as thick as
 * the leg or arm it sits on and the two meet with no step. See HLD: parts, Feet and Hands.
 */
object LimbEnds {
    /** The parameter a template names to take the radius of the limb end it sits on, as a fraction of its own axis length. */
    const val LIMB_PARAM = "limb"

    /**
     * [data], the shape of [part] in [character] as it builds, with [LIMB_PARAM] set from the limb it sits on where the template
     * names it and the part leaves it unset; [data] itself otherwise.
     */
    fun withLimb(character: Character, part: PartId, templates: TemplateLibrary, data: PartData): PartData {
        val instance = character.part(part) ?: return data
        val own = templates.find(character.wornPart(instance).template) ?: return data
        if (LIMB_PARAM !in own.params || LIMB_PARAM in data.params) return data
        val parentId = instance.socket?.parent ?: return data
        val radius = endRadius(character, parentId, templates) ?: return data
        val size = data.params[SIZE_PARAM] ?: own.defaults[SIZE_PARAM] ?: 1f
        val share = radius / (size * own.axis.length)
        return data.copy(params = data.params + (LIMB_PARAM to share))
    }

    /** The radius of [parent]'s end, one radius back from where its outline closes; null where it cannot be read. */
    fun endRadius(character: Character, parent: PartId, templates: TemplateLibrary): Float? {
        val instance = character.part(parent) ?: return null
        val worn = character.wornPart(instance)
        val template = templates.find(worn.template)?.asBuilt(worn.data.params, onSocket = instance.socket != null) ?: return null
        val values = template.defaults + worn.data.params
        val size = values[SIZE_PARAM] ?: 1f
        val drawn = DrawnViews(template, worn.data.outlines, PartBuilder.axes(template), size)
        val (height, width, scale) = if (drawn.heightProfile != null || drawn.widthProfile != null) {
            Triple(drawn.heightProfile, drawn.widthProfile, size)
        } else {
            val undrawn = UndrawnSilhouettes.of(PartBuilder.context(template, worn.data.params))
            Triple(undrawn.height, undrawn.width, 1f)
        }
        return endRadius(height, width, scale)
    }

    /**
     * The radius of the end of a run whose Side and Top are [height] and [width], times [scale]: half its mean thickness one radius back
     * from its far end, so a rounded closing cap reads at its widest and a square end at its edge.
     */
    fun endRadius(height: OutlineProfile?, width: OutlineProfile?, scale: Float): Float? {
        val ruler = height ?: width ?: return null
        fun thickness(x: Float): Float {
            val extents = listOfNotNull(height?.extentAt(x), width?.extentAt(x))
            return extents.average().toFloat() * scale
        }
        val end = ruler.end
        val first = thickness(end - (end - ruler.start) * FIRST_READ) / 2f
        if (first <= 0f) return null
        val radius = thickness(end - first / scale) / 2f
        return radius.takeIf { it > 0f }
    }
}
