package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** The character's view keys: set, undone, serializable, held to range, and written only once set. */
class ViewKeysCommandTest {
    private val empty = SquashDocument(Character(CharacterId("pup"), "Pup", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))

    @Test
    fun aCharacterStartsWithDefaultKeysAndWritesNothingForThem() {
        assertEquals(ViewKeys(), empty.character.viewKeys)
        assertFalse("viewKeys" in Json.encodeToString(SquashDocument.serializer(), empty))
    }

    @Test
    fun viewKeysAreSetUndoneAndSerializable() {
        val keys = ViewKeys(
            face = FaceStyle.FIXED,
            faceFollow = 0.7f,
            keys = listOf(ViewKey(KeyView.THREE_QUARTER, Side.RIGHT, listOf(KeyMove(part = PartId("ear-l"), twist = 20f), KeyMove(KeyFeature.HEAD, area = "chin", left = 0.02f)))),
        )
        val command = SetViewKeys(keys)
        val text = Json.encodeToString(DocumentCommand.serializer(), command)
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
        scenario(empty) {
            run(command)
            expect(command.apply(empty).document)
            undo()
            expect(empty)
        }
    }

    @Test
    fun keysAreHeldToSense() {
        assertFailsWith<IllegalArgumentException> { KeyMove() }
        assertFailsWith<IllegalArgumentException> { KeyMove(KeyFeature.EARS, part = PartId("ear-l")) }
        assertFailsWith<IllegalArgumentException> { KeyMove(KeyFeature.EARS, scale = 9f) }
        assertFailsWith<IllegalArgumentException> { ViewKey(KeyView.FRONT, Side.LEFT) }
        assertFailsWith<IllegalArgumentException> { ViewKeys(faceFollow = 2f) }
    }
}
