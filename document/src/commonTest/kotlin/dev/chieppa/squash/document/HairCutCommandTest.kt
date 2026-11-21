package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Short-hair shells, trims and coils: set, replaced, cleared and undone as one serializable step, and refused out of range. */
class HairCutCommandTest {
    private val head = PartInstance(PartId("head"), TemplateRef(TemplateId("head-round"), 2))
    private val empty = AddPart(head).apply(SquashDocument(Character(CharacterId("toy"), "Toy", BodyPlanRef(BodyPlanId("blank"), 1)))).document

    @Test
    fun aShellIsSetReplacedClearedAndEachUndone() {
        val shell = HairShell(head.id, fade = ShellFade.MID, hairline = Hairline(front = 28f))
        val set = SetHairShell(head.id, shell)
        val text = Json.encodeToString(DocumentCommand.serializer(), set)
        assertEquals(set, Json.decodeFromString(DocumentCommand.serializer(), text))
        val added = set.apply(empty).document
        assertEquals(shell, added.character.hairShellOn(head.id))
        val thicker = shell.copy(thickness = 0.03f)
        scenario(added) {
            run(SetHairShell(head.id, thicker))
            expect(added.copy(character = added.character.copy(hairShells = listOf(thicker))))
            undo()
            expect(added)
            run(SetHairShell(head.id, null))
            expect(empty)
            undo()
            expect(added)
        }
    }

    @Test
    fun aShellOnAPartTheCharacterLacksIsRefused() {
        assertFailsWith<IllegalArgumentException> { SetHairShell(PartId("nobody"), HairShell(PartId("nobody"))).apply(empty) }
        assertFailsWith<IllegalArgumentException> { SetHairShell(head.id, HairShell(PartId("other"))) }
    }

    @Test
    fun cutValuesOutOfRangeAreRefused() {
        assertFailsWith<IllegalArgumentException> { HairShell(head.id, thickness = 0.2f) }
        assertFailsWith<IllegalArgumentException> { HairShell(head.id, fadeTo = 2f) }
        assertFailsWith<IllegalArgumentException> { Hairline(front = 90f) }
        assertFailsWith<IllegalArgumentException> { LockTrim(level = 3f) }
        assertFailsWith<IllegalArgumentException> { LockTrim(tilt = 80f) }
        val group = LockGroup(LockGroupId("bun"), LockGroupKind.CROWN, head.id)
        assertFailsWith<IllegalArgumentException> { group.copy(coil = 4f) }
        assertFailsWith<IllegalArgumentException> { group.copy(lie = 0f) }
    }

    @Test
    fun aGroupWithACutSavesAndReadsBack() {
        val group = LockGroup(LockGroupId("bob"), LockGroupKind.BACK, head.id, section = LockSection.BRAID, trim = LockTrim(-0.5f, 10f), coil = 1.5f, tie = 1f, lie = 0.2f)
        val shelled = SetHairShell(head.id, HairShell(head.id)).apply(SetLockGroup(group.id, group).apply(empty).document).document
        val text = Json.encodeToString(Character.serializer(), shelled.character)
        assertEquals(shelled.character, Json.decodeFromString(Character.serializer(), text))
    }
}
