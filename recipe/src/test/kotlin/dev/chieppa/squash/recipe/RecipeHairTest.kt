package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** A part's preset and a spread's droop, built into parameters and exported back. */
class RecipeHairTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(hair: String, sockets: String = "") = """
        {"format": 1, "id": "toy", "name": "Toy",
         "parts": [{"id": "head", "template": "blob", "sockets": [{"n": 1, "u": 0.5, "v": 0.5}$sockets]},
                   {"id": "hair", "template": "hair-volume", "on": "head", "socket": "socket.1", $hair}]}
    """.trimIndent()

    @Test
    fun aPresetSetsItsTemplatesValuesAndParamsOverrideThem() {
        val params = assertNotNull(builder.build(RecipeJson.read(recipe(""""preset": "bob", "params": {"sides": 0.3}"""))).document.character.resolved(PartId("hair"))).params
        assertEquals(0.6f, params["back"])
        assertEquals(0.3f, params["sides"])
    }

    @Test
    fun anUnknownPresetFailsAtItsPath() {
        val error = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe(""""preset": "mohawk""""))) }
        assertEquals("$.parts[1].preset", error.path)
    }

    @Test
    fun aSpreadsDroopBuildsAndExports() {
        val droop = """, {"n": 2, "u": 0.1, "to": 0.9, "v": 0.2, "toV": 0.8, "spread": {"count": 20, "droop": 70}}"""
        val built = builder.build(RecipeJson.read(recipe(""""preset": "short"""", droop))).document
        assertEquals(70f, assertNotNull(built.character.resolved(PartId("head"))).params["socket.2.droop"])
        assertEquals(70f, exportRecipe(built).recipe.parts.first { it.id == "head" }.sockets.first { it.n == 2 }.spread?.droop)
    }
}
