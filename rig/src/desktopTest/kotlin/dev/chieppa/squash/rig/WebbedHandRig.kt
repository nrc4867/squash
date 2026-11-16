package dev.chieppa.squash.rig

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

/** The hand template whose fingers stand in a toe row with a web between each pair of neighbours. */
private val WEBBED_HAND = TemplateRef(TemplateId("hand"), 3)

/** How far the biped plan spreads the arms out from the body, in degrees. */
private const val ARM_SPREAD = 45f

private val TORSO = PartId("torso")

/** A pair placed as Add part places it on a paired socket of the biped plan, each side on its own parent. */
private fun pair(id: String, template: TemplateRef, role: Role, left: Pair<PartId, PartId>, right: Pair<PartId, PartId>, socket: String, spread: Float = 0f) = AddParts(
    listOf(
        PartInstance(left.first, template, SocketRef(left.second, socket), pair = PairId(id), side = Side.LEFT, role = role),
        PartInstance(right.first, template, SocketRef(right.second, socket), pair = PairId(id), side = Side.RIGHT, role = role),
    ),
    pairs = if (spread == 0f) emptyMap() else mapOf(PairId(id) to PartData(params = mapOf(PlacementParams.OUTWARD to spread))),
)

/** An undrawn upright torso on the biped plan with a pair of arms and, on them, a pair of webbed hands at the template's own shape, rigged once. */
val webbedHandRig: Rig by lazy {
    val armL = PartId("arm-l")
    val armR = PartId("arm-r")
    val document = listOf(
        AddParts(listOf(PartInstance(TORSO, TemplateRef(TemplateId("torso-upright"), 1), role = Role.BODY))),
        pair("arms", TemplateRef(TemplateId("arm"), 1), Role.ARM, armL to TORSO, armR to TORSO, "shoulder", ARM_SPREAD),
        pair("hands", WEBBED_HAND, Role.HAND, PartId("hand-l") to armL, PartId("hand-r") to armR, "hand"),
    ).fold(SquashDocument(Character(CharacterId("hands"), "Hands", BodyPlanRef(BodyPlanId("biped"), 1)))) { held, command -> command.apply(held).document }
    settledRig(document, "the webbed hands")
}
