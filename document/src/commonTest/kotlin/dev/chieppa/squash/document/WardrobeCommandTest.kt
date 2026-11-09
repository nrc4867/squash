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
    fun inkFilletColorAndLooseEnds() {
        val start = WinterFennec.dressed()
        val end = LooseEnd(LooseEndId("tail"), TORSO)
        scenario(start) {
            run(SetJoinInk(SWEATER, LEG_L, true))
            run(SetEdgeInk(SWEATER, hem = false, cuffs = true))
            run(SetGarmentFillet(SWEATER, 0.5f))
            run(SetGarmentColor(SWEATER, SlotName("cream")))
            run(SetLooseEnd(SWEATER, end))
            run(SetLooseEnd(SWEATER, end.copy(bones = 3)))
            expect {
                val sweater = checkNotNull(it.wardrobe.garment(SWEATER))
                assertEquals(setOf(LEG_L), sweater.joins)
                assertEquals(false, sweater.inkHem)
                assertEquals(0.5f, sweater.fillet)
                assertEquals(SlotName("cream"), sweater.color)
                assertEquals(listOf(end.copy(bones = 3)), sweater.looseEnds)
            }
            run(UncoverPart(SWEATER, LEG_L))
            expect { assertEquals(emptySet(), it.wardrobe.garment(SWEATER)?.joins) }
            run(RemoveLooseEnd(SWEATER, end.id))
            expect { assertEquals(emptyList(), it.wardrobe.garment(SWEATER)?.looseEnds) }
            repeat(8) { undo() }
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
