package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.shipped
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.Cowlick
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.ScalpFlow
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.document.SetLockOverride
import dev.chieppa.squash.document.SetScalpFlow
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.lockGroupPartId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Editing hair in a session builds the locks it changed and nothing of the body: the body's assembly and every other part's lines stay as they were. */
@OptIn(ExperimentalCoroutinesApi::class)
@Tag("integration")
class LockSessionTest {
    private val head = PartId("head")
    private val fringe = LockGroup(LockGroupId("fringe"), LockGroupKind.FRINGE, head, count = 6, length = 0.08f, width = 0.03f, thickness = 0.012f)
    private val back = LockGroup(LockGroupId("back"), LockGroupKind.BACK, head, count = 8, length = 0.1f, width = 0.04f, thickness = 0.012f)

    @Test
    fun aLockEditLeavesTheBodyAndTheOtherGroupAsTheyWere() = runTest {
        val start = AddPart(PartInstance(head, TemplateRef(TemplateId("head-round"), 2), role = Role.HEAD))
            .apply(SquashDocument(Character(CharacterId("toy"), "Toy", BodyPlanRef(BodyPlanId("blank"), 1)))).document
        val session = CharacterSession(start, TemplateLibrary(shipped.parts, shipped.plans), backgroundScope, StandardTestDispatcher(testScheduler))
        session.send(SetLockGroup(fringe.id, fringe))
        session.send(SetLockGroup(back.id, back))
        runCurrent()
        assertTrue(session.graph.settled.value)
        val body = assertNotNull(session.bodyAssembly.output.value)
        val lines = assertNotNull(session.bodyLines.output.value)
        val backPart = assertNotNull(session.assembly.output.value?.part(lockGroupPartId(back.id)))

        session.send(SetLockOverride(fringe.id, 2, LockOverride(2, length = 0.12f)))
        runCurrent()
        assertTrue(session.graph.settled.value)
        assertSame(body, session.bodyAssembly.output.value, "the body is not assembled again")
        assertEquals(1, session.lockBuilds.lastBuilt, "only the edited lock is built")
        assertSame(backPart, session.assembly.output.value?.part(lockGroupPartId(back.id)), "the other group is the same part")
        val after = assertNotNull(session.bodyLines.output.value)
        assertSame(lines.parts.getValue(head).grids.first(), after.parts.getValue(head).grids.first(), "the head's line grids are kept")
        assertSame(lines.parts.getValue(lockGroupPartId(back.id)).grids.first(), after.parts.getValue(lockGroupPartId(back.id)).grids.first(), "the other group's are kept")

        session.send(SetScalpFlow(head, ScalpFlow(head, cowlicks = listOf(Cowlick(around = 0f, up = 46f, radius = 20f)))))
        runCurrent()
        assertTrue(session.graph.settled.value)
        assertSame(body, session.bodyAssembly.output.value, "a flow edit does not assemble the body again")
        assertTrue(session.lockBuilds.lastBuilt in 1 until fringe.count, "a cowlick builds only the locks it reaches: ${session.lockBuilds.lastBuilt}")
        assertSame(backPart, session.assembly.output.value?.part(lockGroupPartId(back.id)), "a group the cowlick does not reach is the same part")
    }
}
