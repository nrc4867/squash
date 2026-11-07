package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SectionSpec
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.SectionProfile
import kotlin.math.roundToInt

private const val PREFIX = "generic."
private const val FLAG_ON = 1f
private const val FLAG_OFF = 0f

/**
 * What a part of a generic template chooses for itself, held in its parameters so pairs, overrides, undo and saving treat them like
 * any other: `generic.section`, the place of its profile in [PROFILES]; `generic.view.side`, `.top` and `.front`, 1 where that view
 * sets it; `generic.rings` and `generic.segments`. A part that has set none builds as its template says. See HLD: parts, Blank mode.
 */
object GenericParams {
    const val SECTION = "${PREFIX}section"
    const val RINGS = "${PREFIX}rings"
    const val SEGMENTS = "${PREFIX}segments"

    /** The section profiles a generic part chooses between, in the order the inspector shows them. */
    val PROFILES: List<SectionProfile> = SectionProfile.entries

    /** What each view sets on a generic part while it is on. */
    val READINGS: Map<View, Sets> = mapOf(View.SIDE to Sets.HEIGHT, View.TOP to Sets.WIDTH, View.FRONT to Sets.SECTION)

    /** Set while [view] sets the part. */
    fun view(view: View) = "${PREFIX}view.${view.name.lowercase()}"

    fun isGeneric(name: String) = name.startsWith(PREFIX)

    /** The command that gives [part] the section [profile]: one undo step. */
    fun section(part: PartId, profile: SectionProfile, thisSide: Boolean = false) =
        SetParameter(part, SECTION, PROFILES.indexOf(profile).toFloat(), thisSide)

    /** The command that turns [view] on or off for [part]: one undo step. */
    fun showView(part: PartId, view: View, on: Boolean, thisSide: Boolean = false) =
        SetParameter(part, view(view), if (on) FLAG_ON else FLAG_OFF, thisSide)

    /** The command that gives [part] [rings] rings: one undo step. */
    fun rings(part: PartId, rings: Int, thisSide: Boolean = false) = SetParameter(part, RINGS, rings.toFloat(), thisSide)

    /** The command that gives [part] [segments] control points round its section: one undo step. */
    fun segments(part: PartId, segments: Int, thisSide: Boolean = false) = SetParameter(part, SEGMENTS, segments.toFloat(), thisSide)
}

/** Whether parts of this template choose their own section, views, rings and segments. */
val PartTemplate.isGeneric: Boolean get() = generic != null

/**
 * This template as a part with [params] builds it: for a generic template, with the section, views, rings and segments the part has
 * chosen, each kept within the template's limits, and at least one view on. Any other template is returned as it is.
 */
fun PartTemplate.shapedBy(params: Map<String, Float>): PartTemplate {
    val generic = generic ?: return this
    if (params.keys.none(GenericParams::isGeneric)) return this
    val profile = params[GenericParams.SECTION]?.roundToInt()?.let(GenericParams.PROFILES::getOrNull)
    val rings = params[GenericParams.RINGS]?.roundToInt()?.coerceIn(generic.rings.min, generic.rings.max) ?: topology.rings
    val segments = params[GenericParams.SEGMENTS]?.roundToInt()?.coerceIn(generic.segments.min, generic.segments.max) ?: topology.segments
    val chosen = GenericParams.READINGS.filter { (view, _) -> params[GenericParams.view(view)]?.let { it != 0f } ?: (view in views) }
    return copy(
        section = profile?.let(SectionSpec::Named) ?: section,
        views = chosen.ifEmpty { views },
        topology = topology.copy(rings = rings, segments = segments),
    )
}

/** The profile [template]'s parts start with, or null where its section is given by control points. */
fun PartTemplate.sectionProfile(): SectionProfile? = (section as? SectionSpec.Named)?.profile
