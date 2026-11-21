package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.MouthLine
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.mouth.mouthStyleOf
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.TongueLevel
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far along the lower half the mouth's inside reaches from its root, and how far round its top either side, in u and v. */
private const val INSIDE_REACH = 0.85f
private const val INSIDE_ROUND = 0.12f

/** Where the tongue starts along the lower half, where its tip lies at rest and how much further a full tongue reaches, in u. */
private const val TONGUE_ROOT = 0.06f
private const val TONGUE_TIP = 0.45f
private const val TONGUE_REACH = 0.35f

/** How far round the top the tongue reaches either side, in v. */
private const val TONGUE_ROUND = 0.07f

/** Places round the tongue's tip. */
private const val TIP_STEPS = 8

/**
 * What the lower half of a mouth on a seam, a beak's, shows as its jaw drops: the inside of the mouth in [inner] and the tongue in
 * [tongue] on its top, the tongue reaching further out as [lift] rises. Only while [open] is above 0.
 */
data class JawMouth(val inner: SlotName?, val tongue: SlotName?, val open: Float, val lift: Float)

/**
 * The mouth [instance], a part of role jaw with a jaw joint and no mouth of its own, shows on its top: that of the nearest part it hangs from holding a
 * mouth whose style's line is a seam, while that mouth shows open; null otherwise.
 */
fun jawMouthOf(character: Character, instance: PartInstance, template: PartTemplate, templates: TemplateLibrary): JawMouth? {
    if ((instance.role ?: template.role) != Role.JAW) return null
    if (template.blocks.flatMap { it.joints() }.none { it.name == JAW_JOINT }) return null
    if (character.resolved(instance).surface.groups.any { it.isMouth }) return null
    val holder = generateSequence(instance.socket?.parent?.let(character::part)) { it.socket?.parent?.let(character::part) }
        .firstOrNull { part -> character.resolved(part).surface.groups.any { it.isMouth } } ?: return null
    val group = character.resolved(holder).surface.groups.first { it.isMouth }
    val style = templates.mouthStyleOf(holder, group)
    if (style.line != MouthLine.SEAM) return null
    val controls = style.scale.of(group.controls.copy(open = character.shownOpen(group)))
    if (controls.open <= 0f) return null
    val slot = { role: MarkRole -> group.roles.firstOrNull { it.role == role }?.fill }
    val tongue = slot(MarkRole.TONGUE).takeIf { group.tongueLevel != TongueLevel.NONE }
    return JawMouth(slot(MarkRole.INNER), tongue, controls.open, controls.tongue)
}

/** [mouth]'s inside and tongue on the top of [part]'s first patch, from its root along its length, in layer order. */
fun jawMouthShapes(part: PartId, mouth: JawMouth, surface: PartSurface): List<LandedShape> {
    val patch = surface.patches.firstOrNull()?.name ?: return emptyList()
    fun shape(slot: SlotName?, points: List<SurfaceUv>) = slot?.let { LandedShape(RANK_MOUTH, it, part, patch, ColorShape.Border(points, curved = true)) }
    val inside = listOf(SurfaceUv(0f, -INSIDE_ROUND), SurfaceUv(INSIDE_REACH, -INSIDE_ROUND / 2f), SurfaceUv(INSIDE_REACH, INSIDE_ROUND / 2f), SurfaceUv(0f, INSIDE_ROUND))
    val tip = TONGUE_TIP + TONGUE_REACH * mouth.lift
    val round = List(TIP_STEPS + 1) { i ->
        val angle = PI.toFloat() * i / TIP_STEPS - PI.toFloat() / 2f
        SurfaceUv(tip - TONGUE_ROUND + TONGUE_ROUND * cos(angle), TONGUE_ROUND * sin(angle))
    }
    val tongue = listOf(SurfaceUv(TONGUE_ROOT, -TONGUE_ROUND)) + round + SurfaceUv(TONGUE_ROOT, TONGUE_ROUND)
    return listOfNotNull(shape(mouth.inner, inside.map(::wrapped)), shape(mouth.tongue, tongue.map(::wrapped)))
}

private fun wrapped(uv: SurfaceUv) = SurfaceUv(uv.u, uv.v - kotlin.math.floor(uv.v))
