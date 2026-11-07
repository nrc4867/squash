package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.newest
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Sockets placed by hand on a part: one command each way, every template offered, mirrored in pairs. See HLD: data-model, Hand-made sockets. */
class HandSocketsTest {
    private val plan = shipped.plans.first()
    private val library = TemplateLibrary(shipped.parts, shipped.plans)

    private fun rooted(): Pair<SquashDocument, PartId> {
        val new = newCharacter(plan, shipped, "Fennec")
        return new.commands.fold(new.document) { document, command -> command.apply(document).document } to new.root
    }

    private fun SquashDocument.handOpen() = openSockets(this, plan, shipped).filter { HandSockets.idOf(it.name) != null }

    @Test
    fun aSocketPlacedByHandIsOneStepThatTakesEveryTemplateAndUndoes() {
        val (document, torso) = rooted()
        val applied = HandSockets.add(torso, 1, 0.75f, 0.2f, 0f, paired = true).apply(document)
        val socket = applied.document.handOpen().single()
        assertEquals(HandSockets.name(1), socket.name)
        assertEquals(shipped.newest.map { it.id }, socket.takes)
        assertTrue(socket.paired)
        assertEquals(document, applied.undo.apply(applied.document).document)
    }

    @Test
    fun aPairedSocketIsTwoMirroredAcrossTheBodysMiddle() {
        val (document, torso) = rooted()
        val placed = HandSockets.add(torso, 1, 0.75f, 0.2f, 0f, paired = true).apply(document).document
        val dots = assemble(placed).sockets.filter { it.ref.socket == HandSockets.name(1) }
        assertEquals(listOf(Side.LEFT, Side.RIGHT), dots.map { it.side })
        val (left, right) = dots.map { it.position }
        assertEquals(left.x, right.x, 1e-4f)
        assertEquals(left.y, right.y, 1e-4f)
        assertEquals(left.z, -right.z, 1e-4f)
        assertTrue(left.z < 0f, "the stored v reads as the left side's")
    }

    @Test
    fun aWingPairOnItIsOneStepAndPlacesBothSides() {
        val (document, torso) = rooted()
        val placed = HandSockets.add(torso, 1, 0.75f, 0.2f, 0f, paired = true).apply(document).document
        val socket = placed.handOpen().single()
        val wing = assertNotNull(shipped.template(TemplateId("wing-feathered")))
        val parts = placeOnSocket(socket, wing, placed, roles = shipped.roles(placed.character))
        assertEquals(listOf(Side.LEFT, Side.RIGHT), parts.map { it.side })
        assertTrue(parts.all { it.role == Role.WING })
        val winged = AddParts(parts).apply(placed).document
        assertEquals(emptyList(), winged.handOpen())
        val (left, right) = parts.map { assertNotNull(assemble(winged).part(it.id)).origin }
        assertEquals(left.z, -right.z, 1e-4f)
    }

    @Test
    fun deletingClearsItsParameters() {
        val (document, torso) = rooted()
        val placed = HandSockets.add(torso, 1, 0.75f, 0.2f, 0f, paired = false).apply(document).document
        val deleted = HandSockets.delete(torso, 1).apply(placed).document
        assertEquals(emptyList(), deleted.handOpen())
        assertEquals(document.character.part(torso), deleted.character.part(torso))
    }

    @Test
    fun theNextSocketTakesTheNextNumber() {
        val (document, torso) = rooted()
        val one = HandSockets.add(torso, 1, 0.5f, 0.2f, 0f, paired = false).apply(document).document
        val params = one.character.resolvedShape(assertNotNull(one.character.part(torso))).params
        assertEquals(2, HandSockets.nextId(params))
    }

    @Test
    fun itSurvivesATemplateVersionChangeOfItsPart() {
        val (document, torso) = rooted()
        val placed = HandSockets.add(torso, 1, 0.75f, 0.2f, 15f, paired = true).apply(document).document
        val part = assertNotNull(placed.character.part(torso))
        assertEquals(2, part.template.version)
        val older = placed.copy(character = placed.character.copy(parts = placed.character.parts.map { if (it.id == torso) it.copy(template = TemplateRef(it.template.id, 1)) else it }))
        assertEquals(placed.handOpen(), older.handOpen())
        assertEquals(2, assemble(older).sockets.count { it.ref.socket == HandSockets.name(1) })
    }

    private fun assemble(document: SquashDocument) =
        assembleNow(assemblyParts(document.character), { id -> document.character.resolved(assertNotNull(document.character.part(id))) }, plan, library)
}
