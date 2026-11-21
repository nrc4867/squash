package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.BodyRecesses
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.MUZZLE_PATCH
import dev.chieppa.squash.screens.fennec.fennecDocument
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private val MOUTH = MarkGroupId("mouth")

/** A carved mouth that opens cuts a recess into the head behind its lips, which a closed or painted one does not. */
@Tag("integration")
class CarvedMouthTest {
    private val work = TestWork()
    private val session: CharacterSession by lazy { openSession(fennecDocument(), work) }

    private fun recesses(): BodyRecesses {
        work.settle(session)
        return assertNotNull(session.mouthRecesses.output.value)
    }

    /** The one surface's field at [local], a point of the head's own space. */
    private fun fieldAt(local: Vec3): Float {
        work.settle(session)
        val head = assertNotNull(session.placed.output.value?.part(HEAD))
        val field = assertNotNull(session.characterSurface.output.value?.surface).field
        return field.field.value(head.origin + head.surface.shown(local))
    }

    @Test
    fun anOpenCarvedMouthIsCutIntoTheHeadAndAClosedOneIsNot() {
        val group = assertNotNull(session.document.value.character.resolved(HEAD)).surface.groups.first { it.id == MOUTH }
        session.send(SetMarkGroup(HEAD, MOUTH, group.copy(style = CARVED)))
        assertEquals(BodyRecesses.NONE, recesses(), "a closed mouth cuts nothing")
        session.send(SetMarkControls(HEAD, MOUTH, MarkControls(open = 0.7f)))
        val recess = recesses().of(HEAD).single()
        assertEquals(MUZZLE_PATCH, recess.patch, "the recess is cut into the run the lips lie on")
        val opening = recess.wallAt(0.5f, 0f, upper = true).position.let { top ->
            val bottom = recess.wallAt(0.5f, 0f, upper = false).position
            (top + bottom) * 0.5f
        }
        assertTrue(fieldAt(opening) > 0f, "the middle of the opening is open")
        val back = recess.wallAt(SIDE, 0.9f, upper = false)
        assertTrue(abs(fieldAt(back.position)) < recess.rounding, "a point on the wall teeth would stand on, along the muzzle's side, lies on the surface")
        val step = recess.rounding * STEP_SHARE
        assertTrue(fieldAt(back.position + back.normal * step) > fieldAt(back.position - back.normal * step), "the wall's normal points into the open mouth")
        session.history.undo()
        assertEquals(BodyRecesses.NONE, recesses(), "closing it again fills the head back in")
        assertTrue(assertNotNull(session.characterSurface.output.value?.surface).field.recesses.isEmpty(), "the surface is built uncut")
        session.history.undo()
    }

    @Test
    fun aMuzzlesOwnStyleOpensOnTheSurfaceOnly() {
        session.send(SetMarkControls(HEAD, MOUTH, MarkControls(open = 0.7f)))
        assertEquals(BodyRecesses.NONE, recesses(), "a short muzzle paints its opening and cuts nothing")
        session.history.undo()
    }

    private companion object {
        const val CARVED = "carved"

        /** A place along the mouth line on one side of the muzzle, a share of the way from one corner to the other. */
        const val SIDE = 0.2f

        /** How far either side of the wall the field is read, a share of the lips' rounding. */
        const val STEP_SHARE = 0.25f
    }
}
