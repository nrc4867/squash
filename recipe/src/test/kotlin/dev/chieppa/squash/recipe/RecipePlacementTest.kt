package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A part's roll, Top midline, socket outset, rows round a part and a see-through slot, built and exported back. */
class RecipePlacementTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private val body = """
        {"id": "body", "template": "tube", "rest": "up",
         "sockets": [{"n": 1, "u": 0.5, "v": 0, "count": 8, "toV": 1, "outset": 0.02, "sink": 0}, {"n": 2, "u": 0.8, "v": 0.1, "outset": 0.01}]}
    """.trimIndent()

    private val petal = """{"id": "petal", "template": "sheet", "on": "body", "socket": "socket.2", "rest": "out", "outward": 75, "roll": 30, "topMiddle": true, "color": "lens"}"""

    private fun recipe(parts: String) = """{"format": 1, "id": "toy", "name": "Toy", "palette": [{"slot": "lens", "color": "#203040", "material": {"preset": "glass", "seeThrough": 0.35}}], "parts": [$parts]}"""

    @Test
    fun theFieldsBuildTheirParameters() {
        val character = builder.build(RecipeJson.read(recipe("$body, $petal"))).document.character
        val body = character.resolved(PartId("body"))!!.params
        assertEquals(1f, body["socket.1.toV"])
        assertEquals(0.02f, body["socket.1.outset"])
        assertEquals(0.01f, body["socket.2.outset"])
        val petal = character.resolved(PartId("petal"))!!.params
        val expected = mapOf("move.outward" to 75f, "move.roll" to 30f, "top.middle" to 1f)
        expected.forEach { (key, value) -> assertEquals(value, petal[key], key) }
        assertEquals(0.35f, character.palette.opacity(SlotName("lens")))
    }

    @Test
    fun exportLiftsThemBack() {
        val built = builder.build(RecipeJson.read(recipe("$body, $petal"))).document
        val exported = exportRecipe(built).recipe
        val petal = exported.parts.first { it.id == "petal" }
        assertEquals(emptyMap(), petal.params)
        assertEquals(30f, petal.roll)
        assertEquals(true, petal.topMiddle)
        val row = exported.parts.first { it.id == "body" }.sockets.first { it.n == 1 }
        assertEquals(1f, row.toV)
        assertEquals(0.02f, row.outset)
        assertEquals(0.35f, exported.palette.single().material?.seeThrough)
        assertEquals(MaterialPreset.GLASS, exported.palette.single().material?.preset)
        assertEquals(built, builder.build(RecipeJson.read(RecipeJson.write(exported))).document)
    }

    @Test
    fun aNegativeSinkWarnsToUseOutset() {
        val build = builder.build(RecipeJson.read(recipe("""{"id": "body", "template": "tube", "sockets": [{"n": 1, "u": 0.5, "v": 0, "sink": -2}]}""")))
        assertTrue(build.warnings.any { it.startsWith("$.parts[0].sockets[0].sink:") }, build.warnings.toString())
    }
}
