package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The character's neck style: blended until set, set and undone, and serializable. */
class NeckStyleCommandTest {
    private val empty = SquashDocument(Character(CharacterId("pup"), "Pup", BodyPlanRef(BodyPlanId("biped"), 1)))

    @Test
    fun aCharacterStartsBlendedAndWritesNothingForIt() {
        assertEquals(NeckStyle.BLENDED, empty.character.neck)
        assertFalse("neck" in Json.encodeToString(SquashDocument.serializer(), empty))
    }

    @Test
    fun theNeckStyleIsSetUndoneAndSerializable() {
        val command = SetNeckStyle(NeckStyle.DEFINED)
        val text = Json.encodeToString(DocumentCommand.serializer(), command)
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
        scenario(empty) {
            run(command)
            expect(empty.copy(character = empty.character.copy(neck = NeckStyle.DEFINED)))
            undo()
            expect(empty)
        }
    }
}
