package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.mouthGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A head's lip line is the places of the marks its mouth's lip line role holds, and nothing where it draws no mouth. */
class LipLineTest {
    private val head = PartId("head")
    private val left = Mark(MarkId("lip-l"), listOf(Anchor(head, "muzzle", 0.98f, 0.4f), Anchor(head, "muzzle", 0.7f, 0.3f)))
    private val right = Mark(MarkId("lip-r"), listOf(Anchor(head, "muzzle", 0.98f, 0.6f), Anchor(head, "muzzle", 0.7f, 0.7f)))
    private val whisker = Mark(MarkId("whisker"), listOf(Anchor(head, "muzzle", 0.8f, 0.2f), Anchor(head, "muzzle", 0.6f, 0.2f)))

    @Test
    fun theLipLineIsTheMarksTheMouthsLipLineRoleHolds() {
        val group = mouthGroup(MarkGroupId("mouth"), "mouth").let { empty ->
            empty.copy(roles = empty.roles.map { if (it.role == MarkRole.LIP_LINE) it.copy(marks = listOf(left.id, right.id)) else it })
        }
        val work = SurfaceWork(marks = listOf(whisker, left, right), groups = listOf(group))
        assertEquals(left.points + right.points, lipLineOf(work))
    }

    @Test
    fun marksInNoMouthMakeNoLipLine() {
        val notMouth = MarkGroup(MarkGroupId("brows"), "brows", listOf(GroupRole(MarkRole.INNER, marks = listOf(left.id))))
        assertTrue(lipLineOf(SurfaceWork(marks = listOf(left, whisker), groups = listOf(notMouth))).isEmpty())
        assertTrue(lipLineOf(SurfaceWork(marks = listOf(left, whisker))).isEmpty())
    }
}
