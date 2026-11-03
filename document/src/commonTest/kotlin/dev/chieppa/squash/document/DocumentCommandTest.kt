package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DocumentCommandTest {
    private val leg = PartInstance(PartId("leg-1"), TemplateRef(TemplateId("leg"), 1))
    private val tail = PartInstance(PartId("tail-1"), TemplateRef(TemplateId("tail"), 1))
    private val empty = SquashDocument(
        Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)),
    )
    private val outline = OutlineCurve(
        listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0f), CurvePoint(1f, 1f), CurvePoint(0f, 1f)),
    )

    @Test
    fun addingAPartUndoesAndRedoes() {
        scenario(empty) {
            run(AddPart(leg), AddPart(tail))
            expect { assertEquals(listOf(leg, tail), it.character.parts) }
            undo()
            expect { assertEquals(listOf(leg), it.character.parts) }
            redo()
            expect { assertEquals(listOf(leg, tail), it.character.parts) }
        }
    }

    @Test
    fun removingAPartRestoresItInPlace() {
        scenario(empty) {
            run(AddPart(leg), AddPart(tail), RemovePart(leg.id))
            expect { assertEquals(listOf(tail), it.character.parts) }
            undo()
            expect { assertEquals(listOf(leg, tail), it.character.parts) }
        }
    }

    @Test
    fun settingAnOutlineUndoesToNoneOrThePreviousCurve() {
        val other = OutlineCurve(outline.points.reversed())
        scenario(empty) {
            run(AddPart(leg), SetOutline(leg.id, View.SIDE, outline), SetOutline(leg.id, View.SIDE, other))
            expect { assertEquals(other, it.character.part(leg.id)?.outlines?.get(View.SIDE)) }
            undo()
            expect { assertEquals(outline, it.character.part(leg.id)?.outlines?.get(View.SIDE)) }
            undo()
            expect { assertNull(it.character.part(leg.id)?.outlines?.get(View.SIDE)) }
        }
    }

    @Test
    fun aDragIsOneUndoStep() {
        scenario(empty) {
            run(AddPart(leg))
            gesture(SetParameter(leg.id, "size", 1.1f), SetParameter(leg.id, "size", 1.4f))
            expect { assertEquals(1.4f, it.character.part(leg.id)?.params?.get("size")) }
            undo()
            expect { assertNull(it.character.part(leg.id)?.params?.get("size")) }
        }
    }

    @Test
    fun aCommandOnAMissingPartIsRefused() {
        assertFailsWith<IllegalArgumentException> { SetParameter(PartId("none"), "size", 1f).apply(empty) }
        assertFailsWith<IllegalArgumentException> { AddPart(leg).apply(AddPart(leg).apply(empty).document) }
    }

    @Test
    fun commandsSerialize() {
        val commands = listOf<DocumentCommand>(
            AddPart(leg),
            RemovePart(leg.id),
            SetOutline(leg.id, View.TOP, outline),
            SetParameter(leg.id, "size", null),
        )
        for (command in commands) {
            val text = Json.encodeToString(DocumentCommand.serializer(), command)
            assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
        }
    }

    @Test
    fun anOutlineHoldsThreePointsPerSegmentPlusOne() {
        assertFailsWith<IllegalArgumentException> { OutlineCurve(outline.points.drop(1)) }
    }

    @Test
    fun aUlidIsTwentySixCharactersAndSortsByTime() {
        val first = Ulid.next(millis = 1_000L)
        val second = Ulid.next(millis = 2_000L)
        assertEquals(26, first.length)
        assertEquals(true, first.take(10) < second.take(10))
    }
}
