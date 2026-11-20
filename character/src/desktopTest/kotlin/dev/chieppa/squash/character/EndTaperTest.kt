package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.EndTaper
import dev.chieppa.squash.character.surface.RunSolids
import dev.chieppa.squash.character.surface.fieldOf
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A leg narrows into a paw whose wrist is thinner than the leg's end, and keeps its own end where the paw's wrist is as wide. */
class EndTaperTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val plan = BodyPlanRef(BodyPlanId("canine-quadruped"), 1)
    private val torso = PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 1))
    private val leg = PartInstance(PartId("leg"), TemplateRef(TemplateId("leg"), 1), SocketRef(torso.id, "front-leg"))
    private val paw = PartInstance(PartId("paw"), TemplateRef(TemplateId("paw"), 9), SocketRef(leg.id, "paw"))

    private fun built(ankle: Float): Pair<AssembledCharacter, CharacterField> {
        val character = AddParts(listOf(torso, leg, paw)).apply(SquashDocument(Character(CharacterId("c"), "Fennec", plan))).document.character
        val data = { id: PartId ->
            val held = character.resolved(id) ?: PartData()
            if (id == paw.id) held.copy(params = held.params + ("ankle" to ankle)) else held
        }
        val body = assembleNow(assemblyParts(character), data, library.plan(plan), library)
        return body to assertNotNull(fieldOf(body, RunSolids()))
    }

    private fun CharacterField.run(part: PartInstance) = runs.indexOfFirst { it.partId == part.id && it.first }

    @Test
    fun aLegNarrowsIntoAThinnerWrist() {
        val (body, field) = built(ankle = 0.05f)
        val taper = assertNotNull(field.tapers[field.run(leg)], "the leg ends in the paw's thinner wrist")
        assertTrue(taper.section < taper.radius, "narrower, ${taper.section} against ${taper.radius}")
        val placed = assertNotNull(body.part(leg.id))
        val end = assertNotNull(placed.middleAt(0.97f))
        val out = assertNotNull(placed.pointOn(0.97f, 0.25f)) - end
        val member = field.field.members[field.run(leg)]
        val surface = (1..40).map { it / 40f }.first { member.own(end + out * it) >= 0f }
        assertTrue(surface < 0.98f, "the leg's own surface comes in near its end, at $surface of the drawn radius")
        assertEquals(1f, taper.scale(0, Float.MAX_VALUE), "the leg is untouched far from its end")
    }

    @Test
    fun aLegKeepsItsEndOnAWristAsWide() {
        val (_, field) = built(ankle = 0.3f)
        assertNull(field.tapers[field.run(leg)])
    }

    @Test
    fun aTaperEasesIn() {
        val taper = EndTaper(ring = 10, radius = 0.1f, section = 0.05f)
        assertEquals(0.5f, taper.scale(12, 0f), 1e-6f)
        assertEquals(0.5f, taper.scale(10, 0f), 1e-6f)
        val half = taper.scale(9, 0.125f)
        assertTrue(half > 0.5f && half < 1f, "between, $half")
        assertEquals(1f, taper.scale(5, 0.25f), 1e-6f)
    }
}
