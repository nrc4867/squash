package dev.chieppa.squash.screens.biped

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef

val BIPED_TORSO = PartId("torso")
val BIPED_ARM_L = PartId("arm-l")
val BIPED_ARM_R = PartId("arm-r")
val BIPED_LEG_L = PartId("leg-l")
val BIPED_LEG_R = PartId("leg-r")
val BIPED_HAND_L = PartId("hand-l")
val BIPED_HAND_R = PartId("hand-r")
val BIPED_FOOT_L = PartId("foot-l")
val BIPED_FOOT_R = PartId("foot-r")

/** How far the biped plan spreads the arms out from the body, in degrees. */
private const val ARM_SPREAD = 45f

private fun ref(id: String, version: Int = 1) = TemplateRef(TemplateId(id), version)

/** A pair placed as Add part places it on a paired socket of the biped plan, each side on its own parent. */
private fun pair(id: String, template: TemplateRef, role: Role, left: Pair<PartId, PartId>, right: Pair<PartId, PartId>, socket: String, spread: Float = 0f) = AddParts(
    listOf(
        PartInstance(left.first, template, SocketRef(left.second, socket), pair = PairId(id), side = Side.LEFT, role = role),
        PartInstance(right.first, template, SocketRef(right.second, socket), pair = PairId(id), side = Side.RIGHT, role = role),
    ),
    pairs = if (spread == 0f) emptyMap() else mapOf(PairId(id) to PartData(params = mapOf(PlacementParams.OUTWARD to spread))),
)

/**
 * An upright torso on the biped plan with arms, legs, five-fingered hands with webs between the fingers and shoes for feet, every
 * part placed as Add part places it and none drawn: the smallest character whose hands end its arms and whose feet end its legs.
 */
fun undrawnBiped(): SquashDocument = listOf(
    AddParts(listOf(PartInstance(BIPED_TORSO, ref("torso-upright"), role = Role.BODY))),
    pair("arms", ref("arm"), Role.ARM, BIPED_ARM_L to BIPED_TORSO, BIPED_ARM_R to BIPED_TORSO, "shoulder", ARM_SPREAD),
    pair("legs", ref("leg"), Role.LEG, BIPED_LEG_L to BIPED_TORSO, BIPED_LEG_R to BIPED_TORSO, "hip"),
    pair("hands", ref("hand", 3), Role.HAND, BIPED_HAND_L to BIPED_ARM_L, BIPED_HAND_R to BIPED_ARM_R, "hand"),
    pair("feet", ref("foot", 2), Role.FOOT, BIPED_FOOT_L to BIPED_LEG_L, BIPED_FOOT_R to BIPED_LEG_R, "paw"),
).fold(SquashDocument(Character(CharacterId("biped"), "Biped", BodyPlanRef(BodyPlanId("biped"), 1)))) { held, command -> command.apply(held).document }
