package dev.chieppa.squash.document

import dev.chieppa.squash.document.WinterFennec.BARE
import dev.chieppa.squash.document.WinterFennec.BODY_SLOT
import dev.chieppa.squash.document.WinterFennec.BOOTS
import dev.chieppa.squash.document.WinterFennec.FEET_SLOT
import dev.chieppa.squash.document.WinterFennec.HAT
import dev.chieppa.squash.document.WinterFennec.HEAD
import dev.chieppa.squash.document.WinterFennec.HEAD_SLOT
import dev.chieppa.squash.document.WinterFennec.LEG_L
import dev.chieppa.squash.document.WinterFennec.SANDALS
import dev.chieppa.squash.document.WinterFennec.SWEATER
import dev.chieppa.squash.document.WinterFennec.TORSO
import dev.chieppa.squash.document.WinterFennec.WINTER
import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private val SquashDocument.wardrobe get() = character.wardrobe

class WardrobeCommandTest {
    @Test
    fun everyDressingStepUndoesToTheBareBody() {
        val start = WinterFennec.body()
        scenario(start) {
            WinterFennec.dressing.forEach { run(it) }
            expect { assertEquals(listOf(HAT, SWEATER, BOOTS).toSet(), it.character.worn.map { g -> g.id }.toSet()) }
            WinterFennec.dressing.forEach { _ -> undo() }
            expect(start)
        }
    }

    @Test
    fun switchingTheOutfitIsOneStepAndUndoesInOne() {
        val start = WinterFennec.dressed()
        scenario(start) {
            run(ShowOutfit(BARE))
            expect { assertEquals(emptyList(), it.character.worn) }
            undo()
            expect(start)
        }
    }

    @Test
    fun outfitsMixAndMatchOneGarmentPerSlot() {
        val start = WinterFennec.dressed()
        scenario(start) {
            run(PickGarment(BARE, FEET_SLOT, SANDALS))
            run(ShowOutfit(BARE))
            expect { assertEquals(listOf(SANDALS), it.character.worn.map { g -> g.id }) }
            run(PickGarment(WINTER, FEET_SLOT, SANDALS))
            run(ShowOutfit(WINTER))
            expect { assertEquals(setOf(HAT, SWEATER, SANDALS), it.character.worn.map { g -> g.id }.toSet()) }
        }
    }

    @Test
    fun anOutfitPicksOnlyAGarmentOfThatSlot() {
        assertFailsWith<IllegalArgumentException> { PickGarment(WINTER, HEAD_SLOT, BOOTS).apply(WinterFennec.dressed()) }
    }

    @Test
    fun wornGarmentsComeInnermostFirst() {
        val start = WinterFennec.dressed()
        scenario(start) {
            run(SetLayers(listOf(BOOTS, SWEATER, HAT)))
            expect { assertEquals(listOf(BOOTS, SWEATER, HAT), it.character.worn.map { g -> g.id }) }
            run(SetLayers(listOf(HAT, BOOTS, SWEATER)))
            expect { assertEquals(listOf(HAT, BOOTS, SWEATER), it.character.worn.map { g -> g.id }) }
            undo()
            undo()
            expect(start)
        }
    }

    @Test
    fun removingAGarmentDropsItsPicksAndUndoRestoresThem() {
        val start = WinterFennec.dressed()
        scenario(start) {
            run(RemoveGarment(BOOTS))
            expect { assertNull(it.wardrobe.outfit(WINTER)?.picks?.get(FEET_SLOT)) }
            undo()
            expect(start)
        }
    }

    @Test
    fun removingASlotEmptiesItsGarmentsAndPicks() {
        val start = WinterFennec.dressed()
        scenario(start) {
            run(RemoveGarmentSlot(FEET_SLOT))
            expect { assertNull(it.wardrobe.garment(BOOTS)?.slot) }
            expect { assertEquals(setOf(HEAD_SLOT, BODY_SLOT), it.wardrobe.outfit(WINTER)?.picks?.keys) }
            undo()
            expect(start)
        }
    }

    @Test
    fun movingAGarmentToAnotherSlotDropsItsPicks() {
        val start = WinterFennec.dressed()
        scenario(start) {
            run(SetGarmentSlot(BOOTS, BODY_SLOT))
            expect { assertNull(it.wardrobe.outfit(WINTER)?.picks?.get(FEET_SLOT)) }
            expect { assertEquals(SWEATER, it.wardrobe.outfit(WINTER)?.picks?.get(BODY_SLOT)) }
            undo()
            expect(start)
        }
    }

    @Test
    fun removingTheShownOutfitShowsTheBareBody() {
        val start = WinterFennec.dressed()
        scenario(start) {
            run(RemoveOutfit(WINTER))
            expect { assertNull(it.wardrobe.shown) }
            undo()
            expect(start)
        }
    }

    @Test
    fun namesChange() {
        val start = WinterFennec.dressed()
        scenario(start) {
            run(RenameGarment(HAT, "Beanie"))
            run(RenameGarmentSlot(HEAD_SLOT, "Crown"))
            run(RenameOutfit(WINTER, "Snow"))
            expect { assertEquals("Beanie", it.wardrobe.garment(HAT)?.name) }
            expect { assertEquals("Crown", it.wardrobe.slot(HEAD_SLOT)?.name) }
            expect { assertEquals("Snow", it.wardrobe.outfit(WINTER)?.name) }
            undo()
            undo()
            undo()
            expect(start)
        }
    }

    @Test
    fun branchesTakeTheirRangeEaseOutlinesAndSurface() {
        val start = WinterFennec.dressed()
        val curve = OutlineCurve(listOf(CurvePoint(0f, 0f), CurvePoint(0.3f, 0.2f), CurvePoint(0.7f, 0.2f), CurvePoint(1f, 0f)))
        scenario(start) {
            run(SetBranchRange(SWEATER, LEG_L, 0f, 1.2f))
            run(SetBranchEase(SWEATER, LEG_L, 0.05f))
            run(SetBranchAround(SWEATER, LEG_L, -0.25f, 0.25f))
            run(SetBranchOutline(SWEATER, TORSO, View.SIDE, curve))
            run(SetBranchSurface(SWEATER, TORSO, SurfaceWork(baseSlot = SlotName("fur"))))
            expect {
                val leg = it.wardrobe.garment(SWEATER)?.branch(LEG_L)
                assertEquals(1.2f, leg?.to)
                assertEquals(0.05f, leg?.ease)
                assertEquals(false, leg?.allRound)
                assertEquals(curve, it.wardrobe.garment(SWEATER)?.branch(TORSO)?.outlines?.get(View.SIDE))
                assertEquals(SlotName("fur"), it.wardrobe.garment(SWEATER)?.branch(TORSO)?.surface?.baseSlot)
            }
            run(SetBranchOutline(SWEATER, TORSO, View.SIDE, null))
            expect { assertEquals(emptyMap(), it.wardrobe.garment(SWEATER)?.branch(TORSO)?.outlines) }
            repeat(6) { undo() }
            expect(start)
        }
    }

    @Test
    fun aBranchOverAPartNoAreaPaintsHasItsBoxPaintedAndAPaintedOneIsLeft() {
        val start = WinterFennec.dressed()
        val leg = cellsOfRuns(checkNotNull(start.character.wardrobe.garment(SWEATER)).areas.single().cells.getValue(LEG_L))
        assertEquals((0 until CHART_CELLS).map { it / CHART_COLUMNS < 51 }, leg.toList(), "the sleeve to 0.8 holds the rows whose middles lie under it")
        scenario(start) {
            run(CoverPart(SWEATER, GarmentBranch(LEG_L, 0.5f, 0.6f)))
            expect { assertEquals(start.character.wardrobe.garment(SWEATER)?.areas, it.wardrobe.garment(SWEATER)?.areas, "a painted part keeps its cells") }
            run(CoverPart(HAT, GarmentBranch(TORSO, 0.9f, 1.3f)))
            expect {
                val hat = checkNotNull(it.wardrobe.garment(HAT))
                val torso = cellsOfRuns(hat.areas.single().cells.getValue(TORSO))
                assertEquals((0 until CHART_CELLS).map { cell -> cell / CHART_COLUMNS >= 58 }, torso.toList(), "a box past the end paints to the end")
                assertEquals(1.3f, hat.branch(TORSO)?.to, "and runs on past it")
                assertEquals(0.9f, hat.branch(TORSO)?.from, "its range stays as given")
            }
            repeat(2) { undo() }
            expect(start)
        }
    }

    @Test
    fun paintedAreasHolesAndTucksAreSetAndUndone() {
        val start = WinterFennec.dressed()
        val area = GarmentArea(GarmentAreaId("area-1"), "Area 1", mapOf(LEG_L to listOf(0, 2048)))
        val hole = GarmentHole(TORSO, cuff = true)
        scenario(start) {
            run(SetGarmentPaint(SWEATER, listOf(area), listOf(hole)))
            run(RenameGarmentArea(SWEATER, area.id, "Sleeve"))
            run(SetTuck(SWEATER, LEG_L, TuckKind.TOES, false))
            run(SetTuck(SWEATER, LEG_L, TuckKind.TOES, true))
            expect {
                val sweater = checkNotNull(it.wardrobe.garment(SWEATER))
                assertEquals(listOf(area.copy(name = "Sleeve")), sweater.areas)
                assertEquals(listOf(hole), sweater.holes)
                assertEquals(listOf(TuckChoice(LEG_L, TuckKind.TOES, true)), sweater.tucks)
            }
            run(SetTuck(SWEATER, LEG_L, TuckKind.TOES, null))
            expect { assertEquals(emptyList(), it.wardrobe.garment(SWEATER)?.tucks) }
            repeat(5) { undo() }
            expect(start)
        }
    }

    @Test
    fun printsAndAreaColorsAreSetReplacedRemovedAndUndone() {
        val start = WinterFennec.dressed()
        val area = GarmentArea(GarmentAreaId("area-1"), "Area 1", mapOf(LEG_L to listOf(0, 2048)))
        val border = listOf(Anchor(TORSO, BODY_PATCH, 0.5f, 0.1f), Anchor(TORSO, BODY_PATCH, 0.6f, 0.1f), Anchor(TORSO, BODY_PATCH, 0.55f, 0.2f))
        val region = Region(RegionId("print-1"), border, SlotName("cream"), ink = true, mirrored = true)
        val mark = Mark(MarkId("line-1"), border.take(2), slot = SlotName("cream"))
        scenario(start) {
            run(SetGarmentPaint(SWEATER, listOf(area), emptyList()))
            run(SetGarmentAreaColor(SWEATER, area.id, SlotName("cream")))
            run(SetGarmentRegion(SWEATER, region.id, region))
            run(SetGarmentMark(SWEATER, mark.id, mark))
            run(SetGarmentRegion(SWEATER, region.id, region.copy(ink = false)))
            expect {
                val sweater = checkNotNull(it.wardrobe.garment(SWEATER))
                assertEquals(SlotName("cream"), sweater.area(area.id)?.color)
                assertEquals(listOf(region.copy(ink = false)), sweater.regions)
                assertEquals(listOf(mark), sweater.marks)
                assertEquals(sweater.copy(regions = emptyList(), marks = emptyList(), areas = listOf(area)), sweater.unprinted)
            }
            run(SetGarmentMark(SWEATER, mark.id, null))
            run(SetGarmentAreaColor(SWEATER, area.id, null))
            expect {
                val sweater = checkNotNull(it.wardrobe.garment(SWEATER))
                assertEquals(emptyList(), sweater.marks)
                assertNull(sweater.area(area.id)?.color)
            }
            repeat(7) { undo() }
            expect(start)
        }
    }

    @Test
    fun aPrintIsALineOnPartsTheDocumentHolds() {
        val start = WinterFennec.dressed()
        val fur = Mark(MarkId("line-1"), listOf(Anchor(TORSO, BODY_PATCH, 0.5f, 0.1f)), kind = MarkKind.FUR)
        val nowhere = Region(RegionId("print-1"), List(3) { Anchor(PartId("nowhere"), BODY_PATCH, 0.5f, it * 0.1f) }, SlotName("cream"))
        assertFailsWith<IllegalArgumentException> { SetGarmentMark(SWEATER, fur.id, fur).apply(start) }
        assertFailsWith<IllegalArgumentException> { SetGarmentRegion(SWEATER, nowhere.id, nowhere).apply(start) }
        assertFailsWith<IllegalArgumentException> { SetGarmentRegion(SWEATER, nowhere.id, null).apply(start) }
    }

    @Test
    fun inkFitColorAndLooseEnds() {
        val start = WinterFennec.dressed()
        val end = LooseEnd(LooseEndId("tail"), TORSO)
        scenario(start) {
            run(SetJoinInk(SWEATER, LEG_L, true))
            run(SetHemInk(SWEATER, false))
            run(SetGarmentFit(SWEATER, 0.05f))
            run(SetPartFit(SWEATER, LEG_L, 0.08f))
            run(SetGarmentColor(SWEATER, SlotName("cream")))
            run(SetLooseEnd(SWEATER, end))
            run(SetLooseEnd(SWEATER, end.copy(bones = 3)))
            expect {
                val sweater = checkNotNull(it.wardrobe.garment(SWEATER))
                assertEquals(setOf(LEG_L), sweater.joins)
                assertEquals(false, sweater.inkHem)
                assertEquals(0.05f, sweater.fit)
                assertEquals(mapOf(LEG_L to 0.08f), sweater.partFits)
                assertEquals(0.08f, sweater.easeOf(checkNotNull(sweater.branch(LEG_L))))
                assertEquals(SlotName("cream"), sweater.color)
                assertEquals(listOf(end.copy(bones = 3)), sweater.looseEnds)
            }
            run(UncoverPart(SWEATER, LEG_L))
            expect { assertEquals(emptySet(), it.wardrobe.garment(SWEATER)?.joins) }
            run(RemoveLooseEnd(SWEATER, end.id))
            expect { assertEquals(emptyList(), it.wardrobe.garment(SWEATER)?.looseEnds) }
            repeat(9) { undo() }
            expect(start)
        }
    }

    @Test
    fun aGarmentCoversOnlyPartsOfTheCharacter() {
        assertFailsWith<IllegalArgumentException> { CoverPart(HAT, GarmentBranch(PartId("wing"))).apply(WinterFennec.dressed()) }
    }

    @Test
    fun coveringAPartAgainReplacesItsBranch() {
        val document = CoverPart(HAT, GarmentBranch(HEAD, from = 0.3f)).apply(WinterFennec.dressed()).document
        assertEquals(listOf(0.3f), document.wardrobe.garment(HAT)?.branches?.map { it.from })
    }

    @Test
    fun noPartTakesAGarmentBranchsId() {
        val dressed = WinterFennec.dressed()
        assertFailsWith<IllegalArgumentException> {
            AddPart(PartInstance(branchPartId(HAT, HEAD), WinterFennec.ref("tube"))).apply(dressed)
        }
    }

    @Test
    fun duplicatingAnOutfitCopiesItsPicksAndShowsTheCopyInOneStep() {
        val start = WinterFennec.dressed()
        val copy = OutfitId("outfit-1")
        scenario(start) {
            run(DuplicateOutfit(WINTER, copy, "Winter 2"))
            expect {
                val wardrobe = it.wardrobe
                assertEquals(wardrobe.outfit(WINTER)?.picks, wardrobe.outfit(copy)?.picks)
                assertEquals(copy, wardrobe.shown)
                assertEquals(wardrobe.outfits.indexOfFirst { o -> o.id == WINTER } + 1, wardrobe.outfits.indexOfFirst { o -> o.id == copy })
            }
            undo()
            expect(start)
        }
    }

    @Test
    fun aNewWornGarmentTakesItsSlotInTheOutfitInOneStep() {
        val start = WinterFennec.dressed()
        val socks = Garment(GarmentId("garment-1"), "Garment 1", slot = FEET_SLOT)
        scenario(start) {
            run(AddWornGarment(socks, WINTER))
            expect {
                assertEquals(socks.id, it.wardrobe.outfit(WINTER)?.picks?.get(FEET_SLOT))
                assertEquals(socks, it.wardrobe.garment(socks.id))
            }
            undo()
            expect(start)
            run(AddWornGarment(socks.copy(id = GarmentId("garment-2")), null))
            expect { assertEquals(start.wardrobe.outfits, it.wardrobe.outfits) }
        }
        assertFailsWith<IllegalArgumentException> { AddWornGarment(socks.copy(slot = null), WINTER).apply(start) }
    }

    @Test
    fun theNewWardrobeCommandsSerialize() {
        val commands = listOf<DocumentCommand>(
            DuplicateOutfit(WINTER, OutfitId("outfit-1"), "Winter 2"),
            AddWornGarment(Garment(GarmentId("garment-1"), "Garment 1", slot = FEET_SLOT), WINTER),
        )
        commands.forEach { command ->
            assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), Json.encodeToString(DocumentCommand.serializer(), command)))
        }
    }

    @Test
    fun everyCommandAndItsUndoSerialize() {
        val json = Json
        var document = WinterFennec.body()
        for (command in WinterFennec.dressing) {
            val applied = command.apply(document)
            val undo = applied.undo as DocumentCommand
            assertEquals(command, json.decodeFromString(DocumentCommand.serializer(), json.encodeToString(DocumentCommand.serializer(), command)))
            assertEquals(undo, json.decodeFromString(DocumentCommand.serializer(), json.encodeToString(DocumentCommand.serializer(), undo)))
            document = applied.document
        }
    }
}
