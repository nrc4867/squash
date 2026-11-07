package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeamCommandTest : PairedFennec() {
    private fun stroke(id: String, vararg points: Anchor, toward: SeamSide = SeamSide.PARENT) =
        SeamStroke(SeamStrokeId(id), points.toList(), SeamBrush.PAINT, toward, radius = 0.1f, strength = 0.6f)

    private fun SquashDocument.seamsOf(part: PartInstance) = character.seamStrokes(part.id)

    @Test
    fun aSeamStrokeIsSharedByThePairAndUndoesInOneStep() {
        val painted = stroke("s", on(legL, 0.2f, u = 0.05f), on(torso, 0.3f))
        scenario(placed) {
            run(SetSeamStroke(legL.id, painted.id, painted))
            expect {
                assertEquals(painted, it.seamsOf(legL).single())
                val right = it.seamsOf(legR).single()
                assertEquals(legR.id, right.points[0].part)
                assertEquals(0.7f, right.points[1].v, 1e-6f)
            }
            undo()
            expect(placed)
        }
    }

    @Test
    fun thisSideWritesAnOverrideTheOtherSideDoesNotShow() {
        val painted = stroke("s", on(legR, 0.2f))
        scenario(placed) {
            run(SetSeamStroke(legR.id, painted.id, painted, thisSide = true))
            expect {
                assertEquals(painted, it.seamsOf(legR).single())
                assertTrue(it.seamsOf(legL).isEmpty())
            }
            undo()
            expect(placed)
        }
    }

    @Test
    fun resetTakesAwayThePairsAndBothSidesStrokesAsOneStep() {
        val shared = stroke("a", on(legL, 0.2f))
        val own = stroke("b", on(legR, 0.4f))
        val painted = SetSeamStroke(legR.id, own.id, own, thisSide = true).apply(SetSeamStroke(legL.id, shared.id, shared).apply(placed).document).document
        scenario(painted) {
            run(ResetSeam(legL.id))
            expect {
                assertTrue(it.seamsOf(legL).isEmpty())
                assertTrue(it.seamsOf(legR).isEmpty())
            }
            undo()
            expect(painted)
        }
        val sideOnly = ResetSeam(legR.id, thisSide = true).apply(painted).document
        assertEquals(listOf(shared.id), sideOnly.seamsOf(legR).map { it.id })
    }

    @Test
    fun toTwinCopiesThisSidesStrokesMirroredAsTheTwinsOverrides() {
        val own = stroke("own", on(legL, 0.2f), on(torso, 0.3f))
        val painted = SetSeamStroke(legL.id, own.id, own, thisSide = true).apply(placed).document
        assertTrue(painted.seamsOf(legR).isEmpty())
        scenario(painted) {
            run(SeamToTwin(legL.id))
            expect {
                val copied = it.seamsOf(legR).single()
                assertEquals(legR.id, copied.points[0].part)
                assertEquals(0.7f, copied.points[1].v, 1e-6f)
                assertEquals(own, it.seamsOf(legL).single())
            }
            undo()
            expect(painted)
        }
    }

    @Test
    fun tidysStrokesArriveTogetherAfterTheHeldOnes() {
        val first = stroke("first", on(legL, 0.2f))
        val painted = SetSeamStroke(legL.id, first.id, first).apply(placed).document
        val dabs = listOf(stroke("t1", on(legL, 0.5f), toward = SeamSide.CHILD).copy(dabs = true), stroke("t2", on(torso, 0.4f)).copy(dabs = true))
        scenario(painted) {
            run(AddSeamStrokes(legL.id, dabs))
            expect { assertEquals(listOf("first", "t1", "t2"), it.seamsOf(legL).map { s -> s.id.value }) }
            undo()
            expect(painted)
        }
    }

    @Test
    fun seamStrokesHideFromWhatTheSurfaceShows() {
        val painted = stroke("s", on(legL, 0.2f))
        val document = SetSeamStroke(legL.id, painted.id, painted).apply(placed).document
        assertTrue(document.surfaceOf(legL).shown.seams.isEmpty())
        assertEquals(placed.surfaceOf(legL), document.surfaceOf(legL).shown)
    }

    @Test
    fun seamCommandsSurviveJson() {
        val commands: List<DocumentCommand> = listOf(
            SetSeamStroke(legL.id, SeamStrokeId("s"), stroke("s", on(legL, 0.2f))),
            ResetSeam(legL.id, thisSide = true),
            SeamToTwin(legL.id),
            AddSeamStrokes(legL.id, listOf(stroke("d", on(torso, 0.1f)).copy(dabs = true))),
            PutSeamStrokes(listOf(SeamWrite(legR.id, true, emptyList()))),
        )
        for (command in commands) {
            val text = Json.encodeToString(DocumentCommand.serializer(), command)
            assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
        }
    }
}
