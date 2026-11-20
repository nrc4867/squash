package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CharacterCreases
import dev.chieppa.squash.document.DEFAULT_CREASE_STRENGTH
import dev.chieppa.squash.document.REST_CREASE
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** A recipe's creases and its garments' crease strengths: on at their defaults when left out or given empty, set, checked and exported. */
class RecipeCreasesTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(creases: String = "", garment: String = "") =
        """{"format": 1, "id": "pup", "name": "Pup", "plan": "canine-quadruped", "palette": [{"slot": "fur", "color": "#e6b36e"}, {"slot": "knit", "color": "#4a7fc1"}],
           "parts": [{"id": "torso", "template": "torso", "role": "body", "color": "fur"}],
           "wardrobe": {"slots": [{"id": "body", "name": "Body"}], "garments": [{"id": "sweater", "name": "Sweater", "slot": "body", "color": "knit", $garment
             "areas": [{"id": "body", "paint": [{"on": "torso", "shape": "band", "u": [0.3, 0.9]}]}]}]}
           $creases}"""

    private fun error(text: String): RecipeException = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(text)) }

    @Test
    fun creasesAreOnAtTheirDefaultsWhenLeftOutOrGivenEmpty() {
        assertEquals(CharacterCreases(DEFAULT_CREASE_STRENGTH, DEFAULT_CREASE_STRENGTH, REST_CREASE), builder.build(RecipeJson.read(recipe())).document.character.creases)
        val on = builder.build(RecipeJson.read(recipe(""", "creases": {}"""))).document.character.creases
        assertEquals(CharacterCreases(DEFAULT_CREASE_STRENGTH, DEFAULT_CREASE_STRENGTH, REST_CREASE), on)
        val set = builder.build(RecipeJson.read(recipe(""", "creases": {"skin": 1.5, "cloth": 0, "rest": 0.1}"""))).document.character.creases
        assertEquals(CharacterCreases(1.5f, 0f, 0.1f), set)
    }

    @Test
    fun aGarmentsStrengthsAreWrittenThroughAndAWrongOneStopsAtItsPath() {
        val garment = builder.build(RecipeJson.read(recipe(garment = """"creases": 0.5, "wrinkles": 2, "drape": 0,"""))).document.character.wardrobe.garments.single()
        assertEquals(0.5f, garment.creases)
        assertEquals(2f, garment.wrinkles)
        assertEquals(0f, garment.drape)
        assertEquals("$.wardrobe.garments[0].wrinkles", error(recipe(garment = """"wrinkles": 3,""")).path)
        assertEquals("$.creases.skin", error(recipe(""", "creases": {"skin": -1}""")).path)
        assertEquals("$.creases.rest", error(recipe(""", "creases": {"rest": 2}""")).path)
    }

    @Test
    fun creasesExportAndAreLeftOutAtTheirDefaults() {
        val built = builder.build(RecipeJson.read(recipe(""", "creases": {"skin": 0.8}""", """"drape": 1.5,""")))
        val exported = exportRecipe(built.document).recipe
        assertEquals(0.8f, exported.creases?.skin)
        assertEquals(1.5f, exported.wardrobe?.garments?.single()?.drape)
        assertNull(exportRecipe(builder.build(RecipeJson.read(recipe())).document).recipe.creases)
    }
}
