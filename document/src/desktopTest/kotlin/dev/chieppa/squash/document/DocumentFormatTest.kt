package dev.chieppa.squash.document

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val formatFourteen = File("src/desktopTest/fixtures/format-14.json").readText()
    private val formatSixteenDrawnAndPainted = File("src/desktopTest/fixtures/format-16-drawn-and-painted.json").readText()
    private val formatSixteenDrawnOnly = File("src/desktopTest/fixtures/format-16-drawn-only.json").readText()
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
    fun aSaveFromBeforeRolesReadsWithNone() {
        val parts = readDocument(formatFive).character.parts
        assertTrue(parts.isNotEmpty())
        assertTrue(parts.all { it.role == null && !it.roleGuessed }, "a part with no role reads its template's")
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
    fun aSaveFromBeforeWardrobesWearsNothing() {
        val character = readDocument(formatFive).character
        assertEquals(Wardrobe(), character.wardrobe)
        assertEquals(emptyList(), character.worn)
    }

    @Test
    fun aWardrobeSurvivesASave() {
        val dressed = WinterFennec.dressed()
        assertEquals(dressed, readDocument(Json.encodeToString(SquashDocument.serializer(), dressed)))
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
    fun aFormatFourteenSaveReadsWithEveryPartAndPinAsSaved() {
        val saved = Json.parseToJsonElement(formatFourteen).jsonObject.getValue("character").jsonObject
        val character = readDocument(formatFourteen).character
        assertEquals(listOf("torso", "head", "leg-l", "leg-r"), character.parts.map { it.id.value })
        val pins = saved.getValue("parts").jsonArray.map { it.jsonObject.getValue("template").jsonObject.let { pin -> pin.getValue("id").jsonPrimitive.content to pin.getValue("version").jsonPrimitive.int } }
        assertEquals(pins, character.parts.map { it.template.id.value to it.template.version })
        assertEquals(0.12f, character.part(PartId("leg-l"))?.blend, "a join's blend is kept")
        assertEquals(listOf("vest"), character.wardrobe.garments.map { it.id.value })
        assertEquals(null, character.wardrobe.garments.single().fit, "a garment saved before fits stands off by its branches' eases")
    }

    @Test
    fun aFormatFifteenSaveDropsItsGarmentsCuffInk() {
        val fifteen = """{"format": 15, "character": {"id": "vest", "name": "Vest", "bodyPlan": {"id": "canine-quadruped", "version": 1}, "wardrobe": {"garments": [{"id": "vest", "name": "Vest", "inkHem": false, "inkCuffs": false}]}}}"""
        val garment = readDocument(fifteen).character.wardrobe.garments.single()
        assertEquals(false, garment.inkHem)
        assertEquals(emptyList(), garment.areas, "a garment covering nothing paints nothing")
    }

    @Test
    fun aFormatEighteenGarmentsBoxesArePaintedAsCells() {
        val box = """{"part": "torso", "from": 0.4, "to": 0.97, "fromV": -0.22, "toV": 0.22}"""
        val skirt = """{"part": "leg-l", "from": -0.3, "to": 0.5}"""
        val edge = """{"curve": {"points": [{"part": "torso", "patch": "body", "u": 0.5, "v": 0}], "closed": false}}"""
        val eighteen = """{"format": 18, "character": {"id": "vest", "name": "Vest", "bodyPlan": {"id": "canine-quadruped", "version": 1}, "wardrobe": {"garments": [""" +
            """{"id": "vest", "name": "Vest", "branches": [$box, $skirt], "edges": [$edge], "fill": []}]}}}"""
        val garment = readDocument(eighteen).character.wardrobe.garments.single()
        val area = garment.areas.single()
        assertEquals(FIRST_AREA, area.id)
        val torso = cellsOfRuns(area.cells.getValue(PartId("torso")))
        val rows = (0 until CHART_ROWS).filter { row -> (0 until CHART_COLUMNS).any { torso[row * CHART_COLUMNS + it] } }
        val columns = (0 until CHART_COLUMNS).filter { column -> (0 until CHART_ROWS).any { torso[it * CHART_COLUMNS + column] } }
        assertEquals((26..61).toList(), rows, "the rows whose middles lie in 0.4 to 0.97")
        assertEquals((0..6).toList() + (25..31).toList(), columns, "the columns whose middles lie within 0.22 of v 0")
        assertEquals(0.4f, garment.branch(PartId("torso"))?.from, "the branch stays as saved")
        val leg = checkNotNull(garment.branch(PartId("leg-l")))
        assertEquals(-0.3f, leg.from, "a range past the part's root is kept")
        assertEquals(true, cellsOfRuns(area.cells.getValue(PartId("leg-l")))[0], "and its cells reach the root")
        assertEquals(readDocument(Json.encodeToString(SquashDocument.serializer(), readDocument(eighteen))), readDocument(eighteen))
    }

    @Test
    fun aFormatFourteenSaveWithTheSwitchOnReadsTheSame() {
        val switched = formatFourteen.replace("\"name\": \"Fennec\",", "\"name\": \"Fennec\", \"oneSurface\": true,")
        assertTrue("oneSurface" in switched)
        assertEquals(readDocument(formatFourteen), readDocument(switched))
    }

    @Test
    fun aSaveWritesNeitherTheSwitchNorAFillet() {
        val document = readDocument(formatFourteen)
        val text = Json.encodeToString(SquashDocument.serializer(), document)
        assertTrue("oneSurface" !in text && "fillet" !in text, text)
        assertEquals(document, readDocument(text))
    }

    @Test
    fun aHeadsDrawnEyesBecomeAPaintedPairAndItsMouthStays() {
        val character = readDocument(formatFourteen).character
        val head = checkNotNull(character.resolved(PartId("head"))).surface
        assertEquals(listOf("mouth"), head.groups.map { it.id.value })
        assertTrue(head.marks.none { it.id.value.startsWith("eye-") }, "the eyes' marks go with them")
        val eye = character.paintedEyes.single()
        assertEquals("eyes", eye.id)
        assertTrue(eye.mirrored)
        assertEquals(PartId("head"), eye.anchor.part)
        val lid = Json.parseToJsonElement(formatFourteen).jsonObject.getValue("character").jsonObject.getValue("parts").jsonArray
            .first { it.jsonObject.getValue("id").jsonPrimitive.content == "head" }.jsonObject.getValue("own").jsonObject.getValue("surface").jsonObject
            .getValue("marks").jsonArray.map { it.jsonObject }.first { it.getValue("id").jsonPrimitive.content == "eye-l-lid" }
            .getValue("points").jsonArray.map { it.jsonObject.getValue("u").jsonPrimitive.float to it.jsonObject.getValue("v").jsonPrimitive.float }
        assertEquals((lid.minOf { it.first } + lid.maxOf { it.first }) / 2f, eye.anchor.u, 1e-5f)
        assertEquals((lid.minOf { it.second } + lid.maxOf { it.second }) / 2f, eye.anchor.v, 1e-5f, "the left eye's middle")
        assertEquals((lid.maxOf { it.first } - lid.minOf { it.first }) / 2f, eye.look.width, 1e-5f)
        assertEquals(SlotName("cream"), eye.look.white)
        assertEquals(SlotName("ink"), eye.look.pupil)
        assertEquals(null, eye.look.iris)
        assertTrue(eye.look.lid && !eye.look.lashes && !eye.look.brow, "it draws what the drawn eye drew")
        assertEquals(emptyMap(), character.expressions.single().marks, "an expression drops the eyes' marks")
    }

    @Test
    fun aHeadWithPaintedEyesKeepsThemAndLosesItsDrawnOnes() {
        val painted = PaintedEye("mine", Anchor(PartId("head"), u = 0.5f, v = 0.2f))
        val withPainted = Json.parseToJsonElement(formatFourteen).jsonObject.let { saved ->
            val character = saved.getValue("character").jsonObject
            val eyes = Json.encodeToJsonElement(ListSerializer(PaintedEye.serializer()), listOf(painted))
            JsonObject(saved + ("character" to JsonObject(character + ("paintedEyes" to eyes))))
        }
        val character = readDocument(withPainted.toString()).character
        assertEquals(listOf(painted), character.paintedEyes)
        assertEquals(listOf("mouth"), checkNotNull(character.resolved(PartId("head"))).surface.groups.map { it.id.value })
    }

    @Test
    fun aDrawnEyeLeftOnAPairAndTheGazeCheatsAreDroppedWithTheRest() {
        val eye = "{\"id\": \"eye\", \"name\": \"Eye\", \"roles\": [{\"role\": \"white\"}, {\"role\": \"lid\", \"marks\": [\"eye-lid\"]}], \"clips\": [{\"by\": \"lid\", \"clipped\": \"white\"}]}"
        val mark = "{\"id\": \"eye-lid\", \"points\": [{\"part\": \"leg-l\", \"u\": 0.1, \"v\": 0.2}, {\"part\": \"leg-l\", \"u\": 0.2, \"v\": 0.2}]}"
        val saved = formatFourteen
            .replace("\"front-legs\": {}", "\"front-legs\": {\"surface\": {\"groups\": [$eye], \"marks\": [$mark]}}")
            .replace("\"palette\":", "\"gazeCheats\": {\"cameraBias\": 0.5}, \"palette\":")
        assertTrue("\"lid\"" in saved && "gazeCheats" in saved)
        val character = readDocument(saved).character
        assertEquals(emptyList(), character.parts.flatMap { checkNotNull(character.resolved(it)).surface.groups.filter { group -> group.id.value == "eye" } })
        assertTrue(character.parts.all { part -> checkNotNull(character.resolved(part)).surface.marks.none { it.id.value == "eye-lid" } }, "the eye's marks go with it")
    }

    @Test
    fun aFormatSixteenSaveWithDrawnAndPaintedEyesDropsTheDrawnOnes() {
        val character = readDocument(formatSixteenDrawnAndPainted).character
        assertEquals(listOf("mine"), character.paintedEyes.map { it.id })
        val head = checkNotNull(character.resolved(PartId("head"))).surface
        assertEquals(listOf("smile"), head.groups.map { it.id.value })
        assertEquals(listOf("smile-line"), head.marks.map { it.id.value })
        assertEquals(setOf("smile"), character.expressions.single().marks.keys.map { it.value }.toSet())
    }

    @Test
    fun aFormatSixteenSaveWithOnlyDrawnEyesGetsAPaintedPair() {
        val character = readDocument(formatSixteenDrawnOnly).character
        val eye = character.paintedEyes.single()
        assertEquals("eyes", eye.id)
        assertTrue(eye.mirrored)
        assertEquals(PartId("head"), eye.anchor.part)
        assertEquals(0.3f, eye.anchor.u, 1e-5f)
        assertEquals(0.2f, eye.anchor.v, 1e-5f)
        assertEquals(SlotName("cream"), eye.look.white)
        assertEquals(SlotName("ink"), eye.look.pupil)
        assertEquals(listOf("smile"), checkNotNull(character.resolved(PartId("head"))).surface.groups.map { it.id.value })
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
