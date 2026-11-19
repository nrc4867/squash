package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A socket's spread and path, a part's shades, and a row's sizes, built into parameters and exported back. */
class RecipeCopiesTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(sockets: String, parts: String = "") = """
        {"format": 1, "id": "toy", "name": "Toy",
         "palette": [{"slot": "wool", "color": "#f0ece0"}, {"slot": "wool-2", "color": "#e0d8c8"}],
         "parts": [{"id": "body", "template": "tube", "rest": "up", "sockets": [$sockets]}$parts]}
    """.trimIndent()

    private val spread = """{"n": 1, "u": 0.1, "to": 0.9, "v": 0, "toV": 1, "size": 0.8, "falloff": 0.5, "spread": {"count": 50, "seed": 4, "spacing": 0.02, "sizeJitter": 0.2, "leanJitter": 10, "turnJitter": 90}}"""
    private val path = """{"n": 2, "u": 0.3, "v": 0.2, "to": 0.7, "toV": 0.2, "path": {"via": [[0.5, 0.3]], "roll": 90, "sag": 0.04}}"""
    private val fleece = """, {"id": "fleece", "template": "blob", "on": "body", "socket": "socket.1", "color": "wool", "shades": ["wool-2"]}"""

    @Test
    fun aSpreadAndAPathBuildTheirSocketParameters() {
        val character = builder.build(RecipeJson.read(recipe("$spread, $path", fleece))).document.character
        val body = assertNotNull(character.resolved(PartId("body"))).params
        assertEquals(50f, body["socket.1.spread"])
        assertEquals(4f, body["socket.1.seed"])
        assertEquals(0.9f, body["socket.1.to"])
        assertEquals(90f, body["socket.1.turnJitter"])
        assertEquals(0f, body["socket.2.repeat"])
        assertEquals(0.5f, body["socket.2.via.0.u"])
        assertEquals(90f, body["socket.2.roll"])
        assertEquals(listOf(SlotName("wool-2")), assertNotNull(character.resolved(PartId("fleece"))).surface.shades)
    }

    @Test
    fun exportGivesThemBackAndRebuildsTheSameCharacter() {
        val built = builder.build(RecipeJson.read(recipe("$spread, $path", fleece))).document
        val exported = exportRecipe(built).recipe
        val body = exported.parts.first { it.id == "body" }
        assertEquals(50, body.sockets.first { it.n == 1 }.spread?.count)
        assertEquals(listOf(listOf(0.5f, 0.3f)), body.sockets.first { it.n == 2 }.path?.via)
        assertEquals(emptyMap(), body.params)
        assertEquals(listOf("wool-2"), exported.parts.first { it.id == "fleece" }.shades)
        assertEquals(built, builder.build(RecipeJson.read(RecipeJson.write(exported))).document)
    }

    @Test
    fun aPathBetweenTwoSocketsTakesTheirPlaces() {
        val between = """{"n": 3, "u": 0.2, "v": 0.1}, {"n": 4, "u": 0.6, "v": 0.4}, {"n": 5, "u": 0, "v": 0, "path": {"between": ["socket.3", "socket.4"]}}"""
        val body = assertNotNull(builder.build(RecipeJson.read(recipe(between))).document.character.resolved(PartId("body"))).params
        assertEquals(listOf(0.2f, 0.1f, 0.6f, 0.4f), listOf(body["socket.5.u"], body["socket.5.v"], body["socket.5.to"], body["socket.5.toV"]))
    }

    @Test
    fun tooManyCopiesFailAtTheirCount() {
        val e = assertFailsWith<RecipeException> {
            builder.build(RecipeJson.read(recipe("""{"n": 1, "u": 0, "v": 0, "spread": {"count": 5000}}""")))
        }
        assertEquals("$.parts[0].sockets[0].spread.count", e.path)
        assertTrue("1000" in e.message!!, e.message)
    }

    @Test
    fun anUnknownBetweenSocketIsNamed() {
        val e = assertFailsWith<RecipeException> {
            builder.build(RecipeJson.read(recipe("""{"n": 1, "u": 0, "v": 0, "path": {"between": ["socket.8", "socket.9"]}}""")))
        }
        assertEquals("$.parts[0].sockets[0].path.between[0]", e.path)
    }

    @Test
    fun aPartOnARowStartsAtItsSocketsSizeUnlessItGivesItsOwn() {
        val row = """{"n": 1, "u": 0.2, "to": 0.8, "v": 0, "count": 3, "size": 1.2, "falloff": 0.5}"""
        val parts = """, {"id": "a", "template": "spike", "on": "body", "socket": "socket.1.0"}, {"id": "b", "template": "spike", "on": "body", "socket": "socket.1.2"}, {"id": "c", "template": "spike", "on": "body", "socket": "socket.1.1", "size": 0.7}"""
        val built = builder.build(RecipeJson.read(recipe(row, parts))).document
        val character = built.character
        assertEquals(1.2f, assertNotNull(character.resolved(PartId("a"))).params[SIZE_PARAM])
        assertEquals(0.6f, assertNotNull(character.resolved(PartId("b"))).params[SIZE_PARAM]!!, 1e-5f)
        assertEquals(0.7f, assertNotNull(character.resolved(PartId("c"))).params[SIZE_PARAM])
        val exported = exportRecipe(built).recipe
        assertNull(exported.parts.first { it.id == "a" }.size)
        assertEquals(built, builder.build(RecipeJson.read(RecipeJson.write(exported))).document)
    }
}
