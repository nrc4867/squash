package dev.chieppa.squash.character

import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.positionAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private const val CLOSE = 1e-4f

/** What a lean rebuilds and what it leaves, and that nothing of it stays once it goes. See HLD: data-model, Legs stay level as they lean. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaningLegsRebuildTest : LeaningLegsBench() {
    @Test
    fun leaningBackRestoresEveryVertexExactly() = runTest {
        val session = session()
        val square = session.built()
        apply(session, outward(frontLeft))
        apply(session, outward(frontLeft, 0f))
        assertSameVertices(square, session.built())
        val fresh = session()
        apply(fresh, outward(frontLeft, 0f))
        assertSameVertices(square, fresh.built())
    }

    @Test
    fun undoRestoresEveryVertexExactly() = runTest {
        val session = session()
        val square = session.built()
        apply(session, outward(frontLeft, thisSide = true), lean(backRight))
        session.history.undo()
        session.history.undo()
        runCurrent()
        assertSameVertices(square, session.built())
    }

    @Test
    fun aShortenedLegsOwnSocketPlaneAndThicknessStayWhereTheyWere() = runTest {
        val session = session()
        val square = session.built().of(frontRight)
        apply(session, outward(backLeft))
        val short = session.built().of(frontRight)
        assertEquals(square.origin, short.origin)
        val first = assertNotNull(square.surface.patches.first().slices)
        val after = assertNotNull(short.surface.patches.first().slices)
        assertEquals(first[first.size / 2].height, after[after.size / 2].height, "only its length changes")
        assertEquals(first[first.size / 2].width, after[after.size / 2].width)
    }

    @Test
    fun aPaintOnlyEditRebuildsNothing() = runTest {
        val session = session()
        val meshes = legs.map { session.mesh(it.id) }
        runCurrent()
        val before = meshes.map { it.output.value }
        val assembled = session.built()
        apply(session, SetBaseSlot(frontLeft.id, SlotName("fur")))
        meshes.zip(before).forEach { (node, mesh) -> assertSame(mesh, node.output.value) }
        assertSame(assembled, session.built())
    }

    @Test
    fun aLeanRebuildsTheOtherLegsAndNothingElse() = runTest {
        val session = session()
        val others = listOf(frontRight, backLeft, backRight).map { session.mesh(it.id) }
        val body = session.mesh(torso.id)
        runCurrent()
        val before = others.map { it.output.value }
        val bodyBefore = body.output.value
        apply(session, outward(frontLeft, thisSide = true))
        others.zip(before).forEach { (node, mesh) -> assertNotSame(mesh, node.output.value) }
        assertSame(bodyBefore, body.output.value)
    }

    @Test
    fun surfaceWorkFollowsTheShortenedLeg() = runTest {
        val session = session()
        val work = session.surfaceWork(backLeft.id)
        apply(session, line("sock", on(backLeft, 0.9f, 0.25f), on(backLeft, 0.9f, 0.5f)).let { SetMark(backLeft.id, it.id, it) })
        apply(session, outward(frontLeft))
        val character = session.built()
        val patch = character.of(backLeft).surface.patches.first()
        val point = assertNotNull(work.output.value).marks.single().points.first().position
        assertTrue((patch.positionAt(0.9f, 0.25f) - point).length < CLOSE, "the mark sits on the leg as it is built")
    }
}
