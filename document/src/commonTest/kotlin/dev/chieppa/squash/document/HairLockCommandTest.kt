package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Lock groups, single locks set by hand and collision shapes: each set, replaced, removed and undone as one serializable step. */
class HairLockCommandTest {
    private val head = PartInstance(PartId("head"), TemplateRef(TemplateId("head-round"), 2))
    private val empty = AddPart(head).apply(SquashDocument(Character(CharacterId("toy"), "Toy", BodyPlanRef(BodyPlanId("blank"), 1)))).document
    private val fringeId = LockGroupId("fringe")
    private val fringe = LockGroup(fringeId, LockGroupKind.FRINGE, head.id, count = 9, length = 0.1f, tip = LockTip.FORKED, tipPoints = 3)

    private fun assertRoundTrips(command: DocumentCommand) {
        val text = Json.encodeToString(DocumentCommand.serializer(), command)
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
    }

    @Test
    fun aGroupIsAddedReplacedRemovedAndEachUndone() {
        val add = SetLockGroup(fringeId, fringe)
        assertRoundTrips(add)
        val added = add.apply(empty).document
        assertEquals(listOf(fringe), added.character.lockGroups)
        val longer = fringe.copy(length = 0.2f)
        scenario(added) {
            run(SetLockGroup(fringeId, longer))
            expect(added.copy(character = added.character.copy(lockGroups = listOf(longer))))
            undo()
            expect(added)
            run(SetLockGroup(fringeId, null))
            expect(empty)
            undo()
            expect(added)
        }
    }

    @Test
    fun aRemovedGroupComesBackInItsPlace() {
        val back = LockGroup(LockGroupId("back"), LockGroupKind.BACK, head.id)
        val both = SetLockGroup(back.id, back).apply(SetLockGroup(fringeId, fringe).apply(empty).document).document
        val removed = SetLockGroup(fringeId, null).apply(both)
        assertEquals(listOf(back), removed.document.character.lockGroups)
        assertEquals(both, removed.undo.apply(removed.document).document)
    }

    @Test
    fun aGroupOnAPartTheCharacterLacksIsRefused() {
        assertFailsWith<IllegalArgumentException> { SetLockGroup(fringeId, fringe.copy(on = PartId("nobody"))).apply(empty) }
        assertFailsWith<IllegalArgumentException> { SetLockGroup(fringeId, fringe.copy(mass = PartId("nobody"))).apply(empty) }
    }

    @Test
    fun valuesOutOfRangeAreRefused() {
        assertFailsWith<IllegalArgumentException> { fringe.copy(count = 0) }
        assertFailsWith<IllegalArgumentException> { fringe.copy(curl = 4f) }
        assertFailsWith<IllegalArgumentException> { fringe.copy(tipPoints = 4) }
        assertFailsWith<IllegalArgumentException> { LockOverride(0, bones = 7) }
        assertFailsWith<IllegalArgumentException> { LockPhysics(stiffness = 2f) }
        assertFailsWith<IllegalArgumentException> { Collider("head", head.id, ColliderShape.SPHERE, LockPoint(0f, 0f, 0f), LockPoint(0f, 1f, 0f), 0.1f) }
    }

    @Test
    fun oneLockIsPulledOutByIndexAndReturnedToItsGroup() {
        val withGroup = SetLockGroup(fringeId, fringe).apply(empty).document
        val own = LockOverride(3, length = 0.3f, curl = 1f, guide = listOf(LockPoint(0.01f, -0.05f, 0f), LockPoint(0.03f, -0.1f, 0f)), physics = LockPhysics(stiffness = 0.1f))
        val command = SetLockOverride(fringeId, 3, own)
        assertRoundTrips(command)
        val applied = command.apply(withGroup)
        assertEquals(own, applied.document.character.lockGroup(fringeId)?.overrideOf(3))
        assertNull(applied.document.character.lockGroup(fringeId)?.overrideOf(2))
        assertEquals(withGroup, applied.undo.apply(applied.document).document)
        assertFailsWith<IllegalArgumentException> { SetLockOverride(fringeId, 9, LockOverride(9)).apply(withGroup) }
    }

    @Test
    fun collisionShapesAreSetAndUndone() {
        val shapes = listOf(
            Collider("head", head.id, ColliderShape.SPHERE, LockPoint(0f, 1f, 0f), radius = 0.12f),
            Collider("neck", head.id, ColliderShape.CAPSULE, LockPoint(0f, 0.8f, 0f), LockPoint(0f, 0.9f, 0f), 0.05f),
        )
        val command = SetColliders(shapes)
        assertRoundTrips(command)
        scenario(empty) {
            run(command)
            expect(empty.copy(character = empty.character.copy(colliders = shapes)))
            undo()
            expect(empty)
        }
    }

    @Test
    fun aCharacterWithLocksAndCollidersSavesAndReadsBack() {
        val saved = SetColliders(listOf(Collider("head", head.id, ColliderShape.SPHERE, LockPoint(0f, 1f, 0f), radius = 0.1f)))
            .apply(SetLockGroup(fringeId, fringe.withOverride(1, LockOverride(1, hidden = true))).apply(empty).document).document
        val text = Json.encodeToString(SquashDocument.serializer(), saved)
        assertEquals(saved, readDocument(text))
    }

    @Test
    fun aScalpFlowIsSetReplacedClearedAndEachUndone() {
        val flow = ScalpFlow(head.id, Whorl(around = 160f, turn = -0.5f), Parting(PartingKind.SIDE, at = 0.6f, color = SlotName("scalp")), sweep = 0.3f, cowlicks = listOf(Cowlick(around = 20f)))
        val command = SetScalpFlow(head.id, flow)
        assertRoundTrips(command)
        assertEquals(ScalpFlow(head.id), empty.character.scalpFlowOn(head.id), "a head with no flow of its own takes the default")
        val set = command.apply(empty).document
        assertEquals(flow, set.character.scalpFlowOn(head.id))
        val centre = flow.copy(parting = Parting(PartingKind.CENTRE))
        scenario(set) {
            run(SetScalpFlow(head.id, centre))
            expect(set.copy(character = set.character.copy(scalpFlows = listOf(centre))))
            undo()
            expect(set)
            run(SetScalpFlow(head.id, null))
            expect(empty)
            undo()
            expect(set)
        }
        assertFailsWith<IllegalArgumentException> { SetScalpFlow(PartId("nobody"), ScalpFlow(PartId("nobody"))).apply(empty) }
        assertFailsWith<IllegalArgumentException> { SetScalpFlow(head.id, ScalpFlow(PartId("other"))) }
        assertFailsWith<IllegalArgumentException> { Parting(at = 2f) }
        assertFailsWith<IllegalArgumentException> { Whorl(up = 120f) }
        assertFailsWith<IllegalArgumentException> { ScalpFlow(head.id, cowlicks = List(MOST_COWLICKS + 1) { Cowlick() }) }
        val text = Json.encodeToString(SquashDocument.serializer(), set)
        assertEquals(set, readDocument(text))
    }

    @Test
    fun aBlendedSlotReadsAsItsSlotsMixed() {
        val skin = SlotName("skin")
        val hair = SlotName("hair")
        val palette = Palette(listOf(PaletteSlot(skin, SlotColor(1f, 0.8f, 0.6f), shadow = SlotColor(0.8f, 0.6f, 0.4f)), PaletteSlot(hair, SlotColor(0.4f, 0.2f, 0f), shadow = SlotColor(0.2f, 0f, 0f))))
        val scalp = blendedSlot(skin, hair, 0.5f)
        fun assertNear(expected: SlotColor, actual: SlotColor?) {
            val got = assertNotNull(actual)
            assertEquals(expected.red, got.red, 1e-5f)
            assertEquals(expected.green, got.green, 1e-5f)
            assertEquals(expected.blue, got.blue, 1e-5f)
        }
        assertNear(SlotColor(0.7f, 0.5f, 0.3f), palette.color(scalp))
        assertNear(SlotColor(0.5f, 0.3f, 0.2f), palette.shadow(scalp))
        assertEquals(1f, palette.opacity(scalp))
        assertNull(palette.color(SlotName("nothing")))
        assertEquals(palette.color(skin), palette.color(blendedSlot(skin, SlotName("nothing"), 0.5f)), "a blend with a missing slot takes the one there")
        assertFailsWith<IllegalArgumentException> { blendedSlot(skin, hair, 2f) }
    }

    @Test
    fun aGroupShowsAsAPartOfItsOwn() {
        val withGroup = SetLockGroup(fringeId, fringe).apply(empty).document
        assertEquals(fringeId, lockGroupOf(lockGroupPartId(fringeId)))
        assertEquals(fringe, withGroup.character.lockGroupShownAs(lockGroupPartId(fringeId)))
        assertNull(lockGroupOf(head.id))
    }
}
