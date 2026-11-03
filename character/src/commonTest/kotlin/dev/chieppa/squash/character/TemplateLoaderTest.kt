package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Tube
import dev.chieppa.squash.character.load.TemplateLoadException
import dev.chieppa.squash.character.load.TemplateLoader
import dev.chieppa.squash.character.model.Param
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TemplateLoaderTest {
    private fun template(blocks: String, extra: String = "") = """
        {
          "format": 1,
          "id": "leg",
          "version": 1,
          "name": "Leg",
          "axis": { "rest": [0, -1, 0] },
          "openEnd": "start",
          "topology": { "rings": 8, "segments": 8 },
          "views": { "side": "height" },
          $extra
          "params": { "thickness": { "default": 0.2, "min": 0.1, "max": 0.5 }, "pads": { "default": true } },
          "blocks": [
            $blocks
          ]
        }
    """.trimIndent()

    private val tube = """{ "kind": "tube", "u": [0, 1], "radius": "${'$'}thickness" }"""

    private fun failure(text: String) = assertFailsWith<TemplateLoadException> { TemplateLoader.loadPart("leg.part.json", text) }

    @Test
    fun aTemplateLoadsWithCommentsAndTrailingCommas() {
        val loaded = TemplateLoader.loadPart("leg.part.json", template("$tube, // the body\n"))
        assertEquals(Param("\$thickness"), (loaded.blocks.single() as Tube).radius)
    }

    @Test
    fun experimentKeysAreIgnoredAtAnyDepth() {
        val blocks = """{ "kind": "tube", "u": [0, 1], "radius": 0.2, "x-note": { "anything": 1 } }"""
        val loaded = TemplateLoader.loadPart("leg.part.json", template(blocks, """ "x-draft": true, """))
        assertEquals(1, loaded.blocks.size)
    }

    @Test
    fun anUnknownKeyIsAnErrorNamingFilePathAndLine() {
        val e = failure(template("""{ "kind": "tube", "u": [0, 1], "radius": 0.2, "colour": 1 }"""))
        assertEquals("leg.part.json", e.source)
        assertEquals("$.blocks[0]", e.path)
        assertEquals(13, e.line)
        assertTrue(e.message!!.contains("colour"), e.message)
    }

    @Test
    fun anUnknownKindIsAnError() {
        val e = failure(template("""{ "kind": "wing", "u": [0, 1] }"""))
        assertEquals(13, e.line)
        assertTrue(e.message!!.contains("wing"), e.message)
    }

    @Test
    fun aSyntaxErrorNamesItsLine() {
        val e = failure("{\n  \"format\": 1,\n  oops\n}")
        assertEquals(3, e.line)
    }

    @Test
    fun aMalformedParamIsAnError() {
        val e = failure(template("""{ "kind": "tube", "u": [0, 1], "radius": "thickness" }"""))
        assertTrue(e.message!!.contains("thickness"), e.message)
    }

    @Test
    fun aMistypedSectionOrTupleIsAnError() {
        failure(template(tube, """ "section": "square", """))
        failure(template("""{ "kind": "tube", "u": [0, 1, 2], "radius": 0.2 }"""))
    }
}
