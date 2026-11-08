package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Update from library: a part and its pair's other side move to another version of their template in one step, keeping all they hold. See HLD: data-model, Update from library. */
class UpdateFromLibraryTest : PairedFennec() {
    private val pose = Pose(listOf(BonePose(legL.id, "knee", degrees = 30f), BonePose(torso.id, "withers", degrees = 10f)), planted = setOf(pawL.id))

    private val worked = listOf(
        SetParameter(legL.id, "girth", 0.2f),
        SetParameter(legR.id, "girth", 0.3f, thisSide = true),
        SetMark(legL.id, MarkId("stripe"), line("stripe", legL)),
        SetPose(pose),
    ).fold(placed) { held, command -> command.apply(held).document }

    @Test
    fun bothSidesMoveAndKeepEverythingTheyHold() {
        scenario(worked) {
            run(UpdateFromLibrary(legL.id, 2))
            expect { updated ->
                val character = updated.character
                assertEquals(listOf(2, 2), listOf(legL, legR).map { character.part(it.id)?.template?.version })
                assertEquals(TemplateId("leg"), character.part(legR.id)?.template?.id)
                listOf(legL, legR).forEach { assertEquals(worked.character.resolved(it.id), character.resolved(it.id)) }
                assertEquals(worked.character.overridesOf(checkNotNull(worked.character.part(legR.id))), character.overridesOf(checkNotNull(character.part(legR.id))))
                assertEquals(pose, character.pose)
                assertEquals(1, character.part(torso.id)?.template?.version, "other parts keep their versions")
                assertEquals(1, character.part(pawL.id)?.template?.version)
            }
            undo()
            expect(worked)
            redo()
            expect { assertEquals(2, it.character.part(legR.id)?.template?.version) }
        }
    }

    @Test
    fun itRefusesTheVersionThePartAlreadyPins() {
        assertFailsWith<IllegalArgumentException> { UpdateFromLibrary(torso.id, 1).apply(worked) }
    }

    @Test
    fun itIsSerializable() {
        val command: DocumentCommand = UpdateFromLibrary(legL.id, 2)
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), Json.encodeToString(DocumentCommand.serializer(), command)))
    }
}
