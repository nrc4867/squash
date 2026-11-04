package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.sliceRowNode
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.RemovePart
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.graph.Graph
import dev.chieppa.stretch.core.History
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SliceRowNodeTest {
    private val library = TemplateLibrary(shipped.parts)
    private val legId = PartId("leg-1")
    private val leg = PartInstance(legId, TemplateRef(TemplateId("leg"), 1))
    private val tail = PartInstance(PartId("tail-1"), TemplateRef(TemplateId("tail"), 1))

    private fun history(vararg parts: PartInstance) = History(
        SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1), parts.toList())),
    )

    private fun TestScope.graph() = Graph(backgroundScope, StandardTestDispatcher(testScheduler))

    @Test
    fun aPartBuildsItsRowOfSlices() = runTest {
        val graph = graph()
        val node = graph.sliceRowNode(history(leg).document, legId, library)
        assertFalse(graph.settled.value)
        runCurrent()
        assertTrue(graph.settled.value)
        val slices = assertNotNull(node.output.value?.patches?.single()?.slices)
        assertEquals(template("leg").topology.rings, slices.size)
    }

    @Test
    fun aParameterCommandRebuildsAndUndoIsInstantFromTheCache() = runTest {
        val graph = graph()
        val history = history(leg)
        val node = graph.sliceRowNode(history.document, legId, library)
        runCurrent()
        val before = node.output.value

        history.execute(SetParameter(legId, "thickness", 0.4f))
        assertFalse(graph.settled.value)
        runCurrent()
        assertNotEquals(before, node.output.value)
        assertEquals(2f * 0.4f * 0.5f, node.output.value!!.patches.single().slices!![5].height, 1e-4f)

        history.undo()
        assertTrue(graph.settled.value)
        assertSame(before, node.output.value)
    }

    @Test
    fun surfaceWorkDoesNotRebuildTheSlices() = runTest {
        val graph = graph()
        val history = history(leg)
        val node = graph.sliceRowNode(history.document, legId, library)
        runCurrent()
        val before = node.output.value

        history.execute(SetBaseSlot(legId, SlotName("fur")))
        runCurrent()
        assertTrue(graph.settled.value)
        assertSame(before, node.output.value)
    }

    @Test
    fun anOutlineCommandRebuildsFromTheOutline() = runTest {
        val graph = graph()
        val history = history(leg)
        val node = graph.sliceRowNode(history.document, legId, library)
        runCurrent()
        val before = node.output.value

        val side = outline(0f to 0.1f, 1f to 0.1f, 1f to -0.1f, 0f to -0.1f)
        history.execute(SetOutline(legId, View.SIDE, side))
        assertFalse(graph.settled.value)
        runCurrent()
        assertNotEquals(before, node.output.value)
        assertEquals(0.2f, node.output.value!!.patches.single().slices!![5].height, 1e-3f)

        history.execute(SetOutline(legId, View.SIDE, null))
        assertTrue(graph.settled.value)
        assertSame(before, node.output.value)
    }

    @Test
    fun anotherPartsChangeDoesNotDirtyTheNode() = runTest {
        val graph = graph()
        val history = history(leg, tail)
        val node = graph.sliceRowNode(history.document, legId, library)
        runCurrent()
        val before = node.output.value
        history.execute(SetParameter(tail.id, "thickness", 0.2f))
        assertTrue(graph.settled.value)
        assertSame(before, node.output.value)
    }

    @Test
    fun aRemovedPartHasNoPatches() = runTest {
        val graph = graph()
        val history = history(leg)
        val node = graph.sliceRowNode(history.document, legId, library)
        runCurrent()
        history.execute(RemovePart(legId))
        runCurrent()
        assertEquals(emptyList(), node.output.value?.patches)
        history.execute(AddPart(leg))
        runCurrent()
        assertEquals(1, node.output.value?.patches?.size)
    }

    @Test
    fun anUnknownTemplateFailsTheRunAndStillSettles() = runTest {
        val graph = graph()
        val orphan = PartInstance(PartId("x"), TemplateRef(TemplateId("wing"), 1))
        val node = graph.sliceRowNode(history(orphan).document, orphan.id, library)
        runCurrent()
        assertTrue(graph.settled.value)
        assertIs<IllegalArgumentException>(node.failure.value)
    }
}
