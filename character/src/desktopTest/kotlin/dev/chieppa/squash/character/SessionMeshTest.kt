package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.RemovePart
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.PartMesh
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionMeshTest {
    private val legId = PartId("leg-1")
    private val leg = PartInstance(legId, TemplateRef(TemplateId("leg"), 1))
    private val first = outline(0f to 0.2f, 1f to 0.1f, 1f to -0.1f, 0f to -0.2f)
    private val second = outline(0f to 0.3f, 1f to 0.1f, 1f to -0.1f, 0f to -0.3f)

    private fun TestScope.session(vararg parts: PartInstance) = CharacterSession(
        SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1), parts.toList())),
        TemplateLibrary(shipped.parts),
        backgroundScope,
        StandardTestDispatcher(testScheduler),
    )

    @Test
    fun aPartHasAMeshOnceSettledInTheTemplatesTopology() = runTest {
        val session = session(leg)
        val node = session.mesh(legId)
        assertSame(node, session.mesh(legId))
        assertFalse(session.graph.settled.value)
        runCurrent()
        assertTrue(session.graph.settled.value)
        val mesh = assertNotNull(node.output.value)
        assertEquals("leg-1", mesh.part)
        assertEquals(24 * 16, mesh.patches.single().vertexCount)
        assertEquals(PartBuilder.build(template("leg")).single(), mesh.patches.single().patch)
    }

    @Test
    fun anOutlineCommandRebuildsTheMeshThroughTheOutlines() = runTest {
        val session = session(leg)
        val node = session.mesh(legId)
        runCurrent()
        session.send(SetOutline(legId, View.SIDE, first))
        assertFalse(session.graph.settled.value)
        runCurrent()
        assertTrue(session.graph.settled.value)
        val expected = PartBuilder.build(template("leg"), outlines = mapOf(View.SIDE to first))
        assertEquals(expected.single(), node.output.value?.patches?.single()?.patch)
    }

    @Test
    fun theOldMeshStaysUntilTheNewestRunFinishesAndACancelledRunNeverShows() = runTest {
        val session = session(leg)
        val node = session.mesh(legId)
        runCurrent()
        val old = assertNotNull(node.output.value)
        val seen = mutableListOf<PartMesh?>()
        backgroundScope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) { node.output.collect { seen += it } }

        session.send(SetOutline(legId, View.SIDE, first))
        assertSame(old, node.output.value)
        session.send(SetOutline(legId, View.SIDE, second))
        assertSame(old, node.output.value)
        assertFalse(session.graph.settled.value)

        runCurrent()
        assertTrue(session.graph.settled.value)
        val expected = PartBuilder.build(template("leg"), outlines = mapOf(View.SIDE to second)).single()
        assertEquals(expected, node.output.value?.patches?.single()?.patch)
        assertEquals(2, seen.distinct().size, "only the old mesh and the newest run's mesh ever show")
        assertNotSame(old, node.output.value)
    }

    @Test
    fun undoGivesTheSameMeshAtOnce() = runTest {
        val session = session(leg)
        val node = session.mesh(legId)
        runCurrent()
        val before = node.output.value
        session.send(SetOutline(legId, View.SIDE, first))
        runCurrent()
        session.history.undo()
        runCurrent()
        assertTrue(session.graph.settled.value)
        assertSame(before, node.output.value)
    }

    @Test
    fun aRemovedPartHasAnEmptyMesh() = runTest {
        val session = session(leg)
        val node = session.mesh(legId)
        runCurrent()
        session.send(RemovePart(legId))
        runCurrent()
        assertEquals(emptyList(), node.output.value?.patches)
        session.send(AddPart(leg))
        runCurrent()
        assertEquals(1, node.output.value?.patches?.size)
    }

    @Test
    fun aFailedBuildReachesTheMeshNodeAndKeepsItsLastResult() = runTest {
        val orphan = PartInstance(PartId("x"), TemplateRef(TemplateId("wing"), 1))
        val session = session(orphan)
        val node = session.mesh(orphan.id)
        runCurrent()
        assertTrue(session.graph.settled.value)
        assertIs<IllegalArgumentException>(node.failure.value)
        assertNull(node.output.value)
    }
}
