package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.surface.CharacterField
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
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.CarvedSolid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A cloven hoof's soles are dished inside their walls, each toe its own hollow. See HLD: surface, Soles. */
class HoofSoleTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val plan = BodyPlanRef(BodyPlanId("canine-quadruped"), 1)
    private val torso = PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 1))
    private val leg = PartInstance(PartId("leg"), TemplateRef(TemplateId("leg"), 1), SocketRef(torso.id, "front-leg"))
    private val hoof = PartInstance(PartId("hoof"), TemplateRef(TemplateId("hoof"), 1), SocketRef(leg.id, "paw"))

    private val built: AssembledCharacter by lazy {
        val added = AddParts(listOf(torso, leg, hoof)).apply(SquashDocument(Character(CharacterId("c"), "Goat", plan))).document
        val character = SetParameter(hoof.id, "toes", 2f).apply(added).document.character
        assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, library.plan(plan), library)
    }
    private val field: CharacterField by lazy { assertNotNull(fieldOf(built, RunSolids())) }

    @Test
    fun eachToeHasItsOwnHollowInsideItsWall() {
        val part = assertNotNull(built.part(hoof.id))
        val along = PartBuilder.axes(part.template).along
        val toes = field.runs.indices.filter { field.runs[it].partId == hoof.id && field.runs[it].patch.name.startsWith("hooves:") }
        assertEquals(2, toes.size)
        for (i in toes) {
            val carved = assertNotNull(field.field.members[i].solid as? CarvedSolid, "toe $i is dished")
            val patch = field.runs[i].patch
            val rings = List(patch.nu) { ring -> List(patch.nv) { patch.point(patch.index(ring, it)) } }.filter { ring -> ring.any { (it - ring.first()).length > 1e-5f } }
            val level = rings.flatten().maxOf { it dot along }
            val sole = rings.last().map { it - along * ((it dot along) - level) }
            val middle = sole.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / sole.size)
            val width = sole.maxOf { (it - middle).length }
            val inHollow = middle - along * (width * 0.05f)
            assertTrue(carved.solid.distance(inHollow) < 0f, "the toe's own solid fills its sole's middle")
            assertTrue(carved.distance(inHollow) > 0f, "the hollow empties it")
            val inWall = sole.maxBy { (it - middle).length }.let { edge -> middle + (edge - middle) * 0.85f - along * (width * 0.05f) }
            assertTrue(carved.distance(inWall) < 0f, "the wall stands round it: ${carved.solid.distance(inWall)} uncarved, ${carved.distance(inWall)} carved")
        }
    }
}
