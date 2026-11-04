package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SurfacePairTest : PairedFennec() {

    @Test
    fun aMarkOnOneSideReachesTheOtherOnItsOwnPart() {
        val drawn = line("m", legL)
        scenario(placed) {
            run(SetMark(legL.id, drawn.id, drawn))
            expect {
                assertEquals(drawn, it.surfaceOf(legL).marks.single())
                assertEquals(line("m", legR), it.surfaceOf(legR).marks.single())
            }
            undo()
            expect(placed)
            redo()
            expect { assertEquals(line("m", legR), it.surfaceOf(legR).marks.single()) }
        }
    }

    @Test
    fun aMarkDrawnOnTheRightReadsTheSameAsOneDrawnOnTheLeft() {
        val fromRight = SetMark(legR.id, MarkId("m"), line("m", legR)).apply(placed).document
        val fromLeft = SetMark(legL.id, MarkId("m"), line("m", legL)).apply(placed).document
        assertEquals(fromLeft, fromRight)
    }

    @Test
    fun anAnchorOnTheMiddleOfTheBodyMirrorsAcrossIt() {
        val crossing = Region(RegionId("r"), listOf(on(legL, 0.1f), on(torso, 0.25f), on(torso, 0.3f, u = 0.1f)), slot)
        val document = SetRegion(legL.id, crossing.id, crossing).apply(placed).document
        val right = document.surfaceOf(legR).regions.single().border
        assertEquals(legR.id, right[0].part)
        assertEquals(0.1f, right[0].v)
        assertEquals(torso.id, right[1].part)
        assertEquals(0.75f, right[1].v)
        assertEquals(crossing, document.surfaceOf(legL).regions.single())
    }

    @Test
    fun aRegionDrawnFromTheRightReadsAsDrawnThereAndUndoesExactly() {
        val seen = Region(RegionId("r"), listOf(on(legR, 0.1f), on(torso, 0.3f), on(torso, 0.7f)), slot)
        val command = SetRegion(legR.id, seen.id, seen)
        val written = command.apply(placed).document
        assertEquals(1f - 0.3f, written.surfaceOf(legL).regions.single().border[1].v)
        assertEquals(legL.id, written.surfaceOf(legL).regions.single().border[0].part)
        assertEquals(legR.id, written.surfaceOf(legR).regions.single().border[0].part)
        val replaced = SetRegion(legR.id, seen.id, seen.copy(slot = other)).apply(written)
        assertEquals(written, replaced.undo.apply(replaced.document).document)
    }

    @Test
    fun anAnchorOnAPairedParentFollowsToItsTwin() {
        val crossing = Mark(MarkId("sock"), listOf(on(pawL, 0.5f), on(legL, 0.5f)))
        val document = SetMark(pawL.id, crossing.id, crossing).apply(placed).document
        val right = document.surfaceOf(pawR).marks.single().points
        assertEquals(listOf(pawR.id, legR.id), right.map { it.part })
        assertEquals(listOf(0.5f, 0.5f), right.map { it.v })
    }

    @Test
    fun aMarksAngleVersionsAreReadForTheSideToo() {
        val versioned = line("m", legL).copy(versions = mapOf(CameraBin("front") to MarkVersion(listOf(on(legL, 0.1f), on(torso, 0.2f)))))
        val document = SetMark(legL.id, versioned.id, versioned).apply(placed).document
        val version = document.surfaceOf(legR).marks.single().versions.getValue(CameraBin("front")).points
        assertEquals(legR.id, version[0].part)
        assertEquals(0.8f, version[1].v)
    }

    @Test
    fun anOverrideReplacesOneItemOnOneSideAndClearingItShowsThePairsAgain() {
        val shared = line("m", legL)
        val own = Mark(MarkId("m"), listOf(on(legR, 0.1f), on(legR, 0.2f)), ink = false)
        scenario(placed) {
            run(SetMark(legL.id, shared.id, shared), SetMark(legR.id, own.id, own, thisSide = true))
            expect {
                assertEquals(shared, it.surfaceOf(legL).marks.single())
                assertEquals(own, it.surfaceOf(legR).marks.single())
            }
            run(SetMark(legR.id, own.id, null, thisSide = true))
            expect { assertEquals(line("m", legR), it.surfaceOf(legR).marks.single()) }
            undo()
            expect { assertEquals(own, it.surfaceOf(legR).marks.single()) }
        }
    }

    @Test
    fun aSideCanAddWorkOfItsOwnAfterWhatIsShared() {
        val shared = line("m", legL)
        val scar = Mark(MarkId("scar"), listOf(on(legR, 0.4f), on(legR, 0.6f)))
        val document = listOf(SetMark(legL.id, shared.id, shared), SetMark(legR.id, scar.id, scar, thisSide = true))
            .fold(placed) { d, c -> c.apply(d).document }
        assertEquals(listOf("m", "scar"), document.surfaceOf(legR).marks.map { it.id.value })
        assertEquals(listOf("m"), document.surfaceOf(legL).marks.map { it.id.value })
    }

    @Test
    fun baseSlotAndGrainAreSharedWithOverrides() {
        val combed = Grain(listOf(GrainComb(u = 0.5f, v = 0.5f, du = 0f, dv = 1f)))
        val combedAlone = Grain(listOf(GrainComb(u = 0.1f, v = 0.1f, du = 1f, dv = 0f)))
        scenario(placed) {
            run(SetBaseSlot(legL.id, slot), SetGrain(legR.id, combed))
            expect {
                assertEquals(slot, it.surfaceOf(legR).baseSlot)
                assertEquals(combed, it.surfaceOf(legL).grain)
            }
            run(SetBaseSlot(legR.id, other, thisSide = true), SetGrain(legR.id, combedAlone, thisSide = true))
            expect {
                assertEquals(slot, it.surfaceOf(legL).baseSlot)
                assertEquals(other, it.surfaceOf(legR).baseSlot)
                assertEquals(combed, it.surfaceOf(legL).grain)
                assertEquals(combedAlone, it.surfaceOf(legR).grain)
            }
            undo()
            undo()
            expect { assertEquals(slot, it.surfaceOf(legR).baseSlot) }
            undo()
            undo()
            expect(placed)
        }
    }

    @Test
    fun anAreaFillIsSharedWithOverridesThatClearBackToThePairs() {
        scenario(placed) {
            run(SetAreaFill(legL.id, "pads", slot))
            expect { assertEquals(slot, it.surfaceOf(legR).areaFills["pads"]) }
            run(SetAreaFill(legR.id, "pads", other, thisSide = true))
            expect {
                assertEquals(slot, it.surfaceOf(legL).areaFills["pads"])
                assertEquals(other, it.surfaceOf(legR).areaFills["pads"])
            }
            run(SetAreaFill(legR.id, "pads", null, thisSide = true))
            expect { assertEquals(slot, it.surfaceOf(legR).areaFills["pads"]) }
            undo()
            undo()
            undo()
            expect(placed)
        }
    }

    @Test
    fun controlsOnOneSideLeaveTheOtherAndUndoToTheShared() {
        val eye = eyeGroup(MarkGroupId("eye"), "Eye")
        scenario(placed) {
            run(SetMarkGroup(legL.id, eye.id, eye), SetMarkControls(legR.id, eye.id, MarkControls(open = 0.5f), thisSide = true))
            expect {
                assertEquals(1f, it.surfaceOf(legL).groups.single().controls.open)
                assertEquals(0.5f, it.surfaceOf(legR).groups.single().controls.open)
            }
            undo()
            expect {
                assertEquals(1f, it.surfaceOf(legR).groups.single().controls.open)
                assertEquals(SurfaceWork(), it.character.overridesOf(checkNotNull(it.character.part(legR.id))).surface)
            }
            run(SetMarkControls(legR.id, eye.id, MarkControls(open = 0.25f)))
            expect { assertEquals(0.25f, it.surfaceOf(legL).groups.single().controls.open) }
        }
    }

    @Test
    fun breakingAPairKeepsWhatEachSideShowsAndUndoesExactly() {
        val shared = Region(RegionId("r"), listOf(on(legL, 0.1f), on(torso, 0.25f), on(legL, 0.4f)), slot)
        val own = Mark(MarkId("m"), listOf(on(legR, 0.1f), on(legR, 0.2f)))
        val before = listOf(SetRegion(legL.id, shared.id, shared), SetMark(legR.id, own.id, own, thisSide = true), SetBaseSlot(legL.id, slot))
            .fold(placed) { d, c -> c.apply(d).document }
        val leftShows = before.surfaceOf(legL)
        val rightShows = before.surfaceOf(legR)
        scenario(before) {
            run(BreakPair(legs))
            expect {
                assertEquals(leftShows, it.surfaceOf(legL))
                assertEquals(rightShows, it.surfaceOf(legR))
                assertNull(it.character.part(legR.id)?.pair)
            }
            undo()
            expect(before)
            redo()
            expect { assertEquals(rightShows, it.surfaceOf(legR)) }
        }
    }

    fun anUndoCommandSurvivesSerialization() {
        val seen = Region(RegionId("r"), listOf(on(legR, 0.1f), on(torso, 0.3f), on(torso, 0.7f)), slot)
        val first = SetRegion(legR.id, seen.id, seen).apply(placed)
        val second = SetRegion(legR.id, seen.id, null).apply(first.document)
        val undo = second.undo as DocumentCommand
        val read = Json.decodeFromString(DocumentCommand.serializer(), Json.encodeToString(DocumentCommand.serializer(), undo))
        assertEquals(first.document, read.apply(second.document).document)
    }

    @Test
    fun aRestoredPairsExpressionsSerialize() {
        val command: DocumentCommand = RestorePair(legs, PartData(), mapOf(legL.id to PartData()), mapOf(ExpressionId("glad") to mapOf(MarkGroupId("eye") to RoleMarks())))
        assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), Json.encodeToString(DocumentCommand.serializer(), command)))
    }

    @Test
    fun theShapeLeavesOutTheSurface() {
        val data = PartData(params = mapOf("size" to 1f), surface = SurfaceWork(baseSlot = slot))
        assertEquals(PartData(params = mapOf("size" to 1f)), data.shape)
    }

    @Test
    fun aPairsSurfaceWorkRoundTripsThroughSave() {
        val saved = listOf<DocumentCommand>(
            SetMark(legL.id, MarkId("m"), line("m", legL)),
            SetRegion(legR.id, RegionId("r"), Region(RegionId("r"), listOf(on(legR, 0.1f), on(torso, 0.3f), on(torso, 0.7f)), slot)),
            SetBaseSlot(legR.id, other, thisSide = true),
        ).fold(placed) { d, c -> c.apply(d).document }
        val read = readDocument(Json.encodeToString(SquashDocument.serializer(), saved))
        assertEquals(saved, read)
        assertEquals(saved.surfaceOf(legR), read.surfaceOf(legR))
    }
}
