package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.BreakPair
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairedMeshTest {
    private val pair = PairId("front-legs")
    private val ref = TemplateRef(TemplateId("leg"), 1)
    private val left = PartInstance(PartId("leg-l"), ref, pair = pair, side = Side.LEFT)
    private val right = PartInstance(PartId("leg-r"), ref, pair = pair, side = Side.RIGHT)
    private val tail = PartInstance(PartId("tail-1"), TemplateRef(TemplateId("tail"), 1))
    private val first = outline(0f to 0.2f, 1f to 0.1f, 1f to -0.1f, 0f to -0.2f)
    private val second = outline(0f to 0.3f, 1f to 0.1f, 1f to -0.1f, 0f to -0.3f)

    /** A session with the pair and a tail placed and every mesh built. */
    private fun TestScope.session(): CharacterSession {
        val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
        val session = CharacterSession(
            AddParts(listOf(left, right, tail)).apply(empty).document,
            TemplateLibrary(shipped.parts),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        listOf(left, right, tail).forEach { session.mesh(it.id) }
        runCurrent()
        return session
    }

    private fun CharacterSession.body(part: PartInstance) = mesh(part.id).output.value?.patches?.single()?.patch

    @Test
    fun oneOutlineCommandRebuildsBothSidesMeshes() = runTest {
        val session = session()
        val tailMesh = session.mesh(tail.id).output.value
        session.send(SetOutline(left.id, View.SIDE, first))
        assertFalse(session.graph.settled.value)
        runCurrent()
        assertTrue(session.graph.settled.value)
        val expected = PartBuilder.build(template("leg"), outlines = mapOf(View.SIDE to first)).single()
        assertEquals(expected, session.body(left))
        assertEquals(expected, session.body(right))
        assertSame(tailMesh, session.mesh(tail.id).output.value, "a part whose data did not change keeps its mesh")
    }

    @Test
    fun aSideOverrideRebuildsOnlyThatSide() = runTest {
        val session = session()
        session.send(SetOutline(left.id, View.SIDE, first))
        runCurrent()
        val leftMesh = session.mesh(left.id).output.value
        session.send(SetOutline(right.id, View.SIDE, second, thisSide = true))
        runCurrent()
        assertTrue(session.graph.settled.value)
        assertSame(leftMesh, session.mesh(left.id).output.value)
        assertEquals(PartBuilder.build(template("leg"), outlines = mapOf(View.SIDE to second)).single(), session.body(right))
    }

    @Test
    fun aPairWriteAnOverrideHidesLeavesThatSideAlone() = runTest {
        val session = session()
        session.send(SetOutline(right.id, View.SIDE, second, thisSide = true))
        runCurrent()
        val rightMesh = session.mesh(right.id).output.value
        session.send(SetOutline(left.id, View.SIDE, first))
        runCurrent()
        assertSame(rightMesh, session.mesh(right.id).output.value)
        assertNotEquals(session.body(left), session.body(right))
    }

    @Test
    fun breakingAPairRebuildsNothingAndThenTheSidesPartWays() = runTest {
        val session = session()
        session.send(SetParameter(left.id, "thickness", 0.4f))
        runCurrent()
        val leftMesh = session.mesh(left.id).output.value
        val rightMesh = session.mesh(right.id).output.value
        session.send(BreakPair(pair))
        assertTrue(session.graph.settled.value, "what each side resolves to is unchanged")
        assertSame(leftMesh, session.mesh(left.id).output.value)
        assertSame(rightMesh, session.mesh(right.id).output.value)
        session.send(SetParameter(left.id, "thickness", 0.2f))
        runCurrent()
        assertSame(rightMesh, session.mesh(right.id).output.value)
        assertNotEquals(leftMesh, session.mesh(left.id).output.value)
    }
}
