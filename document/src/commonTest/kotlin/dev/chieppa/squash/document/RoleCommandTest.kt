package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoleCommandTest : PairedFennec() {
    private fun SquashDocument.roleOf(part: PartInstance) = checkNotNull(character.part(part.id)).role
    private fun SquashDocument.guessed(part: PartInstance) = checkNotNull(character.part(part.id)).roleGuessed

    @Test
    fun aRoleIsSetOnBothSidesOfAPairAndUndoneInOneStep() {
        scenario(placed) {
            run(SetRole(legR.id, Role.ARM))
            expect {
                assertEquals(Role.ARM, it.roleOf(legL))
                assertEquals(Role.ARM, it.roleOf(legR))
                assertNull(it.roleOf(torso), "a part off the pair keeps its own")
            }
            undo()
            expect(placed)
            redo()
            expect { assertEquals(Role.ARM, it.roleOf(legL)) }
        }
    }

    @Test
    fun choosingARoleConfirmsAGuess() {
        val guessed = SetRole(pawL.id, Role.FOOT, guessed = true).apply(placed).document
        assertTrue(guessed.guessed(pawL) && guessed.guessed(pawR))
        scenario(guessed) {
            run(SetRole(pawL.id, Role.HAND))
            expect {
                assertEquals(Role.HAND, it.roleOf(pawR))
                assertFalse(it.guessed(pawL) || it.guessed(pawR))
            }
            undo()
            expect {
                assertEquals(Role.FOOT, it.roleOf(pawR))
                assertTrue(it.guessed(pawR), "undo brings the guess back as a guess")
            }
        }
    }

    @Test
    fun noRoleIsNeverAGuess() {
        val cleared = SetRole(torso.id, null, guessed = true).apply(SetRole(torso.id, Role.BODY).apply(placed).document).document
        assertNull(cleared.roleOf(torso))
        assertFalse(cleared.guessed(torso))
    }

    @Test
    fun aRoleAndItsCommandRoundTripThroughJson() {
        val command: DocumentCommand = SetRole(legL.id, Role.LEG, guessed = true)
        val text = Json.encodeToString(DocumentCommand.serializer(), command)
        assertTrue("\"setRole\"" in text && "\"leg\"" in text, text)
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
        val document = SetRole(legL.id, Role.ARM).apply(placed).document
        val saved = Json.encodeToString(SquashDocument.serializer(), document)
        assertTrue("\"role\":\"arm\"" in saved, saved)
        assertEquals(document, Json.decodeFromString(SquashDocument.serializer(), saved))
    }
}
