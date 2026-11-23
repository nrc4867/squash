package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The character's pose keys: set, undone, serializable, held to sense, and written only once set. */
class PoseKeyCommandTest {
    private val empty = SquashDocument(Character(CharacterId("pup"), "Pup", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    private val leg = PartId("front-leg-l")
    private val bent = listOf(BonePose(leg, "knee", 60f))
    private val fuller = PartChange(leg, mapOf(View.SIDE to OutlineScale(plus = listOf(WidthScale(0f, 1f), WidthScale(0.1f, 1.2f)))), blend = 1.5f)

    @Test
    fun aCharacterStartsWithNoKeysAndWritesNothingForThem() {
        assertTrue(empty.character.poseKeys.isEmpty())
        assertFalse("poseKeys" in Json.encodeToString(SquashDocument.serializer(), empty))
    }

    @Test
    fun poseKeysAreSetUndoneAndSerializable() {
        val command = SetPoseKeys(listOf(PoseKey("knee", bent, listOf(fuller))))
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
        assertFailsWith<IllegalArgumentException> { PoseKey("rest", listOf(BonePose(leg, "knee"))) }
        assertFailsWith<IllegalArgumentException> { PoseKey("twice", bent, listOf(fuller, fuller)) }
        assertFailsWith<IllegalArgumentException> { SetPoseKeys(listOf(PoseKey("knee", bent), PoseKey("knee", bent))) }
        assertFailsWith<IllegalArgumentException> { WidthScale(0f, 9f) }
        assertFailsWith<IllegalArgumentException> { OutlineScale(plus = listOf(WidthScale(0.1f, 1f), WidthScale(0f, 1f))) }
        assertFailsWith<IllegalArgumentException> { PartChange(leg, blend = 0f) }
        assertTrue(PartChange(leg).isRest)
        assertFalse(fuller.isRest)
    }
}
