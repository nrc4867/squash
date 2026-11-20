package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The character's creases and a garment's crease strengths: set, undone, serializable, held to range, and left out of its cut. */
class CreasesCommandTest {
    private val sweater = GarmentId("sweater")
    private val empty = SquashDocument(
        Character(
            CharacterId("pup"), "Pup", BodyPlanRef(BodyPlanId("canine-quadruped"), 1),
            wardrobe = Wardrobe(listOf(Garment(sweater, "Sweater"))),
        ),
    )

    private fun assertRoundTrips(command: DocumentCommand) {
        val text = Json.encodeToString(DocumentCommand.serializer(), command)
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
    }

    @Test
    fun aCharacterStartsWithCreasesOnAndWritesNothingForThem() {
        assertTrue(empty.character.creases.on)
        assertEquals(CharacterCreases(DEFAULT_CREASE_STRENGTH, DEFAULT_CREASE_STRENGTH, REST_CREASE), empty.character.creases)
        val text = Json.encodeToString(SquashDocument.serializer(), empty)
        assertFalse("creases" in text || "wrinkles" in text || "drape" in text, text)
    }

    @Test
    fun theCharactersCreasesAreSetUndoneAndSerializable() {
        val command = SetCharacterCreases(CharacterCreases(skin = 0f, cloth = 0.5f))
        assertRoundTrips(command)
        scenario(empty) {
            run(command)
            expect(command.apply(empty).document)
            undo()
            expect(empty)
        }
        assertTrue(command.apply(empty).document.character.creases.on)
        assertFailsWith<IllegalArgumentException> { CharacterCreases(skin = 3f) }
    }

    @Test
    fun aGarmentsStrengthsAreSetHeldToRangeAndUndone() {
        val command = SetGarmentCrease(sweater, ClothCrease.WRINKLES, 1.6f)
        assertRoundTrips(command)
        scenario(empty) {
            run(command)
            run(SetGarmentCrease(sweater, ClothCrease.DRAPE, 5f))
            undo()
            undo()
            expect(empty)
        }
        val set = SetGarmentCrease(sweater, ClothCrease.DRAPE, 5f).apply(command.apply(empty).document).document.character.wardrobe.garments.single()
        assertEquals(1.6f, set.wrinkles)
        assertEquals(MOST_CREASE_STRENGTH, set.drape)
        assertEquals(DEFAULT_CREASE_STRENGTH, set.creases)
    }

    @Test
    fun aGarmentsStrengthsDoNotChangeWhatItsCutReads() {
        val plain = empty.character.wardrobe.garments.single()
        assertSame(plain, plain.unprinted)
        val creased = plain.copy(creases = 0.2f, wrinkles = 1.8f, drape = 0f)
        assertEquals(plain, creased.unprinted)
    }
}
