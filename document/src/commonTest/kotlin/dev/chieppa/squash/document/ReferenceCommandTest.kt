package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReferenceCommandTest {
    private val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    private val sheet = ReferenceImage(ImageId("side-sheet"), "Side sheet", ImageBytes(byteArrayOf(1, 2, 3)), 640, 430, BoardPlace(12f, 12f, 236f))
    private val trace = Trace(sheet.id, fitX = 0f, fitY = 0.4f, fitUnits = 0.002f)

    private fun assertRoundTrips(command: DocumentCommand) {
        val text = Json.encodeToString(DocumentCommand.serializer(), command)
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
    }

    @Test
    fun anImageIsAddedWithItsBytesAndUndone() {
        val command = SetReferenceImage(sheet.id, sheet)
        assertRoundTrips(command)
        scenario(empty) {
            run(command)
            expect(empty.copy(references = References(listOf(sheet))))
            undo()
            expect(empty)
        }
    }

    @Test
    fun removingAnImageTakesItsTracesAndTheUndoPutsThemBack() {
        val placed = SetTrace(View.SIDE, trace).apply(SetReferenceImage(sheet.id, sheet).apply(empty).document).document
        val removal = SetReferenceImage(sheet.id, null).apply(placed)
        assertEquals(emptyMap(), removal.document.character.traces)
        assertEquals(placed, removal.undo.apply(removal.document).document)
        assertRoundTrips(removal.undo as DocumentCommand)
    }

    @Test
    fun aTraceNeedsItsImageOnTheBoard() {
        assertFailsWith<IllegalArgumentException> { SetTrace(View.SIDE, trace).apply(empty) }
    }

    @Test
    fun aTraceIsPlacedMovedAndTakenAway() {
        val held = SetReferenceImage(sheet.id, sheet).apply(empty).document
        val moved = trace.copy(scale = 1.08f, x = 8f, y = -30f)
        scenario(held) {
            run(SetTrace(View.SIDE, trace))
            run(SetTrace(View.SIDE, moved))
            undo()
            expect(SetTrace(View.SIDE, trace).apply(held).document)
        }
        assertEquals(1.08f * 0.002f, moved.units)
        assertEquals(0.4f - 30f * 0.002f, moved.centerY)
        assertNull(SetTrace(View.SIDE, null).apply(SetTrace(View.SIDE, trace).apply(held).document).document.character.traces[View.SIDE])
    }

    @Test
    fun aDocumentWithImagesReadsBack() {
        val document = SetTrace(View.FRONT, trace).apply(SetReferenceImage(sheet.id, sheet).apply(empty).document).document
        val text = Json.encodeToString(SquashDocument.serializer(), document)
        assertEquals(document, readDocument(text))
    }
}
