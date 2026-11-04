package dev.chieppa.squash.document

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Reads saves as an older app wrote them. Each format step has one fixture here, written by the app of that format. */
class DocumentFormatTest {
    private val formatOne = File("src/desktopTest/fixtures/format-1.json").readText()
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
