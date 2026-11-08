package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private const val CLOSE = 1e-4f

/** How far paws may stand from the ground, in body units: a pixel or two at the panes' usual scale. */
private const val PAW_RESIDUAL = 0.005f

/**
 * With both angles at their limit a drawn leg's outline bends the normal its paw sinks along, and the factor reads that
 * normal at the leg's full length, so paws part by a little more than [PAW_RESIDUAL].
 */
private const val DRAWN_LIMIT_RESIDUAL = 0.006f

/** A leg's far rim is the lowest thing on the character when it leans back 60 degrees, this far under the level paws. */
private const val RIM_UNDER_PAWS_LEAN_60 = 0.045f

/** The same with 60 degrees outward as well. */
private const val RIM_UNDER_PAWS_BOTH_60 = 0.02f

/** A leg leaning keeps every paw on the ground. See HLD: data-model, Legs stay level as they lean. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaningLegsTest : LeaningLegsBench() {
    private fun TestScope.assertLevel(tolerance: Float, vararg commands: DocumentCommand, spreadTolerance: Float = PAW_RESIDUAL) {
        val session = session()
        apply(session, *commands)
        val character = session.built()
        assertTrue(character.spread() < spreadTolerance, "paws part by ${character.spread()}")
        assertTrue(character.residual() < tolerance, "paws off the ground by ${character.residual()}")
    }

    @Test
    fun anUnleanedDogStandsLevelWithEveryLegAtFullLength() = runTest {
        val character = session().built()
        assertEquals(0f, character.residual(), CLOSE)
        legs.forEach { assertEquals(1f, character.of(it).lengthFactor) }
    }

    @Test
    fun oneLegLeanedOutLeavesAllFourPawsOnTheGround() = runTest {
        val session = session()
        apply(session, outward(frontLeft, thisSide = true))
        val character = session.built()
        assertTrue(character.residual() < PAW_RESIDUAL, "paws off the ground by ${character.residual()}")
        assertEquals(1f, character.of(frontLeft).lengthFactor, "the leg that reaches least keeps its length")
        val others = listOf(frontRight, backLeft, backRight)
        others.forEach {
            val factor = character.of(it).lengthFactor
            assertTrue(factor < 1f, "${it.id} shortens")
            assertEquals(character.of(frontRight).lengthFactor, factor, "${it.id}")
            assertEquals(0.5f * factor, character.of(it).reach(), CLOSE, "${it.id} builds shorter")
        }
    }

    @Test
    fun aLeanedPairStaysLevelWithBothSidesAtFullLength() = runTest {
        val session = session()
        apply(session, outward(frontLeft))
        val character = session.built()
        assertTrue(character.residual() < PAW_RESIDUAL, "paws off the ground by ${character.residual()}")
        assertEquals(1f, character.of(frontRight).lengthFactor)
    }

    @Test
    fun aLeanEitherWayStaysLevel() = runTest {
        assertLevel(PAW_RESIDUAL, lean(frontLeft))
        assertLevel(PAW_RESIDUAL, lean(frontLeft, -25f))
    }

    @Test
    fun aLeanAndAnOutwardTogetherStayLevel() = runTest {
        assertLevel(PAW_RESIDUAL, outward(backLeft), lean(backLeft))
        assertLevel(PAW_RESIDUAL, outward(backLeft, -25f), lean(backLeft, -25f))
        assertLevel(PAW_RESIDUAL, outward(frontLeft), lean(backLeft, -25f))
    }

    @Test
    fun theLimitsStayLevel() = runTest {
        assertLevel(PAW_RESIDUAL, outward(frontLeft, 60f))
        assertLevel(PAW_RESIDUAL, lean(frontLeft, 60f))
        assertLevel(PAW_RESIDUAL, outward(frontLeft, 60f), lean(frontLeft, 60f))
    }

    @Test
    fun atTheLimitsLeaningBackALegsRimReachesBelowItsLevelPaws() = runTest {
        assertLevel(RIM_UNDER_PAWS_LEAN_60, lean(frontLeft, -60f))
        assertLevel(RIM_UNDER_PAWS_BOTH_60, outward(frontLeft, 60f), lean(frontLeft, -60f))
    }

    @Test
    fun aSideWithMirrorOffLeansAloneAndItsTwinShortens() = runTest {
        val session = session()
        apply(session, outward(frontRight, 30f, thisSide = true))
        val character = session.built()
        assertEquals(1f, character.of(frontRight).lengthFactor)
        assertTrue(character.of(frontLeft).lengthFactor < 1f)
        assertEquals(character.of(frontLeft).lengthFactor, character.of(backRight).lengthFactor)
        assertTrue(character.residual() < PAW_RESIDUAL, "paws off the ground by ${character.residual()}")
    }

    @Test
    fun drawnLegsOfOneLengthStayLevelWhateverTheirEndsDo() = runTest {
        val flat = outline(0f to 0.08f, 0.45f to 0.06f, 0.45f to -0.06f, 0f to -0.08f)
        val tip = outline(0f to 0.1f, 0.5f to 0f, 0f to -0.1f)
        val offCentre = outline(0f to 0.12f, 0.45f to 0.14f, 0.45f to 0.02f, 0f to -0.04f)
        for (shape in listOf(flat, tip, offCentre)) {
            fun drawn(vararg lean: DocumentCommand) =
                arrayOf<DocumentCommand>(SetOutline(frontLeft.id, View.SIDE, shape), SetOutline(backLeft.id, View.SIDE, shape)) + lean
            assertLevel(PAW_RESIDUAL, *drawn(lean(frontLeft)))
            assertLevel(PAW_RESIDUAL, *drawn(lean(frontLeft, -25f)))
            assertLevel(PAW_RESIDUAL, *drawn(outward(frontLeft)))
            assertLevel(PAW_RESIDUAL, *drawn(outward(frontLeft), lean(frontLeft)))
            assertLevel(DRAWN_LIMIT_RESIDUAL, *drawn(outward(frontLeft, 60f), lean(frontLeft, 60f)), spreadTolerance = DRAWN_LIMIT_RESIDUAL)
        }
    }

    @Test
    fun drawnLegsKeepTheirRatioAndTheirStoredOutlines() = runTest {
        val session = session()
        val long = outline(0f to 0.1f, 0.6f to 0.08f, 0.6f to -0.08f, 0f to -0.1f)
        val short = outline(0f to 0.1f, 0.4f to 0.08f, 0.4f to -0.08f, 0f to -0.1f)
        apply(session, SetOutline(backLeft.id, View.SIDE, long), SetOutline(frontLeft.id, View.SIDE, short))
        val square = session.built()
        assertEquals(0.4f / 0.6f, square.of(frontLeft).reach() / square.of(backLeft).reach(), CLOSE)
        apply(session, outward(frontLeft))
        val character = session.built()
        fun share(leg: PartInstance, paw: PartInstance, length: Float) =
            (character.reachOf(leg, paw) - square.reachOf(leg, paw) + length) / length
        assertEquals(share(frontLeft, paws[0], 0.4f), share(backLeft, paws[2], 0.6f), 1e-3f, "each reaches the same share of its length")
        assertEquals(long, session.document.value.character.resolved(backLeft)?.outlines?.get(View.SIDE), "the document keeps what was drawn")
    }

    @Test
    fun aShortDrawnLegWithNoLeanShortensNothing() = runTest {
        val session = session()
        val square = session.built()
        apply(session, SetOutline(frontLeft.id, View.SIDE, outline(0f to 0.1f, 0.2f to 0.08f, 0.2f to -0.08f, 0f to -0.1f)))
        val character = session.built()
        legs.forEach { assertEquals(1f, character.of(it).lengthFactor, "${it.id}") }
        assertContentEquals(square.of(backLeft).surface.mesh.vertices, character.of(backLeft).surface.mesh.vertices)
    }

    @Test
    fun plannedLegsShortenSoAHalfBuiltDogStaysLevel() = runTest {
        val session = session(listOf(torso, frontLeft, frontRight))
        apply(session, lean(frontLeft))
        val character = session.built()
        val planned = character.parts.filter { it.planned && it.template.id.value == "leg" }
        assertEquals(2, planned.size)
        planned.forEach {
            assertTrue(it.lengthFactor < 1f)
            assertEquals(0.5f * it.lengthFactor, it.reach(), CLOSE)
        }
        assertTrue(character.residual() < PAW_RESIDUAL, "paws off the ground by ${character.residual()}")
    }

    @Test
    fun aDogWithNoLegPlacedHasNothingToShorten() = runTest {
        val character = session(listOf(torso)).built()
        character.parts.forEach { assertEquals(1f, it.lengthFactor, it.template.id.value) }
    }

    @Test
    fun assemblingAtOnceAgreesWithTheGraph() = runTest {
        val session = session()
        apply(session, lean(frontLeft))
        val character = session.document.value.character
        val plan = assertNotNull(library.plan(character.bodyPlan))
        val now = assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, plan, library)
        assertSameVertices(session.built(), now)
    }
}
