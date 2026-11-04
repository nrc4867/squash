package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** A dog on four legs and paws through the real graph, for the tests of legs that lean. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class LeaningLegsBench {
    protected val library = TemplateLibrary(shipped.parts, shipped.plans)
    protected val plan = BodyPlanRef(BodyPlanId("canine-quadruped"), 1)

    private fun ref(id: String) = TemplateRef(TemplateId(id), 1)
    protected val torso = PartInstance(PartId("torso"), ref("torso"))

    protected fun leg(id: String, socket: String, pair: String, side: Side) =
        PartInstance(PartId(id), ref("leg"), SocketRef(torso.id, socket), pair = PairId(pair), side = side)

    protected fun paw(id: String, leg: PartInstance) =
        PartInstance(PartId(id), ref("paw"), SocketRef(leg.id, "paw"), pair = PairId("${leg.pair?.value}-paws"), side = leg.side)

    protected val frontLeft = leg("front-l", "front-leg", "front", Side.LEFT)
    protected val frontRight = leg("front-r", "front-leg", "front", Side.RIGHT)
    protected val backLeft = leg("back-l", "back-leg", "back", Side.LEFT)
    protected val backRight = leg("back-r", "back-leg", "back", Side.RIGHT)
    protected val legs = listOf(frontLeft, frontRight, backLeft, backRight)
    protected val paws = legs.map { paw("${it.id.value}-paw", it) }

    protected fun TestScope.session(parts: List<PartInstance> = listOf(torso) + legs + paws): CharacterSession {
        val empty = SquashDocument(Character(CharacterId("dog"), "Dog", plan))
        val session = CharacterSession(AddParts(parts).apply(empty).document, library, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return session
    }

    protected fun TestScope.apply(session: CharacterSession, vararg commands: DocumentCommand) {
        commands.forEach(session::send)
        runCurrent()
    }

    protected fun CharacterSession.built(): AssembledCharacter {
        assertTrue(graph.settled.value)
        return assertNotNull(assembly.output.value)
    }

    protected fun AssembledCharacter.of(part: PartInstance): PlacedPart = assertNotNull(part(part.id))

    /** How far apart the paws on show stand. */
    protected fun AssembledCharacter.spread(): Float {
        val lows = parts.filter { it.template.id.value == "paw" }.map { assertNotNull(it.box).low.y }
        return lows.max() - lows.min()
    }

    /** How far under [leg]'s socket the origin of [paw] on it lies. */
    protected fun AssembledCharacter.reachOf(leg: PartInstance, paw: PartInstance): Float =
        assertNotNull(of(leg).anchor).y - of(paw).origin.y

    /** The furthest any paw on show lies above the ground. */
    protected fun AssembledCharacter.residual(): Float =
        parts.filter { it.template.id.value == "paw" }.maxOf { assertNotNull(it.box).low.y - floor }

    protected fun assertSameVertices(expected: AssembledCharacter, actual: AssembledCharacter) {
        assertEquals(expected.parts.size, actual.parts.size)
        expected.parts.zip(actual.parts).forEach { (a, b) ->
            assertContentEquals(a.surface.mesh.vertices, b.surface.mesh.vertices, "${a.id} ${a.template.id.value} ${a.side}")
            assertEquals(a.origin, b.origin)
        }
        assertEquals(expected.floor, actual.floor)
    }

    /** How far the part reaches along its own axis. */
    protected fun PlacedPart.reach(): Float = assertNotNull(surface.extents).along.max

    protected fun outward(part: PartInstance, degrees: Float = 25f, thisSide: Boolean = false) =
        SetParameter(part.id, PlacementParams.OUTWARD, degrees, thisSide)

    protected fun lean(part: PartInstance, degrees: Float = 25f, thisSide: Boolean = false) =
        SetParameter(part.id, PlacementParams.LEAN, degrees, thisSide)
}
