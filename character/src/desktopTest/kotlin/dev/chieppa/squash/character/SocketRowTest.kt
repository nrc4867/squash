package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnRow
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val COUNT = 5
private const val FROM = 0.8f
private const val TO = 0.2f
private const val FIRST = 1.2f
private const val FALLOFF = 0.5f

/** A hand-made row: sockets evenly spaced along a part, each sizing what Add part places on it, filled in one step. */
class SocketRowTest {
    private val plan = shipped.plans.first()
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val spike = assertNotNull(shipped.template(TemplateId("spike")))

    private fun rowed(): Pair<SquashDocument, PartId> {
        val new = newCharacter(plan, shipped, "Dragon")
        val document = new.commands.fold(new.document) { document, command -> command.apply(document).document }
        return HandSockets.addRow(new.root, 1, FROM, TO, 0f, 0f, false, COUNT, FIRST, FALLOFF).apply(document).document to new.root
    }

    @Test
    fun aRowIsEvenlySpacedSocketsSizedFromTheFirstToTheLast() {
        val (document, torso) = rowed()
        val params = document.character.resolvedShape(assertNotNull(document.character.part(torso))).params
        val row = HandSockets.specs(params)
        assertEquals((0 until COUNT).map { HandSockets.member(1, it) }, row.map { it.name })
        row.zipWithNext().forEach { (a, b) -> assertEquals((TO - FROM) / (COUNT - 1), b.u - a.u, 1e-5f) }
        assertEquals(FIRST, row.first().scale, 1e-5f)
        assertEquals(FIRST * FALLOFF, row.last().scale, 1e-5f)
        assertTrue(row.all { HandSockets.rowOf(it.name) == 1 && HandSockets.idOf(it.name) == null })
    }

    @Test
    fun addPartOnOneSocketFillsTheRowInOneStepEachAtItsSize() {
        val (document, torso) = rowed()
        val open = openSockets(document, plan, shipped).filter { HandSockets.rowOf(it.name) == 1 }
        assertEquals(COUNT, open.size)
        var next = 0
        val placed = placeOnRow(open[2], open, spike, document) { "spike-${next++}" }
        assertEquals(COUNT, placed.size)
        val parts = placed.flatMap { it.first }
        val applied = AddParts(parts).apply(document)
        val character = applied.document.character
        val sizes = parts.map { character.resolvedShape(assertNotNull(character.part(it.id))).params.getValue(SIZE_PARAM) }
        assertEquals(open.map { it.scale }, sizes)
        assertEquals(emptyList(), openSockets(applied.document, plan, shipped).filter { HandSockets.rowOf(it.name) == 1 })
        assertEquals(document, applied.undo.apply(applied.document).document)
        val assembled = assembleNow(assemblyParts(character), { assertNotNull(character.part(it)).let(character::resolved) }, plan, library)
        val along = parts.map { assertNotNull(assembled.part(it.id)).origin.x }
        assertTrue(along.zipWithNext().all { (a, b) -> b < a }, "the spikes run from the front of the torso back: $along")
        assertTrue(parts.all { it.socket?.parent == torso })
    }
}
