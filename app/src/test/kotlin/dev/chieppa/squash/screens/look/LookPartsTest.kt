package dev.chieppa.squash.screens.look

import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlin.test.Test
import kotlin.test.assertEquals

class LookPartsTest {
    private fun part(id: String, template: String, role: Role, on: String? = null) =
        PartInstance(PartId(id), TemplateRef(TemplateId(template), 1), on?.let { SocketRef(PartId(it), "end") }, role = role)

    private fun character(vararg parts: PartInstance) = Character(CharacterId("test"), "Test", BodyPlanRef(BodyPlanId("biped"), 1), parts.toList())

    @Test
    fun `a paw on an arm is a hand and a paw on a leg a foot`() {
        val parts = LookParts(
            character(
                part("torso", "torso", Role.BODY),
                part("arm-l", "tube", Role.ARM, "torso"),
                part("arm-paw", "paw", Role.FOOT, "arm-l"),
                part("leg-l", "leg", Role.LEG, "torso"),
                part("leg-paw", "paw", Role.FOOT, "leg-l"),
            ),
        )
        assertEquals(PartId("arm-paw"), parts.hand)
        assertEquals(PartId("leg-paw"), parts.foot)
    }

    @Test
    fun `a limb end on anything else keeps its own role`() {
        val parts = LookParts(character(part("torso", "torso", Role.BODY), part("hand-l", "hand", Role.HAND, "torso")))
        assertEquals(PartId("hand-l"), parts.hand)
        assertEquals(null, parts.foot)
    }
}
