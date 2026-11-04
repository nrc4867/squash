package dev.chieppa.squash.document

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Reads saves as an older app wrote them. Each format step has one fixture here, written by the app of that format. */
class DocumentFormatTest {
    private val formatOne = File("src/desktopTest/fixtures/format-1.json").readText()
    private val formatTwo = File("src/desktopTest/fixtures/format-2.json").readText()
    private val formatThree = File("src/desktopTest/fixtures/format-3.json").readText()
    private val formatFour = File("src/desktopTest/fixtures/format-4.json").readText()
    private val formatFive = File("src/desktopTest/fixtures/format-5.json").readText()
    private val combs = Grain(listOf(GrainComb(u = 0.25f, v = 0.5f, du = 0f, dv = 1f)))

    @Test
    fun aFormatOneSaveReads() {
        val document = readDocument(formatOne)
        assertEquals(DOCUMENT_FORMAT, document.format)
        val character = document.character
        assertEquals(listOf("torso-1", "leg-l", "leg-r"), character.parts.map { it.id.value })
        assertEquals(Palette(), character.palette)
        assertEquals(emptyList(), character.expressions)
        assertEquals(setOf(View.SIDE), character.resolved(PartId("torso-1"))?.outlines?.keys)
        assertEquals(1.2f, character.resolved(PartId("leg-l"))?.params?.get("size"))
        assertEquals(1.5f, character.resolved(PartId("leg-r"))?.params?.get("size"))
    }

    @Test
    fun aPartsGrainMovesToItsSurfaceWork() {
        val character = readDocument(formatOne).character
        assertEquals(combs, character.resolved(PartId("torso-1"))?.surface?.grain, "an unpaired part holds it as its own")
        assertEquals(combs, character.resolved(PartId("leg-l"))?.surface?.grain, "a paired part holds it as its side's override")
        assertEquals(null, character.resolved(PartId("leg-r"))?.surface?.grain, "a part with no combs has none")
    }

    @Test
    fun aFormatTwoSaveReadsWithEveryStrokeHard() {
        val document = readDocument(formatTwo)
        assertEquals(DOCUMENT_FORMAT, document.format)
        val strokes = listOf("torso-1", "leg-l", "leg-r").associateWith { part ->
            checkNotNull(document.character.resolved(PartId(part))).surface.paint.strokes
        }
        assertEquals(listOf("belly"), strokes.getValue("torso-1").map { it.id.value }, "an unpaired part's own")
        assertEquals(listOf("sock"), strokes.getValue("leg-l").map { it.id.value }, "what a pair shares")
        assertEquals(setOf("sock", "knee"), strokes.getValue("leg-r").map { it.id.value }.toSet(), "a side's override")
        assertEquals(0.08f, strokes.getValue("torso-1").single().radius)
        assertTrue(strokes.values.flatten().all { it.softness == 0f })
    }

    @Test
    fun aFormatFourSaveReadsWithNoLineWeightsAndTheDefaultRules() {
        val document = readDocument(formatFour)
        assertEquals(DOCUMENT_FORMAT, document.format)
        val character = document.character
        assertEquals(LineRules(), character.lineRules)
        assertEquals(CharacterShading(ShadingMode.TWO_TONE, farSideDarker = false), character.shading)
        assertEquals(0.4f, character.resolved(PartId("torso-1"))?.surface?.paint?.strokes?.single()?.softness)
        assertTrue(character.parts.all { character.resolved(it).surface.lineWeights.isEmpty() })
        assertEquals(document, readDocument(Json.encodeToString(SquashDocument.serializer(), document)))
    }

    @Test
    fun aFormatThreeSaveReadsFlatWithNoSlotShadows() {
        val document = readDocument(formatThree)
        assertEquals(DOCUMENT_FORMAT, document.format)
        assertEquals(CharacterShading(), document.character.shading)
        assertTrue(document.character.palette.slots.all { it.shadow == null })
    }

    @Test
    fun theShadingChoiceAndASlotsShadowSurviveASave() {
        val cream = SlotName("cream")
        val tone = SlotColor(0.85f, 0.7f, 0.5f)
        val commands = listOf(
            SetPaletteSlot(cream, SlotColor(0.99f, 0.94f, 0.82f)),
            SetSlotShadow(cream, tone),
            SetCharacterShading(CharacterShading(ShadingMode.TWO_TONE, farSideDarker = false)),
        )
        val shaded = commands.fold(readDocument(formatThree)) { document, command -> command.apply(document).document }
        assertEquals(shaded, readDocument(Json.encodeToString(SquashDocument.serializer(), shaded)))
    }

    @Test
    fun aSoftStrokeSurvivesASaveAndAHardOneWritesNoSoftness() {
        val saved = readDocument(formatTwo)
        val torso = PartId("torso-1")
        val hard = checkNotNull(saved.character.resolved(torso)).surface.paint.strokes.single()
        assertTrue("softness" !in Json.encodeToString(SquashDocument.serializer(), saved))
        val soft = SetBrushStroke(torso, hard.id, hard.copy(softness = 0.4f)).apply(saved).document
        val text = Json.encodeToString(SquashDocument.serializer(), soft)
        assertEquals(soft, readDocument(text))
        assertEquals(0.4f, checkNotNull(readDocument(text).character.resolved(torso)).surface.paint.strokes.single().softness)
    }

    @Test
    fun aFormatFiveFurMarkBecomesARootOfItsKind() {
        val document = readDocument(formatFive)
        assertEquals(DOCUMENT_FORMAT, document.format)
        val marks = checkNotNull(document.character.resolved(PartId("torso-1"))).surface.marks.associateBy { it.id.value }
        val fur = marks.getValue("fur-0-0")
        assertEquals(MarkKind.FUR, fur.kind)
        assertEquals(listOf(0.7f), fur.points.map { it.u }, "only its root stays, since the grain sets the rest")
        val line = marks.getValue("brow")
        assertEquals(MarkKind.LINE, line.kind)
        assertEquals(2, line.points.size)
    }

    @Test
    fun aLineWritesNoKindAndAFurStrokeRoundTrips() {
        val saved = readDocument(formatFive)
        val text = Json.encodeToString(SquashDocument.serializer(), saved)
        assertEquals(1, Regex("\"kind\":\"fur\"").findAll(text).count())
        assertTrue("\"line\"" !in text)
        assertEquals(saved, readDocument(text))
    }

    @Test
    fun aDirectDecodeOfAnOlderFormatIsStillRefused() {
        assertFailsWith<IllegalArgumentException> {
            kotlinx.serialization.json.Json.decodeFromString(SquashDocument.serializer(), formatOne)
        }
    }

    @Test
    fun aNewerFormatIsRefusedAndSaysSo() {
        val newer = formatOne.replace("\"format\": 1", "\"format\": ${DOCUMENT_FORMAT + 1}")
        val failure = assertFailsWith<IllegalArgumentException> { readDocument(newer) }
        assertTrue("newer" in failure.message.orEmpty(), "${failure.message}")
    }

    @Test
    fun aFormatWithNoStepForwardIsRefused() {
        val ancient = formatOne.replace("\"format\": 1", "\"format\": 0")
        assertFailsWith<IllegalArgumentException> { readDocument(ancient) }
    }

    @Test
    fun aSaveThatDoesNotSayItsFormatIsRefused() {
        val unnumbered = formatOne.replace(Regex(",\\s*\"format\": 1"), "")
        assertFailsWith<IllegalArgumentException> { readDocument(unnumbered) }
    }
}
