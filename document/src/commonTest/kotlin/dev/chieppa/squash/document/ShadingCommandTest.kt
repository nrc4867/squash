package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ShadingCommandTest {
    private val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    private val cream = SlotName("cream")
    private val pale = SlotColor(0.99f, 0.94f, 0.82f)
    private val tan = SlotColor(0.85f, 0.70f, 0.50f)
    private val withCream = SetPaletteSlot(cream, pale).apply(empty).document

    private fun assertRoundTrips(command: DocumentCommand) {
        val text = Json.encodeToString(DocumentCommand.serializer(), command)
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
    }

    @Test
    fun aCharacterStartsFlatWithItsFarSideDarker() {
        assertEquals(ShadingMode.FLAT, empty.character.shading.mode)
        assertEquals(true, empty.character.shading.farSideDarker)
    }

    @Test
    fun theShadingChoiceIsSetUndoneAndSerializable() {
        val two = CharacterShading(ShadingMode.TWO_TONE, farSideDarker = false)
        val command = SetCharacterShading(two)
        assertRoundTrips(command)
        scenario(empty) {
            run(command)
            expect(command.apply(empty).document)
            undo()
            expect(empty)
        }
        assertEquals(two, command.apply(empty).document.character.shading)
    }

    @Test
    fun aSlotsShadowIsSetClearedUndoneAndSerializable() {
        val command = SetSlotShadow(cream, tan)
        assertRoundTrips(command)
        val set = command.apply(withCream).document
        assertEquals(tan, set.character.palette.shadow(cream))
        assertEquals(pale, set.character.palette.color(cream))
        assertNull(SetSlotShadow(cream, null).apply(set).document.character.palette.shadow(cream))
        scenario(withCream) {
            run(command)
            expect(set)
            undo()
            expect(withCream)
        }
    }

    @Test
    fun aShadowNeedsItsSlot() {
        assertFailsWith<IllegalArgumentException> { SetSlotShadow(SlotName("none"), tan).apply(empty) }
    }

    @Test
    fun recoloringASlotKeepsItsShadowAndUndoingARemovalBringsItBack() {
        val shaded = SetSlotShadow(cream, tan).apply(withCream).document
        val recolored = SetPaletteSlot(cream, SlotColor(0.9f, 0.9f, 0.8f)).apply(shaded).document
        assertEquals(tan, recolored.character.palette.shadow(cream))
        val removed = SetPaletteSlot(cream, null).apply(shaded)
        assertNull(removed.document.character.palette.color(cream))
        assertEquals(shaded, removed.undo.apply(removed.document).document)
    }

    @Test
    fun whatIsLeftAtItsDefaultWritesNothing() {
        val text = Json.encodeToString(SquashDocument.serializer(), withCream)
        assertEquals(false, "shading" in text || "shadow" in text, text)
    }
}
