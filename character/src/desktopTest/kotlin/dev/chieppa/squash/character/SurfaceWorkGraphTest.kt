package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.RemovePart
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.positionAt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceWorkGraphTest {
    private val first = outline(0f to 0.2f, 1f to 0.1f, 1f to -0.1f, 0f to -0.2f)
    private val second = outline(0f to 0.3f, 1f to 0.1f, 1f to -0.1f, 0f to -0.3f)
    private val body = outline(0f to 0.3f, 1.2f to 0.3f, 1.2f to -0.3f, 0f to -0.3f)
    private val mark = line("sock", on(leftLeg, 0.3f, 0.2f), on(leftLeg, 0.6f, 0.3f))

    private fun markOn(part: PartInstance) = line("sock", on(part, 0.3f, 0.2f), on(part, 0.6f, 0.3f))

    @Test
    fun aSurfaceOnlyEditReachesTheWorkAndLeavesEveryMeshAlone() = runTest {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        session.settle()
        val mesh = assertNotNull(session.mesh(leftLeg.id).output.value)
        val surface = session.surface(leftLeg.id).output.value
        val assembled = session.assembly.output.value
        val torsoWork = session.surfaceWork(torsoPart.id).output.value
        val before = session.surfaceWork(leftLeg.id).output.value

        session.send(SetMark(leftLeg.id, mark.id, mark))
        assertFalse(session.graph.settled.value, "settled waits on the work")
        session.settle()

        assertSame(mesh, session.mesh(leftLeg.id).output.value)
        assertSame(surface, session.surface(leftLeg.id).output.value)
        assertSame(assembled, session.assembly.output.value)
        assertSame(torsoWork, session.surfaceWork(torsoPart.id).output.value, "a part whose work did not change does not run")
        assertNotEquals(before, session.surfaceWork(leftLeg.id).output.value)
        assertEquals(1, session.bodySurfaceWork.output.value?.parts?.get(leftLeg.id)?.marks?.size)
        assertEquals(1, session.bodySurfaceWork.output.value?.parts?.get(rightLeg.id)?.marks?.size)
    }

    @Test
    fun anOutlineChangeRecomputesPositionsAndSettledWaitsForThem() = runTest {
        val session = surfaceSession(leftLeg, rightLeg)
        session.send(SetMark(leftLeg.id, mark.id, mark))
        session.settle()
        val node = session.surfaceWork(leftLeg.id)
        val before = assertNotNull(node.output.value).marks.single().points

        session.send(SetOutline(leftLeg.id, View.SIDE, first))
        assertFalse(session.graph.settled.value)
        assertEquals(before, node.output.value?.marks?.single()?.points, "the old positions stay until the new ones are ready")
        session.settle()

        val after = assertNotNull(node.output.value).marks.single().points
        val patch = session.builtSurface(leftLeg).body()
        assertEquals(before.map { it.anchor }, after.map { it.anchor })
        assertNotEquals(before.map { it.position }, after.map { it.position })
        after.forEach { assertClose(patch.positionAt(it.anchor.u, it.anchor.v), it.position, 1e-5f) }
    }

    @Test
    fun aTemplateChangeRecomputesPositions() = runTest {
        val id = PartId("limb")
        val session = surfaceSession(PartInstance(id, templateRef("leg")))
        val held = PartInstance(id, templateRef("leg"))
        val limb = markOn(held)
        session.send(SetMark(id, limb.id, limb))
        session.settle()
        val node = session.surfaceWork(id)
        val before = assertNotNull(node.output.value).marks.single().points

        val tail = PartInstance(id, templateRef("tail"))
        session.send(RemovePart(id))
        session.send(AddPart(tail))
        session.send(SetMark(id, limb.id, limb))
        session.settle()

        val after = assertNotNull(node.output.value).marks.single().points
        val patch = session.builtSurface(tail).body()
        assertEquals(before.map { it.anchor }, after.map { it.anchor })
        assertNotEquals(before.map { it.position }, after.map { it.position })
        after.forEach { assertClose(patch.positionAt(it.anchor.u, it.anchor.v), it.position, 1e-5f) }
    }

    @Test
    fun aParentsRedrawMovesAChildsBodyPositionsAndNotItsOwn() = runTest {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        session.send(SetMark(leftLeg.id, mark.id, mark))
        session.settle()
        val own = session.surfaceWork(leftLeg.id).output.value
        val before = assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(leftLeg.id)).marks.single().points

        session.send(SetOutline(torsoPart.id, View.SIDE, body))
        session.settle()

        assertSame(own, session.surfaceWork(leftLeg.id).output.value)
        val after = assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(leftLeg.id)).marks.single().points
        val origin = assertNotNull(session.assembly.output.value?.part(leftLeg.id)).origin
        assertNotEquals(before.map { it.position }, after.map { it.position })
        assertEquals(own?.marks?.single()?.points?.map { it.position + origin }, after.map { it.position })
    }

    @Test
    fun aStaleRunNeverShowsAndUndoGivesTheSameWorkAtOnce() = runTest {
        val session = surfaceSession(leftLeg, rightLeg)
        session.send(SetMark(leftLeg.id, mark.id, mark))
        session.settle()
        val node = session.surfaceWork(leftLeg.id)
        val old = assertNotNull(node.output.value)
        val seen = mutableListOf<ResolvedSurfaceWork?>()
        backgroundScope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { node.output.collect { seen += it } }

        session.send(SetOutline(leftLeg.id, View.SIDE, first))
        session.send(SetOutline(leftLeg.id, View.SIDE, second))
        assertSame(old, node.output.value)
        session.settle()

        val patch = session.builtSurface(leftLeg).body()
        val newest = assertNotNull(node.output.value)
        newest.marks.single().points.forEach { assertClose(patch.positionAt(it.anchor.u, it.anchor.v), it.position, 1e-5f) }
        assertEquals(listOf(old, newest), seen.distinct(), "only the old work and the newest run's ever show")
        assertNotSame(old, newest)

        session.history.undo()
        session.history.undo()
        session.settle()
        assertSame(old, node.output.value)
    }

    @Test
    fun theBodyNodeCoversThePartsFromTheStart() = runTest {
        val session = surfaceSession(torsoPart, leftLeg)
        assertFalse(session.graph.settled.value)
        session.settle()
        assertTrue(session.graph.settled.value)
        assertEquals(listOf(torsoPart.id, leftLeg.id), session.bodySurfaceWork.output.value?.parts?.keys?.toList())
    }

    @Test
    fun aFailedBuildReachesTheWorkAndTheGraphStillSettles() = runTest {
        val orphan = PartInstance(PartId("x"), TemplateRef(TemplateId("wing"), 1))
        val session = surfaceSession(orphan)
        val node = session.surfaceWork(orphan.id)
        session.settle()
        assertIs<IllegalArgumentException>(node.failure.value)
        assertNull(node.output.value)
    }

    @Test
    fun aPartNotInTheDocumentHoldsNothing() = runTest {
        val session = surfaceSession(torsoPart)
        val node = session.surfaceWork(PartId("gone"))
        session.settle()
        assertEquals(ResolvedSurfaceWork(), node.output.value)
        assertEquals(setOf(torsoPart.id), session.bodySurfaceWork.output.value?.parts?.keys)
    }
}
