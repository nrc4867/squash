package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PairDataTest {
    private val pair = PairId("front-legs")
    private val left = PartInstance(PartId("leg-l"), TemplateRef(TemplateId("leg"), 1), pair = pair, side = Side.LEFT)
    private val right = PartInstance(PartId("leg-r"), TemplateRef(TemplateId("leg"), 1), pair = pair, side = Side.RIGHT)
    private val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    private val placed = AddParts(listOf(left, right)).apply(empty).document
    private val side = OutlineCurve(listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0f), CurvePoint(1f, 1f), CurvePoint(0f, 1f)))
    private val top = OutlineCurve(side.points.reversed())
    private val other = OutlineCurve(side.points.map { CurvePoint(it.x, it.y + 1f) })

    private fun SquashDocument.of(part: PartInstance) = character.resolved(part.id)!!

    @Test
    fun drawingOnTheLeftReachesTheRight() {
        scenario(placed) {
            run(SetOutline(left.id, View.SIDE, side))
            expect {
                assertEquals(side, it.of(left).outlines[View.SIDE])
                assertEquals(side, it.of(right).outlines[View.SIDE])
            }
            undo()
            expect(placed)
            redo()
            expect { assertEquals(side, it.of(right).outlines[View.SIDE]) }
        }
    }

    @Test
    fun aParameterOnEitherSideWritesThePair() {
        scenario(placed) {
            gesture(SetParameter(right.id, "size", 1.1f), SetParameter(right.id, "size", 1.4f))
            expect {
                assertEquals(1.4f, it.of(left).params["size"])
                assertEquals(1.4f, it.of(right).params["size"])
            }
            undo()
            expect(placed)
        }
    }

    @Test
    fun aPairedPartsOwnFieldsStayEmpty() {
        val drawn = SetOutline(left.id, View.SIDE, side).apply(placed).document
        assertEquals(PartData(), drawn.character.part(left.id)?.own)
        assertEquals(PartData(), drawn.character.part(left.id)?.overrides)
        assertEquals(PartData(outlines = mapOf(View.SIDE to side)), drawn.character.pairs[pair])
        assertFailsWith<IllegalArgumentException> { left.copy(own = PartData(params = mapOf("size" to 1f))) }
        assertFailsWith<IllegalArgumentException> { PartInstance(left.id, left.template, overrides = PartData(params = mapOf("size" to 1f))) }
    }

    @Test
    fun anOverrideReplacesOnlyTheValueItNames() {
        scenario(placed) {
            run(
                SetOutline(left.id, View.SIDE, side),
                SetOutline(left.id, View.TOP, top),
                SetParameter(left.id, "size", 1.2f),
                SetParameter(left.id, "thickness", 0.3f),
            )
            val shared = document
            run(SetOutline(right.id, View.SIDE, other, thisSide = true), SetParameter(right.id, "size", 2f, thisSide = true))
            expect {
                assertEquals(PartData(mapOf(View.SIDE to side, View.TOP to top), mapOf("size" to 1.2f, "thickness" to 0.3f)), it.of(left))
                assertEquals(PartData(mapOf(View.SIDE to other, View.TOP to top), mapOf("size" to 2f, "thickness" to 0.3f)), it.of(right))
            }
            undo(2)
            expect(shared)
            redo(2)
            expect { assertEquals(other, it.of(right).outlines[View.SIDE]) }
        }
    }

    @Test
    fun aPairWriteLeavesAnOverrideInPlaceAndClearingItShowsThePairAgain() {
        scenario(placed) {
            run(SetOutline(right.id, View.SIDE, other, thisSide = true), SetOutline(left.id, View.SIDE, side))
            expect {
                assertEquals(side, it.of(left).outlines[View.SIDE])
                assertEquals(other, it.of(right).outlines[View.SIDE])
            }
            run(SetOutline(right.id, View.SIDE, null, thisSide = true))
            expect { assertEquals(side, it.of(right).outlines[View.SIDE]) }
            undo()
            expect { assertEquals(other, it.of(right).outlines[View.SIDE]) }
        }
    }

    @Test
    fun anUnpairedPartHasOnePlaceForAValue() {
        val tail = PartInstance(PartId("tail"), TemplateRef(TemplateId("tail"), 1))
        val start = AddPart(tail).apply(empty).document
        scenario(start) {
            run(SetParameter(tail.id, "size", 1.5f, thisSide = true))
            expect { assertEquals(1.5f, it.of(tail).params["size"]) }
            undo()
            expect(start)
        }
    }

    @Test
    fun placingAPairCreatesItsDataAndRemovingRestoresItExactly() {
        scenario(empty) {
            run(AddParts(listOf(left, right)))
            expect(placed)
            undo()
            expect(empty)
            redo()
            run(SetOutline(left.id, View.SIDE, side), SetParameter(right.id, "size", 2f, thisSide = true))
            val drawn = document
            run(RemoveParts(listOf(right.id, left.id)))
            expect {
                assertEquals(emptyMap(), it.character.pairs)
                assertEquals(emptyList(), it.character.parts)
            }
            undo()
            expect(drawn)
            redo()
            expect { assertEquals(emptyMap(), it.character.pairs) }
        }
    }

    @Test
    fun aPairsDataStaysUntilItsLastSideGoes() {
        val drawn = SetOutline(left.id, View.SIDE, side).apply(placed).document
        scenario(drawn) {
            run(RemovePart(right.id))
            expect { assertEquals(side, it.of(left).outlines[View.SIDE]) }
            run(RemovePart(left.id))
            expect { assertEquals(emptyMap(), it.character.pairs) }
            undo()
            expect { assertEquals(side, it.of(left).outlines[View.SIDE]) }
            undo()
            expect(drawn)
        }
    }

    @Test
    fun breakingAPairCopiesWhatEachSideShowsIntoItAndClearsThePair() {
        scenario(placed) {
            run(
                SetOutline(left.id, View.SIDE, side),
                SetParameter(left.id, "size", 1.2f),
                SetOutline(right.id, View.TOP, top, thisSide = true),
            )
            val paired = document
            run(BreakPair(pair))
            expect {
                assertEquals(paired.of(left), it.of(left))
                assertEquals(paired.of(right), it.of(right))
                assertEquals(emptyMap(), it.character.pairs)
                for (part in it.character.parts) {
                    assertNull(part.pair)
                    assertEquals(PartData(), part.overrides)
                }
                assertEquals(listOf(Side.LEFT, Side.RIGHT), it.character.parts.map { part -> part.side })
            }
            val broken = document
            run(SetOutline(left.id, View.SIDE, other))
            expect {
                assertEquals(other, it.of(left).outlines[View.SIDE])
                assertEquals(side, it.of(right).outlines[View.SIDE])
            }
            undo()
            expect(broken)
            undo()
            expect(paired)
            redo()
            expect(broken)
        }
    }

    @Test
    fun aMissingPairIsRefused() {
        assertFailsWith<IllegalArgumentException> { BreakPair(PairId("none")).apply(placed) }
    }

    @Test
    fun partsAndPairDataMustAgree() {
        val character = placed.character
        assertFailsWith<IllegalArgumentException> { character.copy(pairs = emptyMap()) }
        assertFailsWith<IllegalArgumentException> { character.copy(parts = emptyList()) }
    }

    @Test
    fun theNewCommandsSerialize() {
        val commands = listOf<DocumentCommand>(
            AddParts(listOf(left, right), listOf(0, 1), mapOf(pair to PartData(params = mapOf("size" to 1f)))),
            AddPart(left, 0, PartData(outlines = mapOf(View.SIDE to side))),
            SetOutline(left.id, View.SIDE, side, thisSide = true),
            SetParameter(left.id, "size", 1f, thisSide = true),
            BreakPair(pair),
            RestorePair(pair, PartData(outlines = mapOf(View.SIDE to side)), mapOf(left.id to PartData(), right.id to PartData(params = mapOf("size" to 2f)))),
        )
        for (command in commands) {
            val text = Json.encodeToString(DocumentCommand.serializer(), command)
            assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
        }
    }

    @Test
    fun aDocumentRoundTripsWithItsPairData() {
        val document = listOf<DocumentCommand>(
            SetOutline(left.id, View.SIDE, side),
            SetParameter(right.id, "size", 2f, thisSide = true),
        ).fold(placed) { d, c -> c.apply(d).document }
        val text = Json.encodeToString(SquashDocument.serializer(), document)
        val read = Json.decodeFromString(SquashDocument.serializer(), text)
        assertEquals(document, read)
        assertEquals(side, read.of(right).outlines[View.SIDE])
        assertEquals(2f, read.of(right).params["size"])
        assertNull(read.of(left).params["size"])
    }

    @Test
    fun aDocumentAlwaysWritesItsFormatAndAnyOtherFormatIsRefused() {
        val text = Json.encodeToString(SquashDocument.serializer(), empty)
        assertEquals(true, "\"format\":$DOCUMENT_FORMAT" in text)
        val newer = text.replace("\"format\":$DOCUMENT_FORMAT", "\"format\":${DOCUMENT_FORMAT + 1}")
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString(SquashDocument.serializer(), newer) }
        val unnumbered = text.replace(",\"format\":$DOCUMENT_FORMAT", "")
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString(SquashDocument.serializer(), unnumbered) }
    }
}
